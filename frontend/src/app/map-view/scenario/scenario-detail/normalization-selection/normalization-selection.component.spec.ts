import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

import { NormalizationSelectionComponent } from './normalization-selection.component';
import { TranslationSetupModule } from '@src/app/app-translation-setup.module';

function setUp() {
  const fixture: ComponentFixture<NormalizationSelectionComponent> = TestBed.createComponent(
    NormalizationSelectionComponent
  );
  const component: NormalizationSelectionComponent = fixture.componentInstance;
  return { component, fixture };
}

describe('NormalizationSelectionComponent', () => {
  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      imports: [TranslationSetupModule],
      declarations: [NormalizationSelectionComponent]
    }).compileComponents();
  }));

  /*it('should create', () => {
    const { component } = setUp();
    expect(component).toBeTruthy();
  });*/
});
