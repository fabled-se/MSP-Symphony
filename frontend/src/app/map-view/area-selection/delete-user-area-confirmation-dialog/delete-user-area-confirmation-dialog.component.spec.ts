import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { DialogRef } from '@src/app/shared/dialog/dialog-ref';
import { DialogConfig } from '@src/app/shared/dialog/dialog-config';
import { TranslationSetupModule } from '@src/app/app-translation-setup.module';

import { DeleteUserAreaConfirmationDialogComponent } from './delete-user-area-confirmation-dialog.component';

function setUp() {
  const fixture: ComponentFixture<DeleteUserAreaConfirmationDialogComponent> = TestBed.createComponent(
    DeleteUserAreaConfirmationDialogComponent
  );
  const component: DeleteUserAreaConfirmationDialogComponent = fixture.componentInstance;
  return { component, fixture };
}

describe('DeleteUserAreaConfirmationDialogComponent', () => {
  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [DeleteUserAreaConfirmationDialogComponent],
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
