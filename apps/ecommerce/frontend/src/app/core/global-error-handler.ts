import { ErrorHandler, Injectable } from '@angular/core';

/**
 * Catches errors that escape component/template code and every RxJS subscription (e.g. a bug in change detection, a broken
 * template expression) and logs them with clear, greppable structure instead of Angular's default bare `console.error`, so a
 * genuinely unexpected failure is easy to spot and diagnose from browser DevTools alone.
 *
 * Deliberately narrow in scope: expected, recoverable failures (e.g. a failed HTTP request) are already surfaced through
 * component-level error state (see `OrderComponent`/`LoginComponent`) and must never reach here. This handler is a last-resort
 * safety net for bugs, not a substitute for proper per-feature error handling.
 */
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  handleError(error: unknown): void {
    const message = error instanceof Error ? error.message : String(error);
    const stack = error instanceof Error ? error.stack : undefined;
    console.error(
      '[GlobalErrorHandler] Unhandled application error:',
      message,
      stack ?? error
    );
  }
}
