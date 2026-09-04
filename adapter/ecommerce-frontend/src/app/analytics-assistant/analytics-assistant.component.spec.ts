import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import { AnalyticsAssistantComponent } from '@app/analytics-assistant/analytics-assistant.component';
import { AnalyticsAssistantService } from '@app/analytics-assistant/analytics-assistant.service';

describe('AnalyticsAssistantComponent', () => {
  let fixture: ComponentFixture<AnalyticsAssistantComponent>;
  let component: AnalyticsAssistantComponent;
  let askQuestionSpy: jasmine.Spy;

  function setup(): void {
    askQuestionSpy = jasmine.createSpy('askQuestion');
    TestBed.configureTestingModule({
      imports: [AnalyticsAssistantComponent],
      providers: [
        {
          provide: AnalyticsAssistantService,
          useValue: { askQuestion: askQuestionSpy },
        },
      ],
    });
    fixture = TestBed.createComponent(AnalyticsAssistantComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should create the component', () => {
    setup();
    expect(component).toBeTruthy();
  });

  it('should not ask a question when input is blank', () => {
    setup();
    component.questionControl.setValue('   ');
    component.askQuestion();
    expect(askQuestionSpy).not.toHaveBeenCalled();
  });

  it('should not ask a question while already sending', () => {
    setup();
    component.questionControl.setValue('How many orders were placed today?');
    component.sending.set(true);
    component.askQuestion();
    expect(askQuestionSpy).not.toHaveBeenCalled();
  });

  it('should append the user message immediately and clear the input', fakeAsync(() => {
    setup();
    askQuestionSpy.and.returnValue(
      of({ answer: '3 orders were placed today.', assistantAvailable: true })
    );
    component.questionControl.setValue('How many orders were placed today?');
    component.askQuestion();
    expect(component.messages()[0]).toEqual({
      role: 'user',
      text: 'How many orders were placed today?',
    });
    expect(component.questionControl.value).toBe('');
    tick();
  }));

  it('should append the assistant answer on success', fakeAsync(() => {
    setup();
    askQuestionSpy.and.returnValue(
      of({ answer: '3 orders were placed today.', assistantAvailable: true })
    );
    component.questionControl.setValue('How many orders were placed today?');
    component.askQuestion();
    tick();
    fixture.detectChanges();
    const assistantMessage = component
      .messages()
      .find((message) => message.role === 'assistant');
    expect(assistantMessage?.text).toBe('3 orders were placed today.');
    expect(assistantMessage?.assistantAvailable).toBeTrue();
    expect(component.sending()).toBeFalse();
  }));

  it('should show an unavailable hint when the assistant answer is a fallback', fakeAsync(() => {
    setup();
    askQuestionSpy.and.returnValue(
      of({ answer: 'Assistant unavailable.', assistantAvailable: false })
    );
    component.questionControl.setValue('How many orders were placed today?');
    component.askQuestion();
    tick();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.unavailable-hint')).toBeTruthy();
  }));

  it('should append a fallback assistant message on HTTP error', fakeAsync(() => {
    setup();
    askQuestionSpy.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500 }))
    );
    component.questionControl.setValue('How many orders were placed today?');
    component.askQuestion();
    tick();
    fixture.detectChanges();
    const assistantMessage = component
      .messages()
      .find((message) => message.role === 'assistant');
    expect(assistantMessage?.text).toBe(
      'Sorry, something went wrong. Please try again.'
    );
    expect(component.sending()).toBeFalse();
  }));
});
