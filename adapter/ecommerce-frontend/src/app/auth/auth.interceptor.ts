import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';

import { environment } from '@environments/environment';
import { AuthService } from '@app/auth/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const document = inject(DOCUMENT);
  const authService = inject(AuthService);
  const token = authService.getAccessToken();
  if (token && isApiRequest(req.url, document.baseURI)) {
    return next(
      req.clone({
        headers: req.headers.set('Authorization', `Bearer ${token}`),
      })
    );
  }
  return next(req);
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
