import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

import { ModalComponent } from './modal.component';

function setUp() {
  const fixture: ComponentFixture<ModalComponent> = TestBed.createComponent(ModalComponent);
  const component: ModalComponent = fixture.componentInstance;
  return { component, fixture };
}

describe('ModalComponent', () => {
  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [ModalComponent]
    }).compileComponents();
  }));

  it('should create', () => {
    const { component } = setUp();
    expect(component).toBeTruthy();
  });
});
