import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  flushMicrotasks,
} from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';

import { AuthService } from '@app/auth/auth.service';
import { LoginComponent } from '@app/auth/login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let loginSpy: jasmine.Spy;

  function setup(returnUrl?: string): void {
    loginSpy = jasmine.createSpy('login').and.resolveTo();

    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        {
          provide: AuthService,
          useValue: { login: loginSpy },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: () => returnUrl ?? null,
              },
            },
          },
        },
      ],
    });

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('renders redirect-based Keycloak login without password fields', () => {
    setup();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('[data-testid="login-submit"]')).toBeTruthy();
    expect(compiled.querySelector('input[type="password"]')).toBeNull();
    expect(compiled.textContent).toContain('Authorization Code + PKCE');
  });

  it('starts login with the original returnUrl', fakeAsync(() => {
    setup('/orders');

    component.onLogin();
    flushMicrotasks();

    expect(loginSpy).toHaveBeenCalledWith('/orders');
  }));

  it('uses dashboard as the default post-login route', fakeAsync(() => {
    setup();

    component.onLogin();
    flushMicrotasks();

    expect(loginSpy).toHaveBeenCalledWith('/dashboard');
  }));

  it('does not start a second redirect while submitting', () => {
    setup();
    component.submitting.set(true);
    fixture.detectChanges();

    component.onLogin();

    const button = fixture.nativeElement.querySelector(
      '[data-testid="login-submit"]'
    ) as HTMLButtonElement;
    expect(button.disabled).toBeTrue();
    expect(button.textContent).toContain('Redirecting');
    expect(loginSpy).not.toHaveBeenCalled();
  });

  it('shows an error if the OIDC redirect cannot be started', fakeAsync(() => {
    setup();

    let rejectLogin: (reason?: unknown) => void = () => undefined;
    const loginPromise = new Promise<void>((_resolve, reject) => {
      rejectLogin = reject;
    });
    loginSpy.and.returnValue(loginPromise);

    component.onLogin();

    // Reject only after onLogin() has already attached its catch handler.
    rejectLogin(new Error('Keycloak unavailable'));
    flushMicrotasks();
    fixture.detectChanges();

    expect(component.submitting()).toBeFalse();
    expect(
      fixture.nativeElement.querySelector('[role="alert"]').textContent
    ).toContain('Could not start the Keycloak sign-in flow');
  }));
});
