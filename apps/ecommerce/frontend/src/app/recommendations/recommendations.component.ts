import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';

import { RecommendationModel } from '@app/recommendations/recommendation.model';
import { RecommendationsService } from '@app/recommendations/recommendations.service';

@Component({
  selector: 'app-recommendations',
  templateUrl: './recommendations.component.html',
  styleUrls: ['./recommendations.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
})
export class RecommendationsComponent {
  private readonly recommendationsService = inject(RecommendationsService);

  readonly loading = signal(false);
  readonly assistantAvailable = signal(true);
  readonly recommendations = signal<RecommendationModel[]>([]);
  readonly errorMessage = signal<string | null>(null);

  readonly emailControl = new FormControl('', {
    nonNullable: true,
    validators: [
      Validators.required,
      Validators.email,
      Validators.maxLength(255),
    ],
  });

  loadRecommendations(): void {
    const email = this.emailControl.value.trim();
    if (!email || this.emailControl.invalid || this.loading()) return;

    this.loading.set(true);
    this.errorMessage.set(null);
    this.recommendationsService.getRecommendations(email).subscribe({
      next: (response) => {
        this.loading.set(false);
        this.assistantAvailable.set(response.assistantAvailable);
        this.recommendations.set(response.recommendations);
      },
      error: () => {
        this.loading.set(false);
        this.assistantAvailable.set(false);
        this.errorMessage.set(
          'Failed to load recommendations. Please try again.'
        );
      },
    });
  }
}
