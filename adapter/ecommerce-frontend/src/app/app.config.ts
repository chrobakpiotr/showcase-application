import { APP_BASE_HREF } from '@angular/common';
import {
  provideHttpClient,
  withInterceptors,
  withInterceptorsFromDi,
  withXhr,
} from '@angular/common/http';
import { ApplicationConfig, ErrorHandler } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';

import { authInterceptor } from '@app/auth/auth.interceptor';
import { GlobalErrorHandler } from '@app/core/global-error-handler';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideNoopAnimations(),
    provideHttpClient(
      withXhr(),
      withInterceptors([authInterceptor]),
      withInterceptorsFromDi()
    ),
    Title,
    { provide: APP_BASE_HREF, useValue: '/home' },
    { provide: ErrorHandler, useClass: GlobalErrorHandler },
  ],
};
