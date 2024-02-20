package se.havochvatten.symphony.service;

import com.fasterxml.jackson.databind.node.NullNode;
import com.github.miachm.sods.*;
import it.geosolutions.jaiext.stats.HistogramMode;
import it.geosolutions.jaiext.stats.Statistics;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.referencing.operation.transform.AffineTransform2D;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import se.havochvatten.symphony.calculation.ComparisonResult;
import se.havochvatten.symphony.calculation.Operations;
import se.havochvatten.symphony.calculation.SankeyChart;
import se.havochvatten.symphony.dto.*;
import se.havochvatten.symphony.dto.CompoundComparisonExport.ComparisonResultExport;
import se.havochvatten.symphony.entity.*;
import se.havochvatten.symphony.exception.SymphonyStandardAppException;
import se.havochvatten.symphony.util.ODSStyles;
import si.uom.SI;
import tech.units.indriya.quantity.Quantities;

import javax.ejb.EJB;
import javax.ejb.Singleton;
import javax.inject.Inject;
import javax.measure.Quantity;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;
import javax.measure.quantity.Length;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toMap;

@Singleton
public class ReportService {
    private static final Logger logger = Logger.getLogger(ReportService.class.getName());

    // The CSV standard (RFC 4180) actually says to use comma, but tab is MS Excel default. Or use TSV?
    private static final char CSV_FIELD_SEPARATOR = '\t';

    private static final int ODF_TITLE_ROWS = 6;
    private static final int ODF_TITLE_ROWS_TOTAL = 3;
    private static final int ODF_CALC_TOTALS_SECTION = 7;
    private static final double ODF_TITLE_COLWIDTH = 55.0;

    @EJB
    private Operations operations;

    @PersistenceContext(unitName = "symphonyPU")
    public EntityManager em;

    @Inject
    MetaDataService metaDataService;

    @Inject
    SensMatrixService matrixService;

    @Inject
    CalcService calcService;

    @Inject
    PropertiesService props;

    record StatisticsResult(double min, double max, double average, double stddev, double[] histogram, long pixels){}

    public record MultiComparisonAuxiliary(int[] ecosystems, int[] pressures, double[][] result) {}

    private StatisticsResult getStatistics(GridCoverage2D coverage) {
        Statistics[] simpleStats = operations.stats(coverage, new int[]{0}, new Statistics.StatsType[]{
                Statistics.StatsType.EXTREMA,
                Statistics.StatsType.MEAN,
                Statistics.StatsType.DEV_STD })[0];

        double[] extrema = (double[]) simpleStats[0].getResult();
        double max = extrema[1] + (Math.ulp(extrema[1]) * 100);

        HistogramMode histogram =
            operations.histogram(coverage, 0.0, max, 100);

        return new StatisticsResult(extrema[0], extrema[1],
                        (double) simpleStats[1].getResult(),
                        (double) simpleStats[2].getResult(),
                        (double[]) histogram.getResult(),
                        simpleStats[0].getNumSamples());
    }

    private static double getResolutionInMetres(GridCoverage2D coverage) {
        double result;
        GridGeometry2D geometry = coverage.getGridGeometry();
        Double scale = ((AffineTransform2D) geometry.getGridToCRS()).getScaleX();
        Unit<Length> unit = (Unit<Length>) geometry.getCoordinateReferenceSystem2D().getCoordinateSystem().getAxis(0).getUnit();
        Quantity<Length> resolution = Quantities.getQuantity(scale, unit);

        try {
            result = resolution.to(SI.METRE).getValue().doubleValue();
        } catch (UnconvertibleException e) { // Coordinate system isn't projected, unit (rad/deg) cannot be converted.
                                             // We could check the CoordinateSystem or specific types of Unit but
                                             // let it throw instead at the conversion stage.
            result = Double.NaN;
        }

        return result;
    }

    /**
     * @param pTotal  output param containing pressure row total
     * @param esTotal output param containing ecosystem column total
     */
    public static double getComponentTotals(double[][] matrix, double[] pTotal, double[] esTotal) {
        double totalTotal = 0;
        for (int b = 0; b < matrix.length; b++) {
            for (int e = 0; e < matrix[b].length; e++) {
                pTotal[b] += matrix[b][e];
                esTotal[e] += matrix[b][e];
                totalTotal += matrix[b][e];
            }
        }
        return totalTotal;
    }

    public static Map<Integer, Double> impactPerComponent(int[] components, double[] totals) {
        return IntStream
                .range(0, components.length).boxed()
                .collect(toMap(i -> components[i], i -> totals[i]));
    }

    public ReportResponseDto generateReportData(CalculationResult calc, boolean computeChart, String preferredLanguage)
        throws FactoryException, TransformException, SymphonyStandardAppException, IOException {
        var scenario = calc.getScenarioSnapshot();
        var coverage = calc.getCoverage();
        if(coverage == null) {
            logger.info("Recalculating raster data for purged calculation: '" + calc.getCalculationName() + "' " +
                             "with id " + calc.getId());
            coverage = calcService.recreateCoverageFromResult(scenario, calc);
        }

         var stats = getStatistics(coverage);

        var impactMatrix = calc.getImpactMatrix();
        int pLen = impactMatrix.length, esLen = impactMatrix[0].length;

        double[] pTotal = new double[pLen];
        double[] esTotal = new double[esLen];
        // N.B: Will set pTotal and esTotal as side effect!
        double total = getComponentTotals(impactMatrix, pTotal, esTotal);

        ReportResponseDto report = new ReportResponseDto();

        report.baselineName = calc.getBaselineVersion().getName();
        report.operationName = calc.getOperationName();
        report.operationOptions = calc.getOperationOptions() != null ?
            calc.getOperationOptions() : Collections.emptyMap();
        report.name = calc.getCalculationName();
        // Does not sum to exactly 100% for some reason? I.e. assert(getComponentTotals(...) == stats
        // .getSum()) not always true. Tolerance?
        report.total = total; //stats.getSum();
        report.min       = stats.min;
        report.max       = stats.max;
        report.average   = stats.average;
        report.stddev    = stats.stddev;
        report.histogram = stats.histogram;

        report.calculatedPixels = stats.pixels;

        double resolution = getResolutionInMetres(coverage);
        report.gridResolution = Double.isNaN(resolution) ? Double.NaN :
                                (double) Math.round(resolution * 100) / 100;
                                // Round to two decimal places and guard for NaN
                                // since Math.round(Double.NaN) returns 0

        report.areaMatrices = new ReportResponseDto.AreaMatrix[scenario.getAreaMatrixMap().size()];
        int am_ix = 0;
        String matrix, areaName;

        for(int areaId : scenario.getAreaMatrixMap().keySet()) {
            areaName = scenario.getAreas().get(areaId).areaName();
            try {
                matrix = matrixService.getSensMatrixbyId(scenario.getAreaMatrixMap().get(areaId), preferredLanguage).getName();
            } catch (SymphonyStandardAppException e) {
                matrix = "<unknown>";
            }
            report.areaMatrices[am_ix++] = new ReportResponseDto.AreaMatrix(areaName, matrix);
        }

        report.normalization = scenario.getNormalization();
        report.impactPerPressure = impactPerComponent(scenario.getPressuresToInclude(), pTotal);
        report.impactPerEcoComponent = impactPerComponent(scenario.getEcosystemsToInclude(), esTotal);
        report.chartWeightThreshold =
            props.getPropertyAsDouble("calc.sankey_chart.link_weight_threshold", 0.001);
        if (computeChart)
            report.chartData = new SankeyChart(scenario.getEcosystemsToInclude(),
                scenario.getPressuresToInclude(), impactMatrix, total,
                report.chartWeightThreshold
            ).getChartData();

        report.geographicalArea =  JTS.transform(scenario.getGeometry(),
                CRS.findMathTransform(DefaultGeographicCRS.WGS84,
                    coverage.getCoordinateReferenceSystem2D())).getArea();


        report.scenarioChanges = scenario.getChangesForReport();
        report.timestamp = calc.getTimestamp().getTime();
        report.overflow = calc.getOverflowForReport();

        return report;
    }

    public double[][] calculateDifferentialImpactMatrix(double[][] imxA, double[][] imxB) {

        int pLen = imxA.length, esLen = imxA[0].length;
        double[][] diffs = new double[pLen][esLen];
        for (int b = 0; b < pLen; b++) {
            for (int e = 0; e < esLen; e++) {
                diffs[b][e] =  imxB[b][e] - imxA[b][e];
            }
        }

        return diffs;
    }

    public ComparisonReportResponseDto
        generateComparisonReportData(
            CalculationResult calcA,
            CalculationResult calcB,
            boolean implicit,
            boolean reverse,
            String preferredLanguage)
                throws FactoryException, TransformException, SymphonyStandardAppException, IOException {
        var report = new ComparisonReportResponseDto();
        report.a = generateReportData(calcA, false, preferredLanguage);
        report.b = generateReportData(calcB, false, preferredLanguage);

        if(implicit) {
            if(reverse) {
                report.b.scenarioChanges = NullNode.getInstance();
            } else {
                report.a.scenarioChanges = NullNode.getInstance();
            }
        }

        double chartWeightThreshold =
            props.getPropertyAsDouble("calc.sankey_chart.link_weight_threshold", 0.001);

        int[] ecoSystems = calcA.getScenarioSnapshot().getEcosystemsToInclude(),
              pressures  = calcA.getScenarioSnapshot().getPressuresToInclude();

        double[][] diffImpact = calculateDifferentialImpactMatrix(calcA.getImpactMatrix(), calcB.getImpactMatrix()),
                   diffImpactPositive = new double[diffImpact.length][diffImpact[0].length],
                   diffImpactNegative = new double[diffImpact.length][diffImpact[0].length];

        for (int b = 0; b < diffImpact.length; b++) {
            for (int e = 0; e < diffImpact[0].length; e++) {
                if (diffImpact[b][e] > 0) {
                    diffImpactPositive[b][e] = diffImpact[b][e];
                }
                if (diffImpact[b][e] < 0) {
                    diffImpactNegative[b][e] = Math.abs(diffImpact[b][e]);
                }
            }
        }

        double totalPositive = Arrays.stream(diffImpactPositive).flatMapToDouble(Arrays::stream).sum(),
               totalNegative = Arrays.stream(diffImpactNegative).flatMapToDouble(Arrays::stream).sum();

        report.chartDataPositive =
            new SankeyChart(ecoSystems, pressures, diffImpactPositive, totalPositive, chartWeightThreshold)
                .getChartData();

        report.chartDataNegative =
            new SankeyChart(ecoSystems, pressures, diffImpactNegative, totalNegative, chartWeightThreshold)
                .getChartData();

        return report;
    }

    private SymphonyBandDto[] flattenAndSort(MetadataComponentDto component) {
        return component.getSymphonyThemes().stream()
            .flatMap(theme -> theme.getBands().stream()).sorted(
                Comparator.comparingInt(SymphonyBandDto::getBandNumber))
            .toArray(SymphonyBandDto[]::new);
    }

    static int[] uniqueIntersection(int[] a, int[] b) {
        Set<Integer> s_a = Arrays.stream(a).boxed().collect(Collectors.toSet()),
                     s_b = Arrays.stream(b).boxed().collect(Collectors.toSet());
        s_a.retainAll(s_b);
        return s_a.stream().mapToInt(i -> i).toArray();
    }

    static final String rptRowFormat = "%s" + CSV_FIELD_SEPARATOR + "%.2f%%";

    public String generateCSVReport(CalculationResult calc, Locale locale) throws SymphonyStandardAppException {
        var metadata = metaDataService.findMetadata(calc.getBaselineVersion().getName(), locale.getLanguage());
        var ecocomponentMetadata = flattenAndSort(metadata.getEcoComponent());
        var pressureMetadata = flattenAndSort(metadata.getPressureComponent());

        var scenario = calc.getScenarioSnapshot();
        var featuredEcosystems = scenario.getEcosystemsToInclude();
        var featuredPressures = scenario.getPressuresToInclude();

        var impactMatrix = calc.getImpactMatrix();
        double[] pTotal = new double[impactMatrix.length];
        double[] esTotal = new double[impactMatrix[0].length];
        var total = getComponentTotals(impactMatrix, pTotal, esTotal);

        var string = new StringWriter();
        try (PrintWriter writer = new PrintWriter(string)) {
            // Sankey header line
            writer.print(calc.getCalculationName());
            writer.print(CSV_FIELD_SEPARATOR);
            writer.print(Arrays.stream(featuredEcosystems).mapToObj(e -> ecocomponentMetadata[e].getTitle()) // column headers
                    .collect(Collectors.joining(String.valueOf(CSV_FIELD_SEPARATOR))));
            writer.println(CSV_FIELD_SEPARATOR + "TOTAL");

            // Sankey matrix body
            IntStream.range(0, featuredPressures.length).forEach(i -> {
                writer.print(pressureMetadata[featuredPressures[i]].getTitle()); // pressure row header
                writer.print(CSV_FIELD_SEPARATOR);
                IntStream.range(0, featuredEcosystems.length).forEach(j -> {
                    writer.print(impactMatrix[i][j]);
                    writer.print(CSV_FIELD_SEPARATOR);
                });
                writer.println(pTotal[i]);
            });

            writer.print("TOTAL");
            IntStream.range(0, featuredEcosystems.length).forEach(j ->
                writer.format(locale, "%c%.4f", CSV_FIELD_SEPARATOR, esTotal[j])
            );
            writer.format(locale, "%c%.4f", CSV_FIELD_SEPARATOR, total);
            writer.println();
            writer.println();

            // Impact per pressure
            writer.println("PRESSURE" + CSV_FIELD_SEPARATOR + "Impact per Pressure");
            writeReportRows(locale, pressureMetadata, featuredPressures, pTotal, total, writer);
            writer.println();

            // Impact per pressure
            writer.println("ECOSYSTEM" + CSV_FIELD_SEPARATOR + "Impact per Ecosystem");
            writeReportRows(locale, ecocomponentMetadata, featuredEcosystems, esTotal, total, writer);
        }
        string.flush();
        return string.toString();
    }

    static final String cmpRowFormat = "%s" + CSV_FIELD_SEPARATOR + "%.2f" + CSV_FIELD_SEPARATOR + "%.2f" + CSV_FIELD_SEPARATOR + "%.2f%%";

    public String generateCSVComparisonReport(CalculationResult calcA, CalculationResult calcB, Locale locale) throws SymphonyStandardAppException {
        MetadataDto metadata = metaDataService.findMetadata(calcA.getBaselineVersion().getName(), locale.getLanguage());
        SymphonyBandDto[]   ecocomponentMetadata = flattenAndSort(metadata.getEcoComponent()),
                                pressureMetadata = flattenAndSort(metadata.getPressureComponent());

        ScenarioSnapshot scenarioA = calcA.getScenarioSnapshot(), scenarioB = calcB.getScenarioSnapshot();
        int[] featuredEcosystems = uniqueIntersection(  scenarioA.getEcosystemsToInclude(),
                                                        scenarioB.getEcosystemsToInclude()),
              featuredPressures = uniqueIntersection(   scenarioA.getPressuresToInclude(),
                                                        scenarioB.getPressuresToInclude());

        double[][] impactMatrixA = calcA.getImpactMatrix(), impactMatrixB = calcB.getImpactMatrix();;
        double[] pTotalA = new double[impactMatrixA.length], pTotalB = new double[impactMatrixB.length];
        double[] esTotalA = new double[impactMatrixA[0].length], esTotalB = new double[impactMatrixB[0].length];
        double totalA = getComponentTotals(impactMatrixA, pTotalA, esTotalA),
               totalB = getComponentTotals(impactMatrixB, pTotalB, esTotalB),
               totalDiff = totalB - totalA;

        StringWriter string = new StringWriter();

        try (PrintWriter writer = new PrintWriter(string)) {
            writer.println("Comparing:"                     + CSV_FIELD_SEPARATOR +
                "\"" +  calcA.getCalculationName() + "\""   + CSV_FIELD_SEPARATOR +
                "\"" +  calcB.getCalculationName() + "\""   + CSV_FIELD_SEPARATOR);

            writer.println();
            writer.println("TOTAL" +
                            CSV_FIELD_SEPARATOR + "Total impact (Scenario A)" +
                            CSV_FIELD_SEPARATOR + "Total impact (Scenario B)" +
                            CSV_FIELD_SEPARATOR + "Relative change");
            writer.format(locale, cmpRowFormat, "All", totalA, totalB, totalDiff == 0 ? 0 : 100 * (totalDiff / totalA));

            writer.println();
            writer.println();
            writer.println("PRESSURE" +
                            CSV_FIELD_SEPARATOR + "Impact per Pressure (Scenario A)" +
                            CSV_FIELD_SEPARATOR + "Impact per Pressure (Scenario B)" +
                            CSV_FIELD_SEPARATOR + "Relative change");

            writeComparisonRows(locale, pressureMetadata, featuredPressures, pTotalA, pTotalB, writer);

            writer.println();
            writer.println("ECOSYSTEM" +
                CSV_FIELD_SEPARATOR + "Impact per Ecosystem (Scenario A)" +
                CSV_FIELD_SEPARATOR + "Impact per Ecosystem (Scenario B)" +
                CSV_FIELD_SEPARATOR + "Relative change");

            writeComparisonRows(locale, ecocomponentMetadata, featuredEcosystems, esTotalA, esTotalB, writer);
        }
        string.flush();
        return string.toString();
    }

    static void writeReportRows(Locale locale, SymphonyBandDto[] meta, int[] featured, double[] total, double sum, PrintWriter writer) {
        IntStream.range(0, featured.length).forEach(i -> {
            writer.format(locale, rptRowFormat,
                meta[featured[i]].getTitle(),
                sum == 0 ? 0 : 100 * (total[i] / sum));
            writer.println();
        });
    }

    static void writeComparisonRows(Locale locale, SymphonyBandDto[] meta, int[] featured, double[] totalA, double[] totalB, PrintWriter writer) {
        IntStream.range(0, featured.length).forEach(i -> {
                writer.format(locale, cmpRowFormat,
                    meta[featured[i]].getTitle(), totalA[i], totalB[i],
                    totalA[i] == 0 ? 0 : 100 * ((totalB[i] - totalA[i]) / totalA[i]));
                writer.println();
        });
    }

    public MultiComparisonAuxiliary getLayerIndicesToListForResult(ComparisonResult result, boolean excludeZeroes) {
        int[] ecosystemsToList = excludeZeroes ? IntStream.range(0, result.includedEcosystems.length)
            .filter(i -> result.totalPerEcosystem.get(result.includedEcosystems[i]) != 0)
            .toArray() : IntStream.range(0, result.includedEcosystems.length).toArray();

        int[] pressuresToList = excludeZeroes ? IntStream.range(0, result.includedPressures.length)
            .filter(i -> result.totalPerPressure.get(result.includedPressures[i]) != 0)
            .toArray() : IntStream.range(0, result.includedPressures.length).toArray();

        double[][] resultMatrix = new double[pressuresToList.length][ecosystemsToList.length];

        for (int p = 0; p < pressuresToList.length; p++) {
            for (int e = 0; e < ecosystemsToList.length; e++) {
                resultMatrix[p][e] = result.result[pressuresToList[p]]
                    [ecosystemsToList[e]];
            }
        }

        return new MultiComparisonAuxiliary(ecosystemsToList, pressuresToList, resultMatrix);
    }

    public CompoundComparisonExport generateMultiComparisonAsJSON(
        CompoundComparison comparison, String preferredLanguage, boolean excludeZeroes)
            throws SymphonyStandardAppException {

        CompoundComparisonExport result = new CompoundComparisonExport();

        BaselineVersion baseline = em.createNamedQuery("BaselineVersion.getById", BaselineVersion.class)
            .setParameter("id", comparison.getBaseline().getId())
            .getSingleResult();

        Map<Integer, String>
            ecoTitles = metaDataService.getComponentTitles(
                comparison.getBaseline().getId(),
                LayerType.ECOSYSTEM, preferredLanguage),
            pressureTitles = metaDataService.getComponentTitles(
                comparison.getBaseline().getId(),
                LayerType.PRESSURE, preferredLanguage);

        ComparisonResult[] cmpResults = excludeZeroes ?
            comparison.getResult().values().stream()
                .filter(c -> c.cumulativeTotal != 0)
                .toArray(ComparisonResult[]::new) :
            comparison.getResult().values().toArray(new ComparisonResult[0]);

        MultiComparisonAuxiliary[] layersToList = new MultiComparisonAuxiliary[cmpResults.length];

        for (int i = 0; i < cmpResults.length; ++i) {
            layersToList[i] = getLayerIndicesToListForResult(cmpResults[i], excludeZeroes);
        }

        result.id = comparison.getId();
        result.baselineName = baseline.getName();
        result.name = comparison.getName();
        result.result = IntStream.range(0, cmpResults.length)
            .mapToObj(i -> {
                ComparisonResultExport cmpResult = new ComparisonResultExport();
                cmpResult.calculationName = cmpResults[i].calculationName;
                cmpResult.ecosystemTitles = Arrays.stream(layersToList[i].ecosystems)
                    .mapToObj(e -> ecoTitles.get(cmpResults[i].includedEcosystems[e]))
                    .toArray(String[]::new);
                cmpResult.pressureTitles = Arrays.stream(layersToList[i].pressures)
                    .mapToObj(p -> pressureTitles.get(cmpResults[i].includedPressures[p]))
                    .toArray(String[]::new);
                cmpResult.comparisonMatrix = layersToList[i].result;
                cmpResult.cumulativeTotal = cmpResults[i].cumulativeTotal;
                return cmpResult;

            }).toArray(ComparisonResultExport[]::new);

        return result;
    }

    private Sheet createSheet(int rows, int cols, String title) {
        Sheet newSheet = new Sheet(title, rows, Math.max(cols, 5));

        Range titleCell = newSheet.getRange(1, 2, 1, 1);

        titleCell.setValue(title);
        titleCell.setStyle(ODSStyles.cmpName);

        newSheet.setRowHeight(1, 14.0);
        newSheet.setColumnWidth(0, 5.0);

        return newSheet;
    }

    // TODO: Probable opportunity to refactor for less repetitive code.
    public byte[] generateMultiComparisonAsODS(
        CompoundComparison comparison, String preferredLanguage, String localisedHeading, boolean excludeZeroes) throws Exception {

        String title = localisedHeading + " \"" + comparison.getName() + "\"";

        Map<Integer, String>
            ecoTitles = metaDataService.getComponentTitles(
                comparison.getBaseline().getId(),
                LayerType.ECOSYSTEM, preferredLanguage),
            pressureTitles = metaDataService.getComponentTitles(
                comparison.getBaseline().getId(),
                LayerType.PRESSURE, preferredLanguage);

        SpreadSheet templateDocument = new SpreadSheet();

        ComparisonResult[] cmpResults =
            excludeZeroes ?
                comparison.getResult().values().stream()
                    .filter(cmp -> cmp.cumulativeTotal != 0)
                    .toArray(ComparisonResult[]::new) :
            comparison.getResult().values().toArray(new ComparisonResult[comparison.getResult().size()]);

        Sheet totalSheet = createSheet(
            cmpResults.length * ODF_CALC_TOTALS_SECTION + ODF_TITLE_ROWS_TOTAL, 5,
            title);

        totalSheet.setName(comparison.getName() + " - Total");
        totalSheet.setColumnWidth(1, ODF_TITLE_COLWIDTH);

        // guard against duplicate calculation names
        Map<String, Integer> cmpNameCounts = new HashMap<>();

        for(ComparisonResult cmp : cmpResults) {
            cmpNameCounts.put(cmp.calculationName, 0);
        }

        // Semantically consistent index variables.
        int e, p, j, cmpIndex = 0;

        for (ComparisonResult cmp : cmpResults) {

            Range nextCell;

            Sheet nextSheet = createSheet(
                cmp.includedEcosystems.length + ODF_TITLE_ROWS + 1,     // + 1 totals row
                cmp.includedPressures.length + 3, // + 2 left padding columns + 1 totals column
                title);

            cmpNameCounts.put(cmp.calculationName, cmpNameCounts.get(cmp.calculationName) + 1);

            nextSheet.setName(cmp.calculationName +
                (cmpNameCounts.get(cmp.calculationName) > 1 ?
                " (" + cmpNameCounts.get(cmp.calculationName) + ")" : ""));

            nextCell = nextSheet.getRange(3, 1);
            nextCell.setValue(cmp.calculationName);
            nextCell.setStyle(ODSStyles.calcName);
            nextSheet.setRowHeight(ODF_TITLE_ROWS - 2, 2.0);

            MultiComparisonAuxiliary layersToList = getLayerIndicesToListForResult(cmp, excludeZeroes);

            for (e = 0; e < layersToList.ecosystems.length; e++) {
                nextCell = nextSheet.getRange(ODF_TITLE_ROWS + e, 1);
                nextCell.setValue(ecoTitles.get(cmp.includedEcosystems[layersToList.ecosystems[e]]));
                nextCell.setStyle(ODSStyles.ecoHeader);
            }

            nextSheet.setColumnWidth(1, ODF_TITLE_COLWIDTH);

            for (p = 0; p < layersToList.pressures.length; p++) {
                nextCell = nextSheet.getRange(ODF_TITLE_ROWS - 1, 2 + p);
                nextCell.setValue(pressureTitles.get(cmp.includedPressures[layersToList.pressures[p]]));
                nextCell.setStyle(ODSStyles.pressureHeader);
                nextSheet.setColumnWidth(2 + p, ODF_TITLE_COLWIDTH);
            }

            for (e = 0; e < layersToList.result[0].length; e++) {
                for (p = 0; p < layersToList.result.length; p++) {
                    nextCell = nextSheet.getRange(ODF_TITLE_ROWS + e, 2 + p);
                    nextCell.setValue(layersToList.result[p][e]);
                    nextCell.setStyle(ODSStyles.valueStyle);
                }
            }

            nextCell = nextSheet.getRange(ODF_TITLE_ROWS - 1, 2 + layersToList.pressures.length);
            nextCell.setValue("TOTAL");
            nextCell.setStyle(ODSStyles.totalHE);
            nextSheet.setColumnWidth(2 + layersToList.pressures.length, ODF_TITLE_COLWIDTH);

            nextCell = nextSheet.getRange(ODF_TITLE_ROWS + layersToList.ecosystems.length, 1);
            nextCell.setValue("TOTAL");
            nextCell.setStyle(ODSStyles.totalHP);

            for (e = 0; e < layersToList.ecosystems.length; e++) {
                nextCell = nextSheet.getRange(ODF_TITLE_ROWS + e, 2 + layersToList.pressures.length);
                nextCell.setValue(cmp.totalPerEcosystem.get(cmp.includedEcosystems[layersToList.ecosystems[e]]));
                nextCell.setStyle(ODSStyles.totalE);
            }

            for (p = 0; p < layersToList.pressures.length; p++) {
                nextCell = nextSheet.getRange(ODF_TITLE_ROWS + layersToList.ecosystems.length, 2 + p);
                nextCell.setValue(cmp.totalPerPressure.get(cmp.includedPressures[layersToList.pressures[p]]));
                nextCell.setStyle(ODSStyles.totalP);
            }

            nextCell = nextSheet.getRange(
                ODF_TITLE_ROWS + layersToList.ecosystems.length,
                2 + layersToList.pressures.length);
            nextCell.setValue(cmp.cumulativeTotal);
            nextCell.setStyle(ODSStyles.totalC);

            // ----- Totals sheet -----

            int calcSectionOffset = cmpIndex * ODF_CALC_TOTALS_SECTION + ODF_TITLE_ROWS_TOTAL,
                sectionLength = Math.max(layersToList.pressures.length, layersToList.ecosystems.length);
            totalSheet.appendColumns(sectionLength);

            nextCell = totalSheet.getRange(calcSectionOffset, 1);
            nextCell.setValue(cmp.calculationName);
            nextCell.setStyle(ODSStyles.calcName);
            totalSheet.setRowHeight(calcSectionOffset + 2, 2.0);
            totalSheet.setRowHeight(calcSectionOffset + 5, 2.0);

            // Left aligned table with row titles column in third column (index 2)
            nextCell = totalSheet.getRange(calcSectionOffset, 2);
            nextCell.setValue("Pressure");
            nextCell.setStyle(ODSStyles.pressureHeader);
            nextCell = totalSheet.getRange(calcSectionOffset + 1, 2);
            nextCell.setValue("TOTAL");
            nextCell.setStyle(ODSStyles.totalHP);


            nextCell = totalSheet.getRange(calcSectionOffset + 3, 2);
            nextCell.setValue("Ecosystem");
            nextCell.setStyle(ODSStyles.pressureHeader);
            nextCell = totalSheet.getRange(calcSectionOffset + 4, 2);
            nextCell.setValue("TOTAL");
            nextCell.setStyle(ODSStyles.totalHP);

            // Left aligned table with horizontal title column in third column:
            // values start in fourth column, hence index + 3
            for (p = 0; p < layersToList.pressures.length; p++) {
                nextCell = totalSheet.getRange(calcSectionOffset, 3 + p);
                nextCell.setValue(pressureTitles.get(cmp.includedPressures[layersToList.pressures[p]]));
                nextCell.setStyle(ODSStyles.pressureHeader);
                nextCell = totalSheet.getRange(calcSectionOffset + 1, 3 + p);
                nextCell.setValue(cmp.totalPerPressure.get(cmp.includedPressures[layersToList.pressures[p]]));
                nextCell.setStyle(ODSStyles.totalP);
                nextSheet.setColumnWidth(3 + p, ODF_TITLE_COLWIDTH);
            }

            for (e = 0; e < layersToList.ecosystems.length; e++) {
                nextCell = totalSheet.getRange(calcSectionOffset + 3, 3 + e);
                nextCell.setValue(ecoTitles.get(cmp.includedEcosystems[layersToList.ecosystems[e]]));
                nextCell.setStyle(ODSStyles.pressureHeader);
                nextCell = totalSheet.getRange(calcSectionOffset + 4, 3 + e);
                nextCell.setValue(cmp.totalPerEcosystem.get(cmp.includedEcosystems[layersToList.ecosystems[e]]));
                nextCell.setStyle(ODSStyles.totalP);
            }

            if (cmpIndex < cmpResults.length - 1) {
                j = 0;
                while (j < sectionLength + 2) {
                    nextCell = totalSheet.getRange(calcSectionOffset + ODF_CALC_TOTALS_SECTION - 1, 1 + j);
                    nextCell.setStyle(ODSStyles.resultSep);
                    if (j != 1) totalSheet.setColumnWidth(1 + j, ODF_TITLE_COLWIDTH);
                    ++j;
                }
            }

            templateDocument.appendSheet(nextSheet);
            ++cmpIndex;
        }

        templateDocument.addSheet(totalSheet, 0);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        templateDocument.save(out);

        return out.toByteArray();
    }
}
