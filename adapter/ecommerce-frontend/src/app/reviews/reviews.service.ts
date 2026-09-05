import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import {
  ReviewModel,
  ReviewSummaryModel,
  SubmitReviewRequestModel,
} from '@app/reviews/review.model';

@Injectable({ providedIn: 'root' })
export class ReviewsService {
  private readonly httpClient = inject(HttpClient);

  submitReview(request: SubmitReviewRequestModel): Observable<ReviewModel> {
    return this.httpClient.post<ReviewModel>(
      `${environment.apiPrefix}/reviews`,
      request
    );
  }

  listApprovedReviews(sku: string): Observable<ReviewModel[]> {
    return this.httpClient.get<ReviewModel[]>(
      `${environment.apiPrefix}/reviews/product/${sku}`
    );
  }

  getSummary(sku: string): Observable<ReviewSummaryModel> {
    return this.httpClient.get<ReviewSummaryModel>(
      `${environment.apiPrefix}/reviews/product/${sku}/summary`
    );
  }

  listPendingReviews(): Observable<ReviewModel[]> {
    return this.httpClient.get<ReviewModel[]>(
      `${environment.apiPrefix}/reviews/moderation/pending`
    );
  }

  approveReview(reviewId: string): Observable<ReviewModel> {
    return this.httpClient.post<ReviewModel>(
      `${environment.apiPrefix}/reviews/moderation/${reviewId}/approve`,
      {}
    );
  }

  rejectReview(reviewId: string): Observable<ReviewModel> {
    return this.httpClient.post<ReviewModel>(
      `${environment.apiPrefix}/reviews/moderation/${reviewId}/reject`,
      {}
    );
  }
}
