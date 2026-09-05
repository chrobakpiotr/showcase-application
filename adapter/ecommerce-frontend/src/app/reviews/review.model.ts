// Mirrors the backend's ReviewResource/ReviewSummaryResource/SubmitReviewResource
// (adapter/web/.../review/resource/*.java).
export interface ReviewModel {
  reviewId: string;
  sku: string;
  authorName: string;
  rating: number;
  comment: string;
  status: string;
  created: string;
}

export interface ReviewSummaryModel {
  sku: string;
  averageRating: number;
  reviewCount: number;
}

export interface SubmitReviewRequestModel {
  sku: string;
  authorName: string;
  rating: number;
  comment: string;
}
