import { TestBed } from '@angular/core/testing';

import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import {
  provideHttpClient,
  withInterceptorsFromDi,
  withXhr,
} from '@angular/common/http';

import { ReviewModel, ReviewSummaryModel } from '@app/reviews/review.model';
import { ReviewsService } from '@app/reviews/reviews.service';
import { environment } from '@environments/environment';

describe('ReviewsService', () => {
  let reviewsService: ReviewsService;
  let httpTestingController: HttpTestingController;

  const review: ReviewModel = {
    reviewId: 'REVIEW-1',
    sku: 'SKU-1',
    authorName: 'Jane Smith',
    rating: 5,
    comment: 'Great!',
    status: 'PENDING',
    created: '2024-03-15T10:30:00.000Z',
  };

  const summary: ReviewSummaryModel = {
    sku: 'SKU-1',
    averageRating: 4.5,
    reviewCount: 12,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ReviewsService,
        provideHttpClient(withXhr(), withInterceptorsFromDi()),
        provideHttpClientTesting(),
      ],
    });
    reviewsService = TestBed.inject(ReviewsService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should be created', () => {
    expect(reviewsService).toBeTruthy();
  });

  it('submits a review', () => {
    reviewsService
      .submitReview({
        sku: 'SKU-1',
        authorName: 'Jane Smith',
        rating: 5,
        comment: 'Great!',
      })
      .subscribe((data) => expect(data).toBe(review));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/reviews`
    );
    expect(req.request.method).toBe('POST');
    req.flush(review);
  });

  it('lists approved reviews', () => {
    reviewsService
      .listApprovedReviews('SKU-1')
      .subscribe((data) => expect(data).toEqual([review]));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/reviews/product/SKU-1`
    );
    expect(req.request.method).toBe('GET');
    req.flush([review]);
  });

  it('gets a summary', () => {
    reviewsService
      .getSummary('SKU-1')
      .subscribe((data) => expect(data).toBe(summary));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/reviews/product/SKU-1/summary`
    );
    expect(req.request.method).toBe('GET');
    req.flush(summary);
  });

  it('lists pending reviews', () => {
    reviewsService
      .listPendingReviews()
      .subscribe((data) => expect(data).toEqual([review]));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/reviews/moderation/pending`
    );
    expect(req.request.method).toBe('GET');
    req.flush([review]);
  });

  it('approves a review', () => {
    reviewsService
      .approveReview('REVIEW-1')
      .subscribe((data) => expect(data).toBe(review));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/reviews/moderation/REVIEW-1/approve`
    );
    expect(req.request.method).toBe('POST');
    req.flush(review);
  });

  it('rejects a review', () => {
    reviewsService
      .rejectReview('REVIEW-1')
      .subscribe((data) => expect(data).toBe(review));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/reviews/moderation/REVIEW-1/reject`
    );
    expect(req.request.method).toBe('POST');
    req.flush(review);
  });
});
