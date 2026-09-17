import { HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ProblemDetailsAdapter {
  toMessage(error: unknown, fallback: string): string {
    if (!(error instanceof HttpErrorResponse)) return fallback;

    const body: unknown = error.error;
    if (typeof body === 'string') {
      return body.trim() || fallback;
    }

    const problem = this.asProblem(body);
    if (!problem) return fallback;

    const title = this.text(problem['title']);
    const detail = this.text(problem['detail']);
    const errorId = this.text(problem['errorId']);
    const traceId = this.text(problem['traceId']);

    if (!title && !detail) return fallback;

    const message = title && detail ? `${title}: ${detail}` : title || detail;
    const correlationIds = [
      errorId ? `error id: ${errorId}` : '',
      traceId ? `trace id: ${traceId}` : '',
    ].filter(Boolean);
    return correlationIds.length
      ? `${message} (${correlationIds.join(', ')})`
      : message;
  }

  type(error: unknown): string | null {
    if (!(error instanceof HttpErrorResponse)) return null;

    const problem = this.asProblem(error.error);
    if (!problem) return null;

    return this.text(problem['type']) || null;
  }

  private asProblem(body: unknown): Record<string, unknown> | null {
    if (body === null || typeof body !== 'object' || Array.isArray(body)) {
      return null;
    }
    return body as Record<string, unknown>;
  }

  private text(value: unknown): string {
    return typeof value === 'string' ? value.trim() : '';
  }
}
