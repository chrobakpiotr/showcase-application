import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';

import { AnalyticsAssistantService } from '@app/analytics-assistant/analytics-assistant.service';

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
  imports: [ReactiveFormsModule],
})
export class AnalyticsAssistantComponent {
  private readonly analyticsAssistantService = inject(
    AnalyticsAssistantService
  );

  readonly sending = signal(false);
  readonly messages = signal<ChatMessage[]>([]);

  readonly questionControl = new FormControl('', {
    nonNullable: true,
    validators: [
      Validators.required,
      Validators.maxLength(QUESTION_MAX_LENGTH),
    ],
  });

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
