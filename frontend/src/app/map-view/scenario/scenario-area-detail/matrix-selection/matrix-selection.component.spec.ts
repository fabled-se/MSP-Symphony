import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { provideMockStore } from '@ngrx/store/testing';
import { SharedModule } from '@shared/shared.module';
import { TranslationSetupModule } from '@src/app/app-translation-setup.module';
import { initialState as metadata } from '@data/metadata/metadata.reducers';
import { initialState as area } from '@data/area/area.reducers';
import { initialState as scenario } from '@data/scenario/scenario.reducers';
import { MatrixSelectionComponent } from './matrix-selection.component';
import { MatRadioButton, MatRadioModule } from "@angular/material/radio";
import { MatSelect, MatSelectModule } from "@angular/material/select";
import { NormalizationType } from "@data/calculation/calculation.service";
import { TranslateModule, TranslateService } from "@ngx-translate/core";

describe('MatrixSelectionComponent', () => {
  let component: MatrixSelectionComponent;
  let fixture: ComponentFixture<MatrixSelectionComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      imports: [
        SharedModule,
        TranslationSetupModule,
        MatRadioModule,
        MatSelectModule,
        TranslateModule.forRoot()
      ],
      declarations: [MatrixSelectionComponent, MatRadioButton, MatSelect],
      providers: [
        TranslateService,
        provideMockStore(
        { initialState: {
          metadata: metadata,
          area: area,
          scenario: scenario,
          user: { baseline: undefined }
        }})
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(MatrixSelectionComponent);
    component = fixture.componentInstance;
    component.scenario = {
      operation: 0,
      operationOptions: {},
      id: -1,
      timestamp: 0,
      baselineId: 0,
      changes: {},
      ecosystemsToInclude: [],
      name: "",
      normalization: { type:NormalizationType.Domain },
      pressuresToInclude: [],
      areas: [{
        id: 1,
        feature: {type:'Feature', geometry: {type: 'Polygon', coordinates:[]}, properties: { feature: { name: ''} }},
        changes: {},
        matrix: {matrixType: 'STANDARD'},
        scenarioId: -1,
        excludedCoastal: null,
        customCalcAreaId: null
      }],
      latestCalculationId: null
    };
    component.areaIndex = 0;
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
