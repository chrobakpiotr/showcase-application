import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { NotFoundComponent } from '@app/not-found/not-found.component';

describe('NotFoundComponent', () => {
  let fixture: ComponentFixture<NotFoundComponent>;
  let component: NotFoundComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [NotFoundComponent],
      providers: [provideRouter([])],
    });

    fixture = TestBed.createComponent(NotFoundComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('renders a link back to home', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const link = compiled.querySelector('[data-testid="not-found-home-link"]');
    expect(link).toBeTruthy();
    expect(link?.getAttribute('href')).toBe('/');
  });
});
