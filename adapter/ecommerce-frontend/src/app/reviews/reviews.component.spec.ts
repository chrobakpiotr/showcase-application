import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, Subject, throwError } from 'rxjs';

import { AuthService } from '@app/auth/auth.service';
import { ReviewModel, ReviewSummaryModel } from '@app/reviews/review.model';
import { ReviewsComponent } from '@app/reviews/reviews.component';
import { ReviewsService } from '@app/reviews/reviews.service';

describe('ReviewsComponent', () => {
  let fixture: ComponentFixture<ReviewsComponent>;
  let component: ReviewsComponent;
  let reviewsServiceSpy: jasmine.SpyObj<ReviewsService>;

  const review: ReviewModel = {
    reviewId: 'REVIEW-1',
    sku: 'SKU-1',
    authorName: 'Jane Smith',
    rating: 5,
    comment: 'Great!',
    status: 'APPROVED',
    created: '2024-03-15T10:30:00.000Z',
  };

  function setup(roles: string[] = []): void {
    reviewsServiceSpy = jasmine.createSpyObj('ReviewsService', [
      'submitReview',
      'listApprovedReviews',
      'getSummary',
      'listPendingReviews',
      'approveReview',
      'rejectReview',
    ]);
    reviewsServiceSpy.listPendingReviews.and.returnValue(of([review]));

    TestBed.configureTestingModule({
      imports: [ReviewsComponent],
      providers: [
        { provide: ReviewsService, useValue: reviewsServiceSpy },
        { provide: AuthService, useValue: { roles: signal(roles) } },
      ],
    });

    fixture = TestBed.createComponent(ReviewsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should create the component', () => {
    setup();
    expect(component).toBeTruthy();
  });

  it('does not load pending reviews without the REVIEWS_READ role', () => {
    setup();
    expect(reviewsServiceSpy.listPendingReviews).not.toHaveBeenCalled();
    expect(component.canModerate).toBeFalse();
  });

  it('loads pending reviews with the REVIEWS_READ role', () => {
    setup(['REVIEWS_READ']);
    expect(reviewsServiceSpy.listPendingReviews).toHaveBeenCalled();
    expect(component.pendingReviews()).toEqual([review]);
    expect(component.canModerate).toBeTrue();
  });

  it('sets an error message when loading pending reviews fails', () => {
    reviewsServiceSpy = jasmine.createSpyObj('ReviewsService', [
      'submitReview',
      'listApprovedReviews',
      'getSummary',
      'listPendingReviews',
      'approveReview',
      'rejectReview',
    ]);
    reviewsServiceSpy.listPendingReviews.and.returnValue(
      throwError(() => new Error('failed'))
    );
    TestBed.configureTestingModule({
      imports: [ReviewsComponent],
      providers: [
        { provide: ReviewsService, useValue: reviewsServiceSpy },
        { provide: AuthService, useValue: { roles: signal(['REVIEWS_READ']) } },
      ],
    });
    fixture = TestBed.createComponent(ReviewsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.moderationErrorMessage()).toBe(
      'Failed to load pending reviews.'
    );
  });

  it('reports canWrite true with the REVIEWS_WRITE role', () => {
    setup(['REVIEWS_WRITE']);
    expect(component.canWrite).toBeTrue();
  });

  it('does not browse when the form is invalid', () => {
    setup();
    component.browseForm.setValue({ sku: '' });
    component.browse();
    expect(reviewsServiceSpy.listApprovedReviews).not.toHaveBeenCalled();
  });

  it('browses reviews and summary for a sku', () => {
    setup();
    reviewsServiceSpy.listApprovedReviews.and.returnValue(of([review]));
    reviewsServiceSpy.getSummary.and.returnValue(
      of({ sku: 'SKU-1', averageRating: 4.5, reviewCount: 12 })
    );
    component.browseForm.setValue({ sku: 'SKU-1' });
    component.browse();

    expect(component.reviews()).toEqual([review]);
    expect(component.summary()).toEqual({
      sku: 'SKU-1',
      averageRating: 4.5,
      reviewCount: 12,
    });
  });

  it('sets an error message when browsing reviews fails', () => {
    setup();
    reviewsServiceSpy.listApprovedReviews.and.returnValue(
      throwError(() => new Error('failed'))
    );
    reviewsServiceSpy.getSummary.and.returnValue(
      of({ sku: 'SKU-1', averageRating: 0, reviewCount: 0 })
    );
    component.browseForm.setValue({ sku: 'SKU-1' });
    component.browse();

    expect(component.errorMessage()).toBe('Failed to load reviews.');
  });

  it('sets an error message when loading the summary fails', () => {
    setup();
    reviewsServiceSpy.listApprovedReviews.and.returnValue(of([review]));
    reviewsServiceSpy.getSummary.and.returnValue(
      throwError(() => new Error('failed'))
    );
    component.browseForm.setValue({ sku: 'SKU-1' });
    component.browse();

    expect(component.errorMessage()).toBe('Failed to load review summary.');
  });

  it('does not submit when the form is invalid', () => {
    setup();
    component.submitForm.setValue({
      sku: '',
      authorName: '',
      rating: null,
      comment: '',
    });
    component.submit();
    expect(reviewsServiceSpy.submitReview).not.toHaveBeenCalled();
  });

  it('submits a review', () => {
    setup();
    reviewsServiceSpy.submitReview.and.returnValue(of(review));
    component.submitForm.setValue({
      sku: 'SKU-1',
      authorName: 'Jane Smith',
      rating: 5,
      comment: 'Great!',
    });
    component.submit();

    expect(reviewsServiceSpy.submitReview).toHaveBeenCalledWith({
      sku: 'SKU-1',
      authorName: 'Jane Smith',
      rating: 5,
      comment: 'Great!',
    });
    expect(component.submitted()).toBeTrue();
    expect(component.submitForm.value.sku).toBe('');
  });

  it('sets an error message when submitting a review fails', () => {
    setup();
    reviewsServiceSpy.submitReview.and.returnValue(
      throwError(() => new Error('failed'))
    );
    component.submitForm.setValue({
      sku: 'SKU-1',
      authorName: 'Jane Smith',
      rating: 5,
      comment: 'Great!',
    });
    component.submit();

    expect(component.errorMessage()).toBe('Failed to submit review.');
  });

  it('approves a pending review and reloads the queue', () => {
    setup(['REVIEWS_READ', 'REVIEWS_WRITE']);
    reviewsServiceSpy.approveReview.and.returnValue(of(review));
    component.approve('REVIEW-1');

    expect(reviewsServiceSpy.approveReview).toHaveBeenCalledWith('REVIEW-1');
    expect(reviewsServiceSpy.listPendingReviews).toHaveBeenCalledTimes(2);
  });

  it('sets an error message when approving fails', () => {
    setup(['REVIEWS_READ', 'REVIEWS_WRITE']);
    reviewsServiceSpy.approveReview.and.returnValue(
      throwError(() => new Error('failed'))
    );
    component.approve('REVIEW-1');

    expect(component.moderationErrorMessage()).toBe(
      'Failed to approve review.'
    );
  });

  it('rejects a pending review and reloads the queue', () => {
    setup(['REVIEWS_READ', 'REVIEWS_WRITE']);
    reviewsServiceSpy.rejectReview.and.returnValue(of(review));
    component.reject('REVIEW-1');

    expect(reviewsServiceSpy.rejectReview).toHaveBeenCalledWith('REVIEW-1');
    expect(reviewsServiceSpy.listPendingReviews).toHaveBeenCalledTimes(2);
  });

  it('sets an error message when rejecting fails', () => {
    setup(['REVIEWS_READ', 'REVIEWS_WRITE']);
    reviewsServiceSpy.rejectReview.and.returnValue(
      throwError(() => new Error('failed'))
    );
    component.reject('REVIEW-1');

    expect(component.moderationErrorMessage()).toBe('Failed to reject review.');
  });

  it('keeps browse results atomic and ignores a stale earlier request', () => {
    setup();
    const firstReviews = new Subject<ReviewModel[]>();
    const firstSummary = new Subject<ReviewSummaryModel>();
    const latestReviews = new Subject<ReviewModel[]>();
    const latestSummary = new Subject<ReviewSummaryModel>();
    reviewsServiceSpy.listApprovedReviews.and.returnValues(
      firstReviews,
      latestReviews
    );
    reviewsServiceSpy.getSummary.and.returnValues(firstSummary, latestSummary);

    component.browseForm.setValue({ sku: 'SKU-1' });
    component.browse();
    component.browseForm.setValue({ sku: 'SKU-2' });
    component.browse();

    firstReviews.next([review]);
    firstReviews.complete();
    firstSummary.next({ sku: 'SKU-1', averageRating: 5, reviewCount: 1 });
    firstSummary.complete();

    expect(component.reviews()).toEqual([]);
    expect(component.summary()).toBeNull();

    const latest = { ...review, reviewId: 'REVIEW-2', sku: 'SKU-2' };
    latestReviews.next([latest]);
    latestReviews.complete();
    latestSummary.next({ sku: 'SKU-2', averageRating: 4, reviewCount: 3 });
    latestSummary.complete();

    expect(component.reviews()).toEqual([latest]);
    expect(component.summary()?.sku).toBe('SKU-2');
    expect(component.loadingReviews()).toBeFalse();
  });

  it('blocks duplicate moderation mutations while one review is being updated', () => {
    setup(['REVIEWS_READ', 'REVIEWS_WRITE']);
    const pending = new Subject<ReviewModel>();
    reviewsServiceSpy.approveReview.and.returnValue(pending);

    component.approve('REVIEW-1');
    component.approve('REVIEW-1');

    expect(reviewsServiceSpy.approveReview).toHaveBeenCalledTimes(1);
    expect(component.moderatingReviewId()).toBe('REVIEW-1');

    pending.next(review);
    pending.complete();

    expect(component.moderatingReviewId()).toBeNull();
  });

  it('blocks duplicate review submissions while the first request is in flight', () => {
    setup();
    const pending = new Subject<ReviewModel>();
    reviewsServiceSpy.submitReview.and.returnValue(pending);
    component.submitForm.setValue({
      sku: 'SKU-1',
      authorName: 'Jane Smith',
      rating: 5,
      comment: 'Great!',
    });

    component.submit();
    component.submit();

    expect(reviewsServiceSpy.submitReview).toHaveBeenCalledTimes(1);
    expect(component.submittingReview()).toBeTrue();

    pending.next(review);
    pending.complete();

    expect(component.submittingReview()).toBeFalse();
  });

  it('blocks reject while another moderation mutation is in flight', () => {
    setup(['REVIEWS_READ', 'REVIEWS_WRITE']);
    const pending = new Subject<ReviewModel>();
    reviewsServiceSpy.approveReview.and.returnValue(pending);

    component.approve('REVIEW-1');
    component.reject('REVIEW-2');

    expect(reviewsServiceSpy.rejectReview).not.toHaveBeenCalled();

    pending.next(review);
    pending.complete();
  });

  it('clears stale browse data while the latest request is pending', () => {
    setup();
    reviewsServiceSpy.listApprovedReviews.and.returnValue(of([review]));
    reviewsServiceSpy.getSummary.and.returnValue(
      of({ sku: 'SKU-1', averageRating: 5, reviewCount: 1 })
    );
    component.browseForm.setValue({ sku: 'SKU-1' });
    component.browse();
    expect(component.reviews()).toEqual([review]);

    const pendingReviews = new Subject<ReviewModel[]>();
    const pendingSummary = new Subject<ReviewSummaryModel>();
    reviewsServiceSpy.listApprovedReviews.and.returnValue(pendingReviews);
    reviewsServiceSpy.getSummary.and.returnValue(pendingSummary);
    component.browseForm.setValue({ sku: 'SKU-2' });
    component.browse();

    expect(component.loadingReviews()).toBeTrue();
    expect(component.reviews()).toEqual([]);
    expect(component.summary()).toBeNull();

    pendingReviews.next([]);
    pendingReviews.complete();
    pendingSummary.next({ sku: 'SKU-2', averageRating: 0, reviewCount: 0 });
    pendingSummary.complete();

    expect(component.loadingReviews()).toBeFalse();
  });
});
