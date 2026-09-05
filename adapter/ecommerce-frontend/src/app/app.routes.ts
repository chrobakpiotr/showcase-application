import { Routes } from '@angular/router';
import { authGuard } from '@app/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./dashboard/dashboard.component').then(
        (m) => m.DashboardComponent
      ),
    canActivate: [authGuard],
  },
  {
    path: 'order',
    loadComponent: () =>
      import('./order/order.component').then((m) => m.OrderComponent),
    canActivate: [authGuard],
  },
  {
    path: 'orders',
    loadComponent: () =>
      import('./order-list/order-list.component').then(
        (m) => m.OrderListComponent
      ),
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
  {
    path: 'catalog',
    loadComponent: () =>
      import('./catalog/catalog.component').then((m) => m.CatalogComponent),
    canActivate: [authGuard],
  },
  {
    path: 'cart',
    loadComponent: () =>
      import('./cart/cart.component').then((m) => m.CartComponent),
    canActivate: [authGuard],
  },
  {
    path: 'inventory',
    loadComponent: () =>
      import('./inventory/inventory.component').then(
        (m) => m.InventoryComponent
      ),
    canActivate: [authGuard],
  },
  {
    path: 'reviews',
    loadComponent: () =>
      import('./reviews/reviews.component').then((m) => m.ReviewsComponent),
    canActivate: [authGuard],
  },
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: '**',
    loadComponent: () =>
      import('./not-found/not-found.component').then(
        (m) => m.NotFoundComponent
      ),
  },
];
