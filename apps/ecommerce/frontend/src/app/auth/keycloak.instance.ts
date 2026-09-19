import { InjectionToken } from '@angular/core';
import Keycloak from 'keycloak-js';

import { environment } from '@environments/environment';

export const KEYCLOAK = new InjectionToken<Keycloak>('KEYCLOAK', {
  providedIn: 'root',
  factory: () =>
    new Keycloak({
      url: environment.authUrl,
      realm: environment.authRealm,
      clientId: environment.clientId,
    }),
});
