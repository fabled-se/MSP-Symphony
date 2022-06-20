package se.havochvatten.symphony.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.response.Response;
import org.junit.Test;
import se.havochvatten.symphony.dto.AreaMatrixResponse;
import se.havochvatten.symphony.dto.AreaSelectionResponseDto;
import se.havochvatten.symphony.dto.SensitivityMatrix;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class CalculationParametersRESTTest extends RESTTest {

    @Test
    public void testGetAreaSelectionParam() throws IOException {
        String endpoint = endpoint("/calculationparams/areamatrices/{baselineName}");
        String baselineName = "BASELINE2019";
        JsonNode jsNode = lysekilPolygon();

        Response response = given().
                auth().
                preemptive().
                basic(getUsername(), getPassword()).
                pathParam("baselineName", baselineName).
                when().
                header("Content-Type", "application/json").
                body(jsNode).
                post(endpoint);

        assertThat(response.getStatusCode(), is(200));

        AreaSelectionResponseDto areaTypeResp = response.getBody().jsonPath().getObject("",
				AreaSelectionResponseDto.class);
        assertTrue(areaTypeResp.getAreaTypes().size() > 0);
        assertTrue(areaTypeResp.getAreaTypes().stream().anyMatch((a) -> "Kustområde".equals(a.getName())));
        assertTrue(areaTypeResp.getAreaTypes().stream().anyMatch((a) -> "n-område".equals(a.getName())));
    }

    private SensitivityMatrix firstDefaultMatrix(List<AreaMatrixResponse> areaMatrixResponse,
												 List<SensitivityMatrix> sensitivityMatrices) {
        SensitivityMatrix firstDefaultMatrix = null;
        List<AreaMatrixResponse> amrList =
				areaMatrixResponse.stream().filter(a -> a.isDefaultArea()).collect(Collectors.toList());
        Integer firstDefaultMatrixId = amrList.isEmpty() ? 0 : amrList.get(0).getMatrixId();
        sensitivityMatrices.stream().filter(s -> s.getMatrixId().equals(firstDefaultMatrixId));
        firstDefaultMatrix = sensitivityMatrices.stream().findFirst().isPresent() ?
				sensitivityMatrices.stream().findFirst().get() : null;
        return firstDefaultMatrix;
    }

    private boolean hasCutArea(List<AreaMatrixResponse> areaMatrixResponse, String cutArea) {
        boolean hasCutArea = false;
        for (AreaMatrixResponse a : areaMatrixResponse) {
            if (a.getPolygons().stream().anyMatch(p -> p.equals(cutArea))) {
                hasCutArea = true;
            }
        }
        return hasCutArea;
    }

    private String expectedCutPolygon() throws IOException {
        File sourceFile = new File("src/test/resources/polygons/lysekilMSPPart.json");
        List<String> rows = Files.readAllLines(sourceFile.toPath(), StandardCharsets.UTF_8);
        String lysekilMSPPart = rows.stream().collect(Collectors.joining(""));
        return lysekilMSPPart.replaceAll("\\s+", "");
    }

    private JsonNode lysekilPolygon() throws IOException {
        File sourceFile = new File("src/test/resources/polygons/lysekil.json");
        List<String> rows = Files.readAllLines(sourceFile.toPath(), StandardCharsets.UTF_8);
        String lysekilPoly = rows.stream().collect(Collectors.joining(""));
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsNode = mapper.readTree(lysekilPoly);
        return jsNode;
    }
}
