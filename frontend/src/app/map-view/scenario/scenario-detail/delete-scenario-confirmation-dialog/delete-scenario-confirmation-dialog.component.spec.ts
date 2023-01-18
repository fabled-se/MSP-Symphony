import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { DialogRef } from '@src/app/shared/dialog/dialog-ref';
import { DialogConfig } from '@src/app/shared/dialog/dialog-config';
import { TranslationSetupModule } from '@src/app/app-translation-setup.module';

import { DeleteScenarioConfirmationDialogComponent } from './delete-scenario-confirmation-dialog.component';

function setUp() {
  const fixture: ComponentFixture<DeleteScenarioConfirmationDialogComponent> = TestBed.createComponent(
    DeleteScenarioConfirmationDialogComponent
  );
  const component: DeleteScenarioConfirmationDialogComponent = fixture.componentInstance;
  return { component, fixture };
}

describe('DeleteUserAreaConfirmationDialogComponent', () => {
  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [DeleteScenarioConfirmationDialogComponent],
      imports: [TranslationSetupModule],
      providers: [
        {
          provide: DialogRef,
          useValue: {}
        },
        {
          provide: DialogConfig,
          useValue: {
            data: {
              areaName: ''
            }
          }
        }
      ]
    }).compileComponents();
  }));

  it('should create', () => {
    const { component } = setUp();
    expect(component).toBeTruthy();
  });
});
