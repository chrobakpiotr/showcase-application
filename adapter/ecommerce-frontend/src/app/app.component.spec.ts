import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { AuthService } from '@app/auth/auth.service';
import { AppComponent } from './app.component';

describe('AppComponent', () => {
  let fixture: ComponentFixture<AppComponent>;
  let component: AppComponent;
  let logoutSpy: jasmine.Spy;
  let router: Router;

  function setup(
    authenticated: boolean,
    username = '',
    roles: string[] = []
  ): void {
    logoutSpy = jasmine.createSpy('logout');

    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            isAuthenticated: signal(authenticated),
            username: signal(username),
            roles: signal(roles),
            logout: logoutSpy,
          },
        },
      ],
    });

    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
    fixture = TestBed.createComponent(AppComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should create the component', () => {
    setup(false);
    expect(component).toBeTruthy();
  });

  it('renders router-outlet', () => {
    setup(false);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('router-outlet')).toBeTruthy();
  });

  it('shows header when isAuthenticated is true', () => {
    setup(true, 'admin');
    const compiled = fixture.nativeElement as HTMLElement;
    const header = compiled.querySelector('header');
    expect(header).toBeTruthy();
    expect(header?.textContent).toContain('admin');
  });

  it('hides header when isAuthenticated is false', () => {
    setup(false);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('header')).toBeFalsy();
  });

  it('logout() calls authService.logout() and navigates to /login', () => {
    setup(true);
    component.logout();
    expect(logoutSpy).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('shows the analytics nav link when the user has ORDER_READ', () => {
    setup(true, 'admin', ['ORDER_READ']);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('a[routerLink="/analytics"]')).toBeTruthy();
  });

  it('hides the analytics nav link when the user lacks ORDER_READ', () => {
    setup(true, 'admin', []);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('a[routerLink="/analytics"]')).toBeFalsy();
  });

  it('shows the catalog nav link when the user has CATALOG_READ', () => {
    setup(true, 'admin', ['CATALOG_READ']);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('a[routerLink="/catalog"]')).toBeTruthy();
  });

  it('hides the catalog nav link when the user lacks CATALOG_READ', () => {
    setup(true, 'admin', []);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('a[routerLink="/catalog"]')).toBeFalsy();
  });
});
