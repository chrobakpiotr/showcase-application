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

import { AuthService } from '@app/auth/auth.service';
import { environment } from '@environments/environment';

function createJwt(payload: object): string {
  const header = btoa(JSON.stringify({ alg: 'RS256' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.fakesig`;
}

function createService(initialToken?: string): {
  service: AuthService;
  httpTesting: HttpTestingController;
} {
  sessionStorage.clear();
  if (initialToken !== undefined) {
    sessionStorage.setItem('ecommerce_access_token', initialToken);
  }
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withXhr(), withInterceptorsFromDi()),
      provideHttpClientTesting(),
    ],
  });
  return {
    service: TestBed.inject(AuthService),
    httpTesting: TestBed.inject(HttpTestingController),
  };
}

describe('AuthService', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
    sessionStorage.clear();
  });

  it('should be created with no token: isAuthenticated false, username empty, roles empty, token null', () => {
    const { service, httpTesting } = createService();
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.username()).toBe('');
    expect(service.roles()).toEqual([]);
    expect(service.getAccessToken()).toBeNull();
    httpTesting.verify();
  });

  it('should restore session from a valid stored token', () => {
    const payload = {
      sub: 'user1',
      preferred_username: 'test-user',
      exp: Math.floor(Date.now() / 1000) + 3600,
      realm_access: { roles: ['ORDER_READ'] },
    };
    const token = createJwt(payload);
    const { service, httpTesting } = createService(token);
    expect(service.isAuthenticated()).toBeTrue();
    expect(service.username()).toBe('test-user');
    expect(service.roles()).toContain('ORDER_READ');
    expect(service.getAccessToken()).toBe(token);
    httpTesting.verify();
  });

  it('should NOT restore session from an expired token', () => {
    const payload = {
      sub: 'user1',
      preferred_username: 'user1',
      exp: Math.floor(Date.now() / 1000) - 3600,
      realm_access: { roles: [] },
    };
    const expiredToken = createJwt(payload);
    const { service, httpTesting } = createService(expiredToken);
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.getAccessToken()).toBeNull();
    httpTesting.verify();
  });

  it('should NOT restore session from a malformed token (JSON.parse throws)', () => {
    const malformedToken = `header.${btoa('not-json-at-all')}.sig`;
    const { service, httpTesting } = createService(malformedToken);
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.getAccessToken()).toBeNull();
    httpTesting.verify();
  });

  it('login() success: updates auth state and stores token in sessionStorage', (done) => {
    const { service, httpTesting } = createService();
    const payload = {
      sub: 'user1',
      preferred_username: 'admin',
      exp: Math.floor(Date.now() / 1000) + 3600,
      realm_access: { roles: ['ORDER_WRITE'] },
    };
    const accessToken = createJwt(payload);

    service.login('admin', 'password').subscribe({
      next: (result) => {
        expect(result).toBeUndefined();
        expect(service.isAuthenticated()).toBeTrue();
        expect(service.username()).toBe('admin');
        expect(service.roles()).toContain('ORDER_WRITE');
        expect(sessionStorage.getItem('ecommerce_access_token')).toBe(
          accessToken
        );
        done();
      },
      error: done.fail,
    });

    const req = httpTesting.expectOne(environment.authTokenUrl);
    expect(req.request.method).toBe('POST');
    req.flush({ access_token: accessToken });
    httpTesting.verify();
  });

  it('login() 401 error: returns error message "Invalid username or password."', (done) => {
    const { service, httpTesting } = createService();

    service.login('admin', 'wrong').subscribe({
      next: () => done.fail('should have errored'),
      error: (err: Error) => {
        expect(err.message).toBe('Invalid username or password.');
        done();
      },
    });

    const req = httpTesting.expectOne(environment.authTokenUrl);
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
    httpTesting.verify();
  });

  it('login() 500 error: returns error message "Login failed. Please try again."', (done) => {
    const { service, httpTesting } = createService();

    service.login('admin', 'password').subscribe({
      next: () => done.fail('should have errored'),
      error: (err: Error) => {
        expect(err.message).toBe('Login failed. Please try again.');
        done();
      },
    });

    const req = httpTesting.expectOne(environment.authTokenUrl);
    req.flush('Server error', {
      status: 500,
      statusText: 'Internal Server Error',
    });
    httpTesting.verify();
  });

  it('logout(): clears token, resets signals, removes from sessionStorage', () => {
    const payload = {
      sub: 'user1',
      preferred_username: 'admin',
      exp: Math.floor(Date.now() / 1000) + 3600,
      realm_access: { roles: ['ORDER_WRITE'] },
    };
    const token = createJwt(payload);
    const { service, httpTesting } = createService(token);

    expect(service.isAuthenticated()).toBeTrue();

    service.logout();

    expect(service.isAuthenticated()).toBeFalse();
    expect(service.username()).toBe('');
    expect(service.roles()).toEqual([]);
    expect(service.getAccessToken()).toBeNull();
    expect(sessionStorage.getItem('ecommerce_access_token')).toBeNull();
    httpTesting.verify();
  });

  it('getAccessToken() returns null when not authenticated', () => {
    const { service, httpTesting } = createService();
    expect(service.getAccessToken()).toBeNull();
    httpTesting.verify();
  });

  it('getAccessToken() returns token when authenticated', () => {
    const payload = {
      sub: 'user1',
      preferred_username: 'admin',
      exp: Math.floor(Date.now() / 1000) + 3600,
      realm_access: { roles: [] },
    };
    const token = createJwt(payload);
    const { service, httpTesting } = createService(token);
    expect(service.getAccessToken()).toBe(token);
    httpTesting.verify();
  });
});
