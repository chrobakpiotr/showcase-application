import { TestBed } from '@angular/core/testing';

import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import {
  provideHttpClient,
  withInterceptorsFromDi,
  withXhr,
} from '@angular/common/http';

import { AnalyticsAssistantService } from '@app/analytics-assistant/analytics-assistant.service';
import { AnalyticsAnswerModel } from '@app/analytics-assistant/analytics-answer.model';
import { environment } from '@environments/environment';

describe('AnalyticsAssistantService', () => {
  let analyticsAssistantService: AnalyticsAssistantService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [],
      providers: [
        AnalyticsAssistantService,
        provideHttpClient(withXhr(), withInterceptorsFromDi()),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    analyticsAssistantService = TestBed.inject(AnalyticsAssistantService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should be created', () => {
    expect(analyticsAssistantService).toBeTruthy();
  });

  it('should post the question with a generated conversationId', () => {
    const answer: AnalyticsAnswerModel = {
      answer: '3 orders were placed today.',
      assistantAvailable: true,
    };

    analyticsAssistantService
      .askQuestion('How many orders were placed today?')
      .subscribe((data) => expect(data).toBe(answer));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/order/analytics/ask`
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body.question).toBe(
      'How many orders were placed today?'
    );
    expect(req.request.body.conversationId).toBeTruthy();

    req.flush(answer);
  });

  it('should reuse the same conversationId across multiple questions', () => {
    analyticsAssistantService.askQuestion('First question').subscribe();
    const firstRequest = httpTestingController.expectOne(
      `${environment.apiPrefix}/order/analytics/ask`
    );
    firstRequest.flush({ answer: 'ok', assistantAvailable: true });

    analyticsAssistantService.askQuestion('Second question').subscribe();
    const secondRequest = httpTestingController.expectOne(
      `${environment.apiPrefix}/order/analytics/ask`
    );
    secondRequest.flush({ answer: 'ok', assistantAvailable: true });

    expect(firstRequest.request.body.conversationId).toBe(
      secondRequest.request.body.conversationId
    );
  });
});
