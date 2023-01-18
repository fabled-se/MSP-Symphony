import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { TranslationSetupModule } from '@src/app/app-translation-setup.module';
import { DialogRef } from '@src/app/shared/dialog/dialog-ref';
import { DialogConfig } from '@src/app/shared/dialog/dialog-config';

import { CreateUserAreaModalComponent } from './create-user-area-modal.component';

function setUp() {
  const fixture: ComponentFixture<CreateUserAreaModalComponent> = TestBed.createComponent(
    CreateUserAreaModalComponent
  );
  const component: CreateUserAreaModalComponent = fixture.componentInstance;
  return { component, fixture };
}

describe('CreateUserAreaModalComponent', () => {
  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [CreateUserAreaModalComponent],
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
