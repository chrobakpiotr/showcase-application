import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';

import { environment } from '@environments/environment';

const TOKEN_STORAGE_KEY = 'ecommerce_access_token';

interface JwtPayload {
  sub: string;
  preferred_username: string;
  exp: number;
  realm_access: { roles: string[] };
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly httpClient = inject(HttpClient);

  readonly isAuthenticated = signal<boolean>(false);
  readonly username = signal<string>('');
  readonly roles = signal<string[]>([]);

  private token: string | null = null;

  constructor() {
    this.restoreSession();
  }

  private decodeJwtPayload(token: string): JwtPayload {
    const payloadPart = token.split('.')[1];
    const base64 = payloadPart.replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(base64)) as JwtPayload;
  }

  private isTokenExpired(token: string): boolean {
    try {
      const payload = this.decodeJwtPayload(token);
      return payload.exp * 1000 < Date.now();
    } catch {
      return true;
    }
  }

  private applyToken(token: string): void {
    const payload = this.decodeJwtPayload(token);
    this.token = token;
    this.isAuthenticated.set(true);
    this.username.set(payload.preferred_username);
    this.roles.set(payload.realm_access.roles);
  }

  private restoreSession(): void {
    const storedToken = sessionStorage.getItem(TOKEN_STORAGE_KEY);
    if (storedToken && !this.isTokenExpired(storedToken)) {
      this.applyToken(storedToken);
    }
  }

  login(username: string, password: string): Observable<void> {
    const body = new HttpParams()
      .set('grant_type', 'password')
      .set('client_id', environment.clientId)
      .set('username', username)
      .set('password', password);

    return this.httpClient
      .post<{ access_token: string }>(
        environment.authTokenUrl,
        body.toString(),
        {
          headers: new HttpHeaders({
            'Content-Type': 'application/x-www-form-urlencoded',
          }),
        }
      )
      .pipe(
        tap((response) => {
          sessionStorage.setItem(TOKEN_STORAGE_KEY, response.access_token);
          this.applyToken(response.access_token);
        }),
        map(() => void 0),
        catchError((err) => {
          const message =
            err.status === 401
              ? 'Invalid username or password.'
              : 'Login failed. Please try again.';
          return throwError(() => new Error(message));
        })
      );
  }

  logout(): void {
    sessionStorage.removeItem(TOKEN_STORAGE_KEY);
    this.token = null;
    this.isAuthenticated.set(false);
    this.username.set('');
    this.roles.set([]);
  }

  getAccessToken(): string | null {
    return this.token;
  }
}
