import { HttpErrorResponse } from '@angular/common/http';

import { ProblemDetailsAdapter } from '@app/http/problem-details.adapter';

describe('ProblemDetailsAdapter', () => {
  const adapter = new ProblemDetailsAdapter();
  const fallback = 'Fallback message';

  it('uses the fallback for non-HTTP errors', () => {
    expect(adapter.toMessage(new Error('boom'), fallback)).toBe(fallback);
    expect(adapter.type(new Error('boom'))).toBeNull();
  });

  it('uses a trimmed plain-text HTTP body and falls back for blank text', () => {
    expect(
      adapter.toMessage(
        new HttpErrorResponse({ status: 400, error: '  Invalid request  ' }),
        fallback
      )
    ).toBe('Invalid request');
    expect(
      adapter.toMessage(
        new HttpErrorResponse({ status: 400, error: '   ' }),
        fallback
      )
    ).toBe(fallback);
  });

  it('formats RFC 9457 title, detail and correlation id', () => {
    expect(
      adapter.toMessage(
        new HttpErrorResponse({
          status: 409,
          error: {
            title: '  Order conflict ',
            detail: ' Duplicate order ',
            errorId: ' err-123 ',
          },
        }),
        fallback
      )
    ).toBe('Order conflict: Duplicate order (error id: err-123)');
  });

  it('supports title-only and detail-only problem responses', () => {
    expect(
      adapter.toMessage(
        new HttpErrorResponse({
          status: 409,
          error: { title: 'Order conflict', errorId: ' ' },
        }),
        fallback
      )
    ).toBe('Order conflict');
    expect(
      adapter.toMessage(
        new HttpErrorResponse({
          status: 422,
          error: { detail: 'Quantity must be positive' },
        }),
        fallback
      )
    ).toBe('Quantity must be positive');
  });

  it('falls back for non-problem object shapes and empty problem details', () => {
    for (const body of [
      null,
      42,
      [],
      {},
      { title: 123, detail: false, errorId: {} },
    ]) {
      expect(
        adapter.toMessage(
          new HttpErrorResponse({ status: 500, error: body }),
          fallback
        )
      ).toBe(fallback);
    }
  });

  it('extracts a trimmed RFC 9457 type only from object problem bodies', () => {
    expect(
      adapter.type(
        new HttpErrorResponse({
          status: 409,
          error: { type: '  urn:problem-type:insufficient-stock  ' },
        })
      )
    ).toBe('urn:problem-type:insufficient-stock');

    for (const body of [
      null,
      'plain text',
      [],
      { type: '   ' },
      { type: 123 },
    ]) {
      expect(
        adapter.type(new HttpErrorResponse({ status: 409, error: body }))
      ).toBeNull();
    }
  });
});
