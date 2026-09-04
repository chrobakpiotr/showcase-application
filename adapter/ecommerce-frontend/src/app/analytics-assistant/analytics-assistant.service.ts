import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import { AnalyticsAnswerModel } from '@app/analytics-assistant/analytics-answer.model';
import { AnalyticsQuestionModel } from '@app/analytics-assistant/analytics-question.model';

@Injectable({ providedIn: 'root' })
export class AnalyticsAssistantService {
  private readonly httpClient = inject(HttpClient);

  private readonly httpOptions = {
    headers: new HttpHeaders({
      'Content-Type': 'application/json',
    }),
  };

  // Generated once per browser tab/session (not persisted across reloads - fine for a demo page) and reused
  // for every question, the same "client generates an opaque correlation token" pattern already used by
  // SupportAssistantService/order placement's Idempotency-Key. Lets the backend's chat-memory advisor track
  // multi-turn conversation context.
  private readonly conversationId = crypto.randomUUID();

  askQuestion(question: string): Observable<AnalyticsAnswerModel> {
    const body: AnalyticsQuestionModel = {
      question,
      conversationId: this.conversationId,
    };
    return this.httpClient.post<AnalyticsAnswerModel>(
      `${environment.apiPrefix}/order/analytics/ask`,
      body,
      this.httpOptions
    );
  }
}
