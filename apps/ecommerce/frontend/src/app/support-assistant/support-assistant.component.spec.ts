import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import { SupportAssistantComponent } from '@app/support-assistant/support-assistant.component';
import { SupportAssistantService } from '@app/support-assistant/support-assistant.service';

describe('SupportAssistantComponent', () => {
  let fixture: ComponentFixture<SupportAssistantComponent>;
  let component: SupportAssistantComponent;
  let askQuestionSpy: jasmine.Spy;

  function setup(): void {
    askQuestionSpy = jasmine.createSpy('askQuestion');
    TestBed.configureTestingModule({
      imports: [SupportAssistantComponent],
      providers: [
        {
          provide: SupportAssistantService,
          useValue: { askQuestion: askQuestionSpy },
        },
      ],
    });
    fixture = TestBed.createComponent(SupportAssistantComponent);
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

  it('should be closed by default', () => {
    setup();
    expect(component.open()).toBeFalse();
  });

  it('should toggle open state', () => {
    setup();
    component.toggle();
    expect(component.open()).toBeTrue();
    component.toggle();
    expect(component.open()).toBeFalse();
  });

  it('should not ask a question when input is blank', () => {
    setup();
    component.questionControl.setValue('   ');
    component.askQuestion();
    expect(askQuestionSpy).not.toHaveBeenCalled();
  });

  it('should not ask a question while already sending', () => {
    setup();
    component.questionControl.setValue('Can I cancel my order?');
    component.sending.set(true);
    component.askQuestion();
    expect(askQuestionSpy).not.toHaveBeenCalled();
  });

  it('should append the user message immediately and clear the input', fakeAsync(() => {
    setup();
    askQuestionSpy.and.returnValue(
      of({ answer: 'Yes, you can.', assistantAvailable: true })
    );
    component.questionControl.setValue('Can I cancel my order?');
    component.askQuestion();
    expect(component.messages()[0]).toEqual({
      role: 'user',
      text: 'Can I cancel my order?',
    });
    expect(component.questionControl.value).toBe('');
    tick();
  }));

  it('should append the assistant answer on success', fakeAsync(() => {
    setup();
    askQuestionSpy.and.returnValue(
      of({ answer: 'Yes, you can.', assistantAvailable: true })
    );
    component.questionControl.setValue('Can I cancel my order?');
    component.askQuestion();
    tick();
    fixture.detectChanges();
    const assistantMessage = component
      .messages()
      .find((message) => message.role === 'assistant');
    expect(assistantMessage?.text).toBe('Yes, you can.');
    expect(assistantMessage?.assistantAvailable).toBeTrue();
    expect(component.sending()).toBeFalse();
  }));

  it('should show an unavailable hint when the assistant answer is a fallback', fakeAsync(() => {
    setup();
    component.toggle();
    askQuestionSpy.and.returnValue(
      of({ answer: 'Assistant unavailable.', assistantAvailable: false })
    );
    component.questionControl.setValue('Can I cancel my order?');
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
    component.questionControl.setValue('Can I cancel my order?');
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
