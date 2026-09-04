import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import { SupportAnswerModel } from '@app/support-assistant/support-answer.model';
import { SupportQuestionModel } from '@app/support-assistant/support-question.model';

@Injectable({ providedIn: 'root' })
export class SupportAssistantService {
  private readonly httpClient = inject(HttpClient);

  private readonly httpOptions = {
    headers: new HttpHeaders({
      'Content-Type': 'application/json',
    }),
  };

  // Generated once per browser tab/session (not persisted across reloads - fine for a demo widget) and reused
  // for every question, the same "client generates an opaque correlation token" pattern already used for order
  // placement's Idempotency-Key. Lets the backend's chat-memory advisor track multi-turn conversation context.
  private readonly conversationId = crypto.randomUUID();

  askQuestion(question: string): Observable<SupportAnswerModel> {
    const body: SupportQuestionModel = {
      question,
      conversationId: this.conversationId,
    };
    return this.httpClient.post<SupportAnswerModel>(
      `${environment.apiPrefix}/support-assistant/questions`,
      body,
      this.httpOptions
    );
  }
}
