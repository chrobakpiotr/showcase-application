import { Routes } from '@angular/router';
import { authGuard } from '@app/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'order',
    loadComponent: () =>
      import('./order/order.component').then((m) => m.OrderComponent),
    canActivate: [authGuard],
  },
  {
    path: 'analytics',
    loadComponent: () =>
      import('./analytics-assistant/analytics-assistant.component').then(
        (m) => m.AnalyticsAssistantComponent
      ),
    canActivate: [authGuard],
  },
  { path: '', redirectTo: 'order', pathMatch: 'full' },
  {
    path: '**',
    loadComponent: () =>
      import('./not-found/not-found.component').then(
        (m) => m.NotFoundComponent
      ),
  },
];
