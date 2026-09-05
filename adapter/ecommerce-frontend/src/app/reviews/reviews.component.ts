import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';

import { AuthService } from '@app/auth/auth.service';
import { ReviewModel, ReviewSummaryModel } from '@app/reviews/review.model';
import { ReviewsService } from '@app/reviews/reviews.service';

@Component({
  selector: 'app-reviews',
  templateUrl: './reviews.component.html',
  styleUrls: ['./reviews.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DatePipe],
})
export class ReviewsComponent implements OnInit {
  private readonly reviewsService = inject(ReviewsService);
  private readonly authService = inject(AuthService);

  readonly reviews = signal<ReviewModel[]>([]);
  readonly summary = signal<ReviewSummaryModel | null>(null);
  readonly pendingReviews = signal<ReviewModel[]>([]);
  readonly errorMessage = signal<string | null>(null);
  readonly moderationErrorMessage = signal<string | null>(null);
  readonly submitted = signal(false);

  readonly browseForm = new FormGroup({
    sku: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  readonly submitForm = new FormGroup({
    sku: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    authorName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    rating: new FormControl<number | null>(5, {
      validators: [Validators.required, Validators.min(1), Validators.max(5)],
    }),
    comment: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  get canModerate(): boolean {
    return this.authService.roles().includes('REVIEWS_READ');
  }

  get canWrite(): boolean {
    return this.authService.roles().includes('REVIEWS_WRITE');
  }

  ngOnInit(): void {
    if (this.canModerate) {
      this.loadPendingReviews();
    }
  }

  browse(): void {
    if (this.browseForm.invalid) return;
    const { sku } = this.browseForm.getRawValue();
    this.errorMessage.set(null);
    this.reviewsService.listApprovedReviews(sku).subscribe({
      next: (reviews) => this.reviews.set(reviews),
      error: () => this.errorMessage.set('Failed to load reviews.'),
    });
    this.reviewsService.getSummary(sku).subscribe({
      next: (summary) => this.summary.set(summary),
      error: () => this.errorMessage.set('Failed to load review summary.'),
    });
  }

  submit(): void {
    if (this.submitForm.invalid) return;
    const { sku, authorName, rating, comment } = this.submitForm.getRawValue();
    this.errorMessage.set(null);
    this.submitted.set(false);
    this.reviewsService
      .submitReview({ sku, authorName, rating: rating!, comment })
      .subscribe({
        next: () => {
          this.submitted.set(true);
          this.submitForm.reset({
            sku: '',
            authorName: '',
            rating: 5,
            comment: '',
          });
        },
        error: () => this.errorMessage.set('Failed to submit review.'),
      });
  }

  approve(reviewId: string): void {
    this.moderationErrorMessage.set(null);
    this.reviewsService.approveReview(reviewId).subscribe({
      next: () => this.loadPendingReviews(),
      error: () => this.moderationErrorMessage.set('Failed to approve review.'),
    });
  }

  reject(reviewId: string): void {
    this.moderationErrorMessage.set(null);
    this.reviewsService.rejectReview(reviewId).subscribe({
      next: () => this.loadPendingReviews(),
      error: () => this.moderationErrorMessage.set('Failed to reject review.'),
    });
  }

  private loadPendingReviews(): void {
    this.reviewsService.listPendingReviews().subscribe({
      next: (reviews) => this.pendingReviews.set(reviews),
      error: () =>
        this.moderationErrorMessage.set('Failed to load pending reviews.'),
    });
  }
}
