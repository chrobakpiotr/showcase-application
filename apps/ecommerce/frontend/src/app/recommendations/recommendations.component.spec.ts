import type { Mock } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { RecommendationsComponent } from '@app/recommendations/recommendations.component';
import { RecommendationsService } from '@app/recommendations/recommendations.service';

describe('RecommendationsComponent', () => {
  beforeEach(() => {
    vi.useFakeTimers({ advanceTimeDelta: 1, shouldAdvanceTime: true });
  });
  afterEach(() => {
    vi.useRealTimers();
  });
  let fixture: ComponentFixture<RecommendationsComponent>;
  let component: RecommendationsComponent;
  let getRecommendationsSpy: Mock;

  function setup(): void {
    getRecommendationsSpy = vi.fn().mockName('getRecommendations');
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

  it('should load and render recommendations', async () => {
    setup();
    getRecommendationsSpy.mockReturnValue(
      of({
        assistantAvailable: true,
        recommendations: [
          { sku: 'SKU-1', productName: 'Mouse', reason: 'Good fit.' },
        ],
      })
    );
    component.emailControl.setValue('john.doe@test.com');

    component.loadRecommendations();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();

    expect(component.recommendations()).toHaveLength(1);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(
      compiled.querySelector('[data-testid="recommendation-card"]')
    ).toBeTruthy();
  });

  it('should show unavailable hint when the backend returns a fallback response', async () => {
    setup();
    getRecommendationsSpy.mockReturnValue(
      of({ assistantAvailable: false, recommendations: [] })
    );
    component.emailControl.setValue('john.doe@test.com');

    component.loadRecommendations();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.unavailable-hint')).toBeTruthy();
  });

  it('should show an error message on HTTP error', async () => {
    setup();
    getRecommendationsSpy.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500 }))
    );
    component.emailControl.setValue('john.doe@test.com');

    component.loadRecommendations();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();

    expect(component.errorMessage()).toBe(
      'Failed to load recommendations. Please try again.'
    );
  });
});
