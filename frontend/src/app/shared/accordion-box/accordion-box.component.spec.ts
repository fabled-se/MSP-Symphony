import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

import { AccordionBoxComponent } from './accordion-box.component';
import { IconButtonComponent } from '../icon-button/icon-button.component';
import { IconComponent } from '../icon/icon.component';

function setUp() {
  const fixture: ComponentFixture<AccordionBoxComponent> = TestBed.createComponent(AccordionBoxComponent);
  const component: AccordionBoxComponent = fixture.componentInstance;
  return { component, fixture };
}

describe('AccordionBoxComponent', () => {
  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [AccordionBoxComponent, IconButtonComponent, IconComponent]
    }).compileComponents();
  }));

  it('should create', () => {
    const { component } = setUp();
    expect(component).toBeTruthy();
  });
});
