import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '@app/auth/auth.service';
import { capabilityForPath } from '@app/auth/capabilities';

export const roleGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const path = `/${state.url.split('?')[0].replace(/^\/+/, '')}`;
  const capability = capabilityForPath(path);
  const role = capability?.readRole ?? capability?.writeRole;

  if (!role || authService.roles().includes(role)) {
    return true;
  }

  return router.createUrlTree(['/forbidden']);
};
