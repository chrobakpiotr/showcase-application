import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { KEYCLOAK } from '@app/auth/keycloak.instance';

interface OperatorToken {
  preferred_username?: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private static readonly REFRESH_MIN_VALIDITY_SECONDS = 30;

  private readonly keycloak = inject(KEYCLOAK);
  private readonly document = inject(DOCUMENT);

  readonly initialized = signal(false);
  readonly isAuthenticated = signal(false);
  readonly username = signal('');
  readonly roles = signal<string[]>([]);

  constructor() {
    this.keycloak.onAuthSuccess = () => this.syncFromKeycloak();
    this.keycloak.onAuthRefreshSuccess = () => this.syncFromKeycloak();
    this.keycloak.onAuthLogout = () => this.clearLocalState();
    this.keycloak.onTokenExpired = () => {
      void this.refreshExpiredToken();
    };
  }

  async initialize(): Promise<void> {
    try {
      const authenticated = await this.keycloak.init({
        onLoad: 'check-sso',
        flow: 'standard',
        pkceMethod: 'S256',
        checkLoginIframe: false,
      });
      if (authenticated) {
        this.syncFromKeycloak();
      } else {
        this.clearLocalState();
      }
    } catch {
      this.clearLocalState();
    } finally {
      this.initialized.set(true);
    }
  }

  async login(returnUrl = '/dashboard'): Promise<void> {
    await this.keycloak.login({
      redirectUri: this.internalAppUrl(returnUrl),
    });
  }

  async logout(): Promise<void> {
    this.clearLocalState();
    await this.keycloak.logout({
      redirectUri: this.internalAppUrl('/login'),
    });
  }

  async getValidAccessToken(): Promise<string | null> {
    if (!this.keycloak.authenticated || !this.keycloak.token) {
      this.clearLocalState();
      return null;
    }

    try {
      await this.keycloak.updateToken(AuthService.REFRESH_MIN_VALIDITY_SECONDS);
      this.syncFromKeycloak();
      return this.keycloak.token ?? null;
    } catch {
      this.keycloak.clearToken();
      this.clearLocalState();
      return null;
    }
  }

  private async refreshExpiredToken(): Promise<void> {
    try {
      await this.keycloak.updateToken(0);
      this.syncFromKeycloak();
    } catch {
      this.keycloak.clearToken();
      this.clearLocalState();
    }
  }

  private syncFromKeycloak(): void {
    if (!this.keycloak.authenticated || !this.keycloak.token) {
      this.clearLocalState();
      return;
    }

    const parsed = this.keycloak.tokenParsed as OperatorToken | undefined;
    this.isAuthenticated.set(true);
    this.username.set(parsed?.preferred_username ?? '');
    this.roles.set([...(this.keycloak.realmAccess?.roles ?? [])]);
  }

  private clearLocalState(): void {
    this.isAuthenticated.set(false);
    this.username.set('');
    this.roles.set([]);
  }

  private internalAppUrl(returnUrl: string): string {
    const base = new URL(this.document.baseURI);
    const basePath = base.pathname.endsWith('/')
      ? base.pathname
      : `${base.pathname}/`;
    const fallback = new URL('dashboard', base).href;

    if (
      !returnUrl.startsWith('/') ||
      returnUrl.startsWith('//') ||
      returnUrl.includes('\\')
    ) {
      return fallback;
    }

    const resolved = new URL(returnUrl.slice(1), base);
    if (!resolved.pathname.startsWith(basePath)) {
      return fallback;
    }

    return resolved.href;
  }
}
