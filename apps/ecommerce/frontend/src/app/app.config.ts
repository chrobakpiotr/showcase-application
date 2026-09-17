import { APP_BASE_HREF } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, ErrorHandler } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';

import { authInterceptor } from '@app/auth/auth.interceptor';
import { GlobalErrorHandler } from '@app/core/global-error-handler';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    Title,
    { provide: APP_BASE_HREF, useValue: '/home' },
    { provide: ErrorHandler, useClass: GlobalErrorHandler },
  ],
};
