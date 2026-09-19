import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ForbiddenComponent } from '@app/forbidden/forbidden.component';

describe('ForbiddenComponent', () => {
  let fixture: ComponentFixture<ForbiddenComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ForbiddenComponent],
      providers: [provideRouter([])],
    });

    fixture = TestBed.createComponent(ForbiddenComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('renders a readable forbidden state with a dashboard link', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Forbidden');
    expect(compiled.querySelector('a')?.getAttribute('href')).toBe(
      '/dashboard'
    );
  });
});
