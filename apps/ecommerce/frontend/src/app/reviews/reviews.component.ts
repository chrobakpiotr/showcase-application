import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  Subject,
  catchError,
  finalize,
  forkJoin,
  of,
  switchMap,
  tap,
} from 'rxjs';
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
  private readonly destroyRef = inject(DestroyRef);
  private readonly browseRequests = new Subject<string>();

  readonly reviews = signal<ReviewModel[]>([]);
  readonly summary = signal<ReviewSummaryModel | null>(null);
  readonly pendingReviews = signal<ReviewModel[]>([]);
  readonly loadingReviews = signal(false);
  readonly submittingReview = signal(false);
  readonly moderationLoading = signal(false);
  readonly moderatingReviewId = signal<string | null>(null);
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
    this.browseRequests
      .pipe(
        tap(() => {
          this.loadingReviews.set(true);
          this.errorMessage.set(null);
          this.reviews.set([]);
          this.summary.set(null);
        }),
        switchMap((sku) =>
          forkJoin({
            reviews: this.reviewsService
              .listApprovedReviews(sku)
              .pipe(catchError(() => of(null))),
            summary: this.reviewsService
              .getSummary(sku)
              .pipe(catchError(() => of(null))),
          })
        ),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(({ reviews, summary }) => {
        this.loadingReviews.set(false);
        if (reviews === null) {
          this.errorMessage.set('Failed to load reviews.');
          return;
        }
        if (summary === null) {
          this.errorMessage.set('Failed to load review summary.');
          return;
        }
        this.reviews.set(reviews);
        this.summary.set(summary);
      });

    if (this.canModerate) {
      this.loadPendingReviews();
    }
  }

  browse(): void {
    if (this.browseForm.invalid) return;
    this.browseRequests.next(this.browseForm.getRawValue().sku);
  }

  submit(): void {
    if (this.submitForm.invalid || this.submittingReview()) return;
    const { sku, authorName, rating, comment } = this.submitForm.getRawValue();
    this.errorMessage.set(null);
    this.submitted.set(false);
    this.submittingReview.set(true);
    this.reviewsService
      .submitReview({ sku, authorName, rating: rating!, comment })
      .pipe(
        finalize(() => this.submittingReview.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
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
    if (this.moderatingReviewId()) return;
    this.moderationErrorMessage.set(null);
    this.moderatingReviewId.set(reviewId);
    this.reviewsService
      .approveReview(reviewId)
      .pipe(
        finalize(() => this.moderatingReviewId.set(null)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => this.loadPendingReviews(),
        error: () =>
          this.moderationErrorMessage.set('Failed to approve review.'),
      });
  }

  reject(reviewId: string): void {
    if (this.moderatingReviewId()) return;
    this.moderationErrorMessage.set(null);
    this.moderatingReviewId.set(reviewId);
    this.reviewsService
      .rejectReview(reviewId)
      .pipe(
        finalize(() => this.moderatingReviewId.set(null)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => this.loadPendingReviews(),
        error: () =>
          this.moderationErrorMessage.set('Failed to reject review.'),
      });
  }

  private loadPendingReviews(): void {
    this.moderationLoading.set(true);
    this.reviewsService
      .listPendingReviews()
      .pipe(
        finalize(() => this.moderationLoading.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (reviews) => this.pendingReviews.set(reviews),
        error: () =>
          this.moderationErrorMessage.set('Failed to load pending reviews.'),
      });
  }
}
