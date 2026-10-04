import type { Mock } from "vitest";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { ActivatedRoute } from "@angular/router";

import { AuthService } from "@app/auth/auth.service";
import { LoginComponent } from "@app/auth/login.component";

describe("LoginComponent", () => {
  beforeEach(() => {
    vi.useFakeTimers({ advanceTimeDelta: 1, shouldAdvanceTime: true });
  });
  afterEach(() => {
    vi.useRealTimers();
  });
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let loginSpy: Mock;

  function setup(returnUrl?: string): void {
    loginSpy = vi.fn().mockName("login").mockResolvedValue(undefined);

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

  it("renders redirect-based Keycloak login without password fields", () => {
    setup();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('[data-testid="login-submit"]')).toBeTruthy();
    expect(compiled.querySelector('input[type="password"]')).toBeNull();
    expect(compiled.textContent).toContain("Authorization Code + PKCE");
  });

  it("starts login with the original returnUrl", async () => {
    setup("/orders");

    component.onLogin();
    await vi.advanceTimersByTimeAsync(0);

    expect(loginSpy).toHaveBeenCalledWith("/orders");
  });

  it("uses dashboard as the default post-login route", async () => {
    setup();

    component.onLogin();
    await vi.advanceTimersByTimeAsync(0);

    expect(loginSpy).toHaveBeenCalledWith("/dashboard");
  });

  it("does not start a second redirect while submitting", () => {
    setup();
    component.submitting.set(true);
    fixture.detectChanges();

    component.onLogin();

    const button = fixture.nativeElement.querySelector(
      '[data-testid="login-submit"]',
    ) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain("Redirecting");
    expect(loginSpy).not.toHaveBeenCalled();
  });

  it("shows an error if the OIDC redirect cannot be started", async () => {
    setup();

    let rejectLogin: (reason?: unknown) => void = () => undefined;
    const loginPromise = new Promise<void>((_resolve, reject) => {
      rejectLogin = reject;
    });
    loginSpy.mockReturnValue(loginPromise);

    component.onLogin();

    // Reject only after onLogin() has already attached its catch handler.
    rejectLogin(new Error("Keycloak unavailable"));
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();

    expect(component.submitting()).toBe(false);
    expect(
      fixture.nativeElement.querySelector('[role="alert"]').textContent,
    ).toContain("Could not start the Keycloak sign-in flow");
  });
});
