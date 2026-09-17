import { APP_BASE_HREF } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { ErrorHandler, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

import { GlobalErrorHandler } from '@app/core/global-error-handler';
import { appConfig } from './app.config';

describe('appConfig', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideNoopAnimations(), ...appConfig.providers],
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
});
