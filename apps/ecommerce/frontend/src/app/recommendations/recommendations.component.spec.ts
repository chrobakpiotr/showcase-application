import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { RecommendationsComponent } from '@app/recommendations/recommendations.component';
import { RecommendationsService } from '@app/recommendations/recommendations.service';

describe('RecommendationsComponent', () => {
  let fixture: ComponentFixture<RecommendationsComponent>;
  let component: RecommendationsComponent;
  let getRecommendationsSpy: jasmine.Spy;

  function setup(): void {
    getRecommendationsSpy = jasmine.createSpy('getRecommendations');
    TestBed.configureTestingModule({
      imports: [RecommendationsComponent],
      providers: [
        {
          provide: RecommendationsService,
          useValue: { getRecommendations: getRecommendationsSpy },
        },
      ],
    });
    fixture = TestBed.createComponent(RecommendationsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should create', () => {
    setup();
    expect(component).toBeTruthy();
  });

  it('should not load recommendations when the email is blank', () => {
    setup();
    component.emailControl.setValue('   ');
    component.loadRecommendations();
    expect(getRecommendationsSpy).not.toHaveBeenCalled();
  });

  it('should load and render recommendations', fakeAsync(() => {
    setup();
    getRecommendationsSpy.and.returnValue(
      of({
        assistantAvailable: true,
        recommendations: [
          { sku: 'SKU-1', productName: 'Mouse', reason: 'Good fit.' },
        ],
      })
    );
    component.emailControl.setValue('john.doe@test.com');

    component.loadRecommendations();
    tick();
    fixture.detectChanges();

    expect(component.recommendations()).toHaveSize(1);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(
      compiled.querySelector('[data-testid="recommendation-card"]')
    ).toBeTruthy();
  }));

  it('should show unavailable hint when the backend returns a fallback response', fakeAsync(() => {
    setup();
    getRecommendationsSpy.and.returnValue(
      of({ assistantAvailable: false, recommendations: [] })
    );
    component.emailControl.setValue('john.doe@test.com');

    component.loadRecommendations();
    tick();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.unavailable-hint')).toBeTruthy();
  }));

  it('should show an error message on HTTP error', fakeAsync(() => {
    setup();
    getRecommendationsSpy.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500 }))
    );
    component.emailControl.setValue('john.doe@test.com');

    component.loadRecommendations();
    tick();
    fixture.detectChanges();

    expect(component.errorMessage()).toBe(
      'Failed to load recommendations. Please try again.'
    );
  }));
});
