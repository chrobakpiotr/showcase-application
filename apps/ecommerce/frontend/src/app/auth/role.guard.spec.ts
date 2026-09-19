import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';

import { AuthService } from '@app/auth/auth.service';
import { roleGuard } from '@app/auth/role.guard';

describe('roleGuard', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  function setup(roles: string[]): void {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: Router,
          useValue: {
            createUrlTree: (commands: unknown[]) =>
              ({ commands } as unknown as UrlTree),
          },
        },
        {
          provide: AuthService,
          useValue: { roles: signal(roles) },
        },
      ],
    });
  }

  function run(url: string): boolean | UrlTree {
    return TestBed.runInInjectionContext(() =>
      roleGuard({} as never, { url } as never)
    ) as boolean | UrlTree;
  }

  it('allows a role-agnostic capability', () => {
    setup([]);
    expect(run('/cart')).toBeTrue();
  });

  it('allows an unknown route and leaves not-found handling to the router', () => {
    setup([]);
    expect(run('/unknown')).toBeTrue();
  });

  it('allows a read capability when the role is present', () => {
    setup(['ORDER_READ']);
    expect(run('/orders?status=CONFIRMED')).toBeTrue();
  });

  it('allows a write-only capability when the role is present', () => {
    setup(['ORDER_WRITE']);
    expect(run('/order')).toBeTrue();
  });

  it('redirects to forbidden when the required role is missing', () => {
    setup([]);
    const result = run('/inventory') as UrlTree;
    expect((result as unknown as { commands: string[] }).commands).toEqual([
      '/forbidden',
    ]);
  });
});
