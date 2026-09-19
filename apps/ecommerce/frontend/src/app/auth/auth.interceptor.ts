import { DOCUMENT } from '@angular/common';
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap } from 'rxjs';

import { AuthService } from '@app/auth/auth.service';
import { environment } from '@environments/environment';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const document = inject(DOCUMENT);
  if (!isApiRequest(req.url, document.baseURI)) {
    return next(req);
  }

  const authService = inject(AuthService);
  return from(authService.getValidAccessToken()).pipe(
    switchMap((token) =>
      next(
        token
          ? req.clone({
              headers: req.headers.set('Authorization', `Bearer ${token}`),
            })
          : req
      )
    )
  );
};

function isApiRequest(url: string, baseUri: string): boolean {
  try {
    const target = new URL(url, baseUri);
    const api = new URL(environment.apiPrefix, baseUri);
    const path = api.pathname.replace(/\/$/, '');
    return (
      (target.protocol === 'http:' || target.protocol === 'https:') &&
      target.origin === api.origin &&
      (target.pathname === path || target.pathname.startsWith(`${path}/`))
    );
  } catch {
    return false;
  }
}
