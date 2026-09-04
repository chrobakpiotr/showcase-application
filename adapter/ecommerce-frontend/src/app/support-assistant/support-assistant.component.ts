import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';

import { SupportAssistantService } from '@app/support-assistant/support-assistant.service';

interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
  assistantAvailable?: boolean;
}

// Mirrors the backend's ValidationConstants.SUPPORT_QUESTION_MAX.
const QUESTION_MAX_LENGTH = 2000;

@Component({
  selector: 'app-support-assistant',
  templateUrl: './support-assistant.component.html',
  styleUrls: ['./support-assistant.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
})
export class SupportAssistantComponent {
  private readonly supportAssistantService = inject(SupportAssistantService);

  readonly open = signal(false);
  readonly sending = signal(false);
  readonly messages = signal<ChatMessage[]>([]);

  readonly questionControl = new FormControl('', {
    nonNullable: true,
    validators: [
      Validators.required,
      Validators.maxLength(QUESTION_MAX_LENGTH),
    ],
  });

  toggle(): void {
    this.open.update((isOpen) => !isOpen);
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

    this.supportAssistantService.askQuestion(question).subscribe({
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
