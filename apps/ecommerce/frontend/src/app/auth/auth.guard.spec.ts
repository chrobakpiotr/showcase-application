import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';

import { AuthService } from '@app/auth/auth.service';
import { authGuard } from '@app/auth/auth.guard';

describe('authGuard', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  function setup(authenticated: boolean): void {
    const isAuthenticatedSignal = signal(authenticated);
    TestBed.configureTestingModule({
      providers: [
        {
          provide: Router,
          useValue: {
            createUrlTree: (commands: unknown[], extras?: unknown) =>
              ({ commands, extras } as unknown as UrlTree),
            navigate: jasmine.createSpy('navigate'),
          },
        },
        {
          provide: AuthService,
          useValue: { isAuthenticated: isAuthenticatedSignal },
        },
      ],
    });
  }

  it('returns true when user is authenticated', () => {
    setup(true);
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url: '/order' } as never)
    );
    expect(result).toBeTrue();
  });

  it('returns UrlTree redirecting to /login when not authenticated', () => {
    setup(false);
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url: '/order' } as never)
    ) as UrlTree;
    expect(result).toBeTruthy();
    expect((result as unknown as { commands: string[] }).commands).toEqual([
      '/login',
    ]);
    expect(
      (result as unknown as { extras: { queryParams: { returnUrl: string } } })
        .extras.queryParams.returnUrl
    ).toBe('/order');
  });
});
