import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { DatePipe, KeyValuePipe } from '@angular/common';

import { AnalyticsAssistantService } from '@app/analytics-assistant/analytics-assistant.service';
import { OpsDigestModel } from '@app/analytics-assistant/ops-digest.model';

interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
  assistantAvailable?: boolean;
}

// Mirrors the backend's ValidationConstants.ANALYTICS_QUESTION_MAX.
const QUESTION_MAX_LENGTH = 2000;

@Component({
  selector: 'app-analytics-assistant',
  templateUrl: './analytics-assistant.component.html',
  styleUrls: ['./analytics-assistant.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DatePipe, KeyValuePipe],
})
export class AnalyticsAssistantComponent implements OnInit {
  private readonly analyticsAssistantService = inject(
    AnalyticsAssistantService
  );

  readonly sending = signal(false);
  readonly messages = signal<ChatMessage[]>([]);
  // Undefined while loading, null once loaded if no digest has been generated yet (rare - one is generated
  // eagerly on application start-up).
  readonly opsDigest = signal<OpsDigestModel | null | undefined>(undefined);

  readonly questionControl = new FormControl('', {
    nonNullable: true,
    validators: [
      Validators.required,
      Validators.maxLength(QUESTION_MAX_LENGTH),
    ],
  });

  ngOnInit(): void {
    this.analyticsAssistantService.getLatestDigest().subscribe({
      next: (digest) => this.opsDigest.set(digest),
      error: () => this.opsDigest.set(null),
    });
  }

  askQuestion(): void {
    const question = this.questionControl.value.trim();
    if (!question || this.questionControl.invalid || this.sending()) return;

    this.messages.update((current) => [
      ...current,
      { role: 'user', text: question },
    ]);
    this.questionControl.setValue('');
    this.sending.set(true);

    this.analyticsAssistantService.askQuestion(question).subscribe({
      next: (response) => {
        this.sending.set(false);
        this.messages.update((current) => [
          ...current,
          {
            role: 'assistant',
            text: response.answer,
            assistantAvailable: response.assistantAvailable,
          },
        ]);
      },
      error: () => {
        this.sending.set(false);
        this.messages.update((current) => [
          ...current,
          {
            role: 'assistant',
            text: 'Sorry, something went wrong. Please try again.',
            assistantAvailable: false,
          },
        ]);
      },
    });
  }
}
