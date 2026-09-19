import { APP_BASE_HREF } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ErrorHandler, inject } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';

import { AuthService } from '@app/auth/auth.service';
import { GlobalErrorHandler } from '@app/core/global-error-handler';
import { appConfig } from './app.config';

describe('appConfig', () => {
  let initializeSpy: jasmine.Spy;

  beforeEach(() => {
    initializeSpy = jasmine.createSpy('initialize').and.resolveTo();

    TestBed.configureTestingModule({
      providers: [
        ...appConfig.providers,
        {
          provide: AuthService,
          useValue: { initialize: initializeSpy },
        },
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('provides the application shell dependencies', () => {
    expect(TestBed.inject(Router)).toBeTruthy();
    expect(TestBed.inject(HttpClient)).toBeTruthy();
    expect(TestBed.inject(Title)).toBeTruthy();
    expect(TestBed.inject(APP_BASE_HREF)).toBe('/home');
  });

  it('registers a GlobalErrorHandler as the ErrorHandler', () => {
    const handler = TestBed.runInInjectionContext(() => inject(ErrorHandler));
    expect(handler).toBeInstanceOf(GlobalErrorHandler);
  });

  it('runs the mocked identity initializer instead of a real Keycloak redirect', () => {
    expect(TestBed.inject(AuthService)).toBeTruthy();
    expect(initializeSpy).toHaveBeenCalledTimes(1);
  });
});
