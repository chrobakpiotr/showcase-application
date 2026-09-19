import { Routes } from '@angular/router';
import { authGuard } from '@app/auth/auth.guard';
import { roleGuard } from '@app/auth/role.guard';

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
    canActivate: [authGuard, roleGuard],
  },
  {
    path: 'orders',
    loadComponent: () =>
      import('./order-list/order-list.component').then(
        (m) => m.OrderListComponent
      ),
    canActivate: [authGuard, roleGuard],
  },
  {
    path: 'analytics',
    loadComponent: () =>
      import('./analytics-assistant/analytics-assistant.component').then(
        (m) => m.AnalyticsAssistantComponent
      ),
    canActivate: [authGuard, roleGuard],
  },
  {
    path: 'catalog',
    loadComponent: () =>
      import('./catalog/catalog.component').then((m) => m.CatalogComponent),
    canActivate: [authGuard, roleGuard],
  },
  {
    path: 'cart',
    loadComponent: () =>
      import('./cart/cart.component').then((m) => m.CartComponent),
    canActivate: [authGuard, roleGuard],
  },
  {
    path: 'wishlist',
    loadComponent: () =>
      import('./wishlist/wishlist.component').then((m) => m.WishlistComponent),
    canActivate: [authGuard, roleGuard],
  },
  {
    path: 'recommendations',
    loadComponent: () =>
      import('./recommendations/recommendations.component').then(
        (m) => m.RecommendationsComponent
      ),
    canActivate: [authGuard, roleGuard],
  },
  {
    path: 'inventory',
    loadComponent: () =>
      import('./inventory/inventory.component').then(
        (m) => m.InventoryComponent
      ),
    canActivate: [authGuard, roleGuard],
  },
  {
    path: 'reviews',
    loadComponent: () =>
      import('./reviews/reviews.component').then((m) => m.ReviewsComponent),
    canActivate: [authGuard, roleGuard],
  },
  {
    path: 'returns',
    loadComponent: () =>
      import('./returns/returns.component').then((m) => m.ReturnsComponent),
    canActivate: [authGuard, roleGuard],
  },
  {
    path: 'notifications',
    loadComponent: () =>
      import('./notifications/notifications.component').then(
        (m) => m.NotificationsComponent
      ),
    canActivate: [authGuard, roleGuard],
  },
  {
    path: 'shipments',
    loadComponent: () =>
      import('./shipments/shipments.component').then(
        (m) => m.ShipmentsComponent
      ),
    canActivate: [authGuard, roleGuard],
  },
  {
    path: 'coupons',
    loadComponent: () =>
      import('./coupons/coupons.component').then((m) => m.CouponsComponent),
    canActivate: [authGuard, roleGuard],
  },
  {
    path: 'forbidden',
    loadComponent: () =>
      import('./forbidden/forbidden.component').then(
        (m) => m.ForbiddenComponent
      ),
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
