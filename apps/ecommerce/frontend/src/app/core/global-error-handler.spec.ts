import { GlobalErrorHandler } from '@app/core/global-error-handler';

describe('GlobalErrorHandler', () => {
  let handler: GlobalErrorHandler;
  let consoleErrorSpy: jasmine.Spy;

  beforeEach(() => {
    handler = new GlobalErrorHandler();
    consoleErrorSpy = spyOn(console, 'error');
  });

  it('should create the handler', () => {
    expect(handler).toBeTruthy();
  });

  it('logs the message and stack for an Error instance', () => {
    const error = new Error('boom');

    handler.handleError(error);

    expect(consoleErrorSpy).toHaveBeenCalledWith(
      '[GlobalErrorHandler] Unhandled application error:',
      'boom',
      error.stack
    );
  });

  it('logs a string representation for a non-Error value', () => {
    handler.handleError('not an Error object');

    expect(consoleErrorSpy).toHaveBeenCalledWith(
      '[GlobalErrorHandler] Unhandled application error:',
      'not an Error object',
      'not an Error object'
    );
  });
});
