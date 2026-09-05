import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';

import { AuthService } from '@app/auth/auth.service';
import { ReviewModel } from '@app/reviews/review.model';
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
});
