import { signal } from '@angular/core';
import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from '@app/auth/auth.service';
import { LoginComponent } from '@app/auth/login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let loginSpy: jasmine.Spy;
  let navigateByUrlSpy: jasmine.Spy;
  let queryParamMapGetSpy: jasmine.Spy;

  function setup(returnUrl?: string): void {
    loginSpy = jasmine.createSpy('login');
    navigateByUrlSpy = jasmine.createSpy('navigateByUrl');
    queryParamMapGetSpy = jasmine
      .createSpy('get')
      .and.returnValue(returnUrl ?? null);

    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        {
          provide: AuthService,
          useValue: {
            isAuthenticated: signal(false),
            login: loginSpy,
          },
        },
        {
          provide: Router,
          useValue: { navigateByUrl: navigateByUrlSpy },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: { get: queryParamMapGetSpy },
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

  it('should create the component', () => {
    setup();
    expect(component).toBeTruthy();
  });

  it('should not submit when form is invalid', () => {
    setup();
    component.onSubmit();
    expect(loginSpy).not.toHaveBeenCalled();
  });

  it('should not submit when already submitting', () => {
    setup();
    component.loginForm.setValue({ username: 'user', password: 'pass' });
    component.submitting.set(true);
    component.onSubmit();
    expect(loginSpy).not.toHaveBeenCalled();
  });

  it('should show validation errors when fields are touched and invalid', () => {
    setup();
    component.usernameControl.markAsTouched();
    component.passwordControl.markAsTouched();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const errors = compiled.querySelectorAll('.error');
    expect(errors.length).toBe(2);
  });

  it('should call authService.login() with form values on submit', fakeAsync(() => {
    setup();
    loginSpy.and.returnValue(of(void 0));
    component.loginForm.setValue({ username: 'admin', password: 'secret' });
    component.onSubmit();
    tick();
    expect(loginSpy).toHaveBeenCalledWith('admin', 'secret');
  }));

  it('on success: navigates to returnUrl query param', fakeAsync(() => {
    setup('/order');
    loginSpy.and.returnValue(of(void 0));
    component.loginForm.setValue({ username: 'admin', password: 'secret' });
    component.onSubmit();
    tick();
    expect(navigateByUrlSpy).toHaveBeenCalledWith('/order');
  }));

  it('on success with no returnUrl: navigates to /dashboard', fakeAsync(() => {
    setup();
    loginSpy.and.returnValue(of(void 0));
    component.loginForm.setValue({ username: 'admin', password: 'secret' });
    component.onSubmit();
    tick();
    expect(navigateByUrlSpy).toHaveBeenCalledWith('/dashboard');
  }));

  it('on error: shows errorMessage', fakeAsync(() => {
    setup();
    loginSpy.and.returnValue(
      throwError(() => new Error('Invalid username or password.'))
    );
    component.loginForm.setValue({ username: 'admin', password: 'wrong' });
    component.onSubmit();
    tick();
    fixture.detectChanges();
    expect(component.errorMessage()).toBe('Invalid username or password.');
    const compiled = fixture.nativeElement as HTMLElement;
    const alert = compiled.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('Invalid username or password.');
  }));

  it('button is disabled when form is invalid', () => {
    setup();
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector(
      '[data-testid="login-submit"]'
    ) as HTMLButtonElement;
    expect(button.disabled).toBeTrue();
  });

  it('button is disabled when submitting', fakeAsync(() => {
    setup();
    loginSpy.and.returnValue(of(void 0));
    component.loginForm.setValue({ username: 'admin', password: 'secret' });
    component.submitting.set(true);
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector(
      '[data-testid="login-submit"]'
    ) as HTMLButtonElement;
    expect(button.disabled).toBeTrue();
  }));
});
