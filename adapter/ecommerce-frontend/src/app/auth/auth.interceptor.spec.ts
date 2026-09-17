import { TestBed } from '@angular/core/testing';
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
  let getAccessTokenSpy: jasmine.Spy;

  function setup(token: string | null = null): void {
    sessionStorage.clear();
    getAccessTokenSpy = jasmine
      .createSpy('getAccessToken')
      .and.returnValue(token);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(
          withInterceptors([authInterceptor]),
          withInterceptorsFromDi()
        ),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: { getAccessToken: getAccessTokenSpy },
        },
      ],
    });
    httpClient = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    httpTesting.verify();
    TestBed.resetTestingModule();
    sessionStorage.clear();
  });

  it('adds Authorization header when token exists and URL includes apiPrefix', () => {
    setup('my-test-token');
    const apiUrl = `${environment.apiPrefix}/order`;
    httpClient.get(apiUrl).subscribe();
    const req = httpTesting.expectOne(apiUrl);
    expect(req.request.headers.get('Authorization')).toBe(
      'Bearer my-test-token'
    );
    req.flush({});
  });

  it('does NOT add Authorization header when URL does NOT include apiPrefix', () => {
    setup('my-test-token');
    const tokenUrl = environment.authTokenUrl;
    httpClient.get(tokenUrl).subscribe();
    const req = httpTesting.expectOne(tokenUrl);
    expect(req.request.headers.get('Authorization')).toBeNull();
    req.flush({});
  });

  it('does NOT add Authorization header when token is null', () => {
    setup(null);
    const apiUrl = `${environment.apiPrefix}/order`;
    httpClient.get(apiUrl).subscribe();
    const req = httpTesting.expectOne(apiUrl);
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
      const req = httpTesting.expectOne(url);
      expect(req.request.headers.has('Authorization')).toBeFalse();
      req.flush({});
    });
  }

  it('attaches to the exact API path and its absolute same-origin descendants', () => {
    setup('secret-token');
    for (const url of [
      environment.apiPrefix,
      new URL(`${environment.apiPrefix}/order?x=1`, document.baseURI).href,
    ]) {
      httpClient.get(url).subscribe();
      const req = httpTesting.expectOne(url);
      expect(req.request.headers.get('Authorization')).toBe(
        'Bearer secret-token'
      );
      req.flush({});
    }
  });
});
