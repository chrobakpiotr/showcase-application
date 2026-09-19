import { TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
  withInterceptorsFromDi,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';

import { authInterceptor } from '@app/auth/auth.interceptor';
import { AuthService } from '@app/auth/auth.service';
import { environment } from '@environments/environment';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpTesting: HttpTestingController;
  let getValidAccessTokenSpy: jasmine.Spy;

  function setup(token: string | null = null): void {
    getValidAccessTokenSpy = jasmine
      .createSpy('getValidAccessToken')
      .and.resolveTo(token);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(
          withInterceptors([authInterceptor]),
          withInterceptorsFromDi()
        ),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: { getValidAccessToken: getValidAccessTokenSpy },
        },
      ],
    });
    httpClient = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    httpTesting.verify();
    TestBed.resetTestingModule();
  });

  it('refreshes and adds Authorization for protected API requests', fakeAsync(() => {
    setup('fresh-token');
    const apiUrl = `${environment.apiPrefix}/order`;

    httpClient.get(apiUrl).subscribe();
    flushMicrotasks();

    expect(getValidAccessTokenSpy).toHaveBeenCalled();
    const req = httpTesting.expectOne(apiUrl);
    expect(req.request.headers.get('Authorization')).toBe('Bearer fresh-token');
    req.flush({});
  }));

  it('does not add Authorization when refresh returns null', fakeAsync(() => {
    setup(null);
    const apiUrl = `${environment.apiPrefix}/order`;

    httpClient.get(apiUrl).subscribe();
    flushMicrotasks();

    const req = httpTesting.expectOne(apiUrl);
    expect(req.request.headers.get('Authorization')).toBeNull();
    req.flush({});
  }));

  it('does not ask for a token outside the API boundary', () => {
    setup('secret-token');
    const keycloakUrl = `${environment.authUrl}/realms/${environment.authRealm}`;

    httpClient.get(keycloakUrl).subscribe();

    expect(getValidAccessTokenSpy).not.toHaveBeenCalled();
    const req = httpTesting.expectOne(keycloakUrl);
    expect(req.request.headers.get('Authorization')).toBeNull();
    req.flush({});
  });

  for (const url of [
    'https://attacker.example/home/api/order',
    '//attacker.example/home/api/order',
    '/elsewhere?next=/home/api/order',
    '/home/api-sibling/order',
    '/home/api/../outside',
    'data:text/plain,/home/api',
    'http://[invalid',
  ]) {
    it(`does not attach a token outside the API boundary: ${url}`, () => {
      setup('secret-token');

      httpClient.get(url).subscribe();

      expect(getValidAccessTokenSpy).not.toHaveBeenCalled();
      const req = httpTesting.expectOne(url);
      expect(req.request.headers.has('Authorization')).toBeFalse();
      req.flush({});
    });
  }

  it('attaches to the exact API path and same-origin descendants', fakeAsync(() => {
    setup('secret-token');

    for (const url of [
      environment.apiPrefix,
      new URL(`${environment.apiPrefix}/order?x=1`, document.baseURI).href,
    ]) {
      httpClient.get(url).subscribe();
      flushMicrotasks();

      const req = httpTesting.expectOne(url);
      expect(req.request.headers.get('Authorization')).toBe(
        'Bearer secret-token'
      );
      req.flush({});
    }
  }));
});
