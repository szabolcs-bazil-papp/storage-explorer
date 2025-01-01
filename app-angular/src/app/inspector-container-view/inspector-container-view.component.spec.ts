import { ComponentFixture, TestBed } from '@angular/core/testing';

import { InspectorContainerViewComponent } from './inspector-container-view.component';

describe('InspectorContainerViewComponent', () => {
  let component: InspectorContainerViewComponent;
  let fixture: ComponentFixture<InspectorContainerViewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InspectorContainerViewComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(InspectorContainerViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
