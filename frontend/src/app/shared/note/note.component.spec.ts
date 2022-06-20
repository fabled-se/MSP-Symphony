import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { NoteComponent } from './note.component';
import { IconComponent } from '../icon/icon.component';

function setUp() {
  const fixture: ComponentFixture<NoteComponent> = TestBed.createComponent(NoteComponent);
  const component: NoteComponent = fixture.componentInstance;
  return { component, fixture };
}

describe('NoteComponent', () => {
  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [NoteComponent, IconComponent]
    }).compileComponents();
  }));

  it('should create', () => {
    const { component } = setUp();
    expect(component).toBeTruthy();
  });
});
