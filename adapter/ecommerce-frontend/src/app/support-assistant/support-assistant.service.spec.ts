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

import { SupportAssistantService } from '@app/support-assistant/support-assistant.service';
import { SupportAnswerModel } from '@app/support-assistant/support-answer.model';
import { environment } from '@environments/environment';

describe('SupportAssistantService', () => {
  let supportAssistantService: SupportAssistantService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [],
      providers: [
        SupportAssistantService,
        provideHttpClient(withXhr(), withInterceptorsFromDi()),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    supportAssistantService = TestBed.inject(SupportAssistantService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should be created', () => {
    expect(supportAssistantService).toBeTruthy();
  });

  it('should post the question with a generated conversationId', () => {
    const answer: SupportAnswerModel = {
      answer: 'Yes, you can cancel your order.',
      assistantAvailable: true,
    };

    supportAssistantService
      .askQuestion('Can I cancel my order?')
      .subscribe((data) => expect(data).toBe(answer));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/support-assistant/questions`
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body.question).toBe('Can I cancel my order?');
    expect(req.request.body.conversationId).toBeTruthy();

    req.flush(answer);
  });

  it('should reuse the same conversationId across multiple questions', () => {
    supportAssistantService.askQuestion('First question').subscribe();
    const firstRequest = httpTestingController.expectOne(
      `${environment.apiPrefix}/support-assistant/questions`
    );
    firstRequest.flush({ answer: 'ok', assistantAvailable: true });

    supportAssistantService.askQuestion('Second question').subscribe();
    const secondRequest = httpTestingController.expectOne(
      `${environment.apiPrefix}/support-assistant/questions`
    );
    secondRequest.flush({ answer: 'ok', assistantAvailable: true });

    expect(firstRequest.request.body.conversationId).toBe(
      secondRequest.request.body.conversationId
    );
  });
});
