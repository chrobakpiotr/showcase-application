import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthService } from '@app/auth/auth.service';

interface DashboardCard {
  title: string;
  description: string;
  routerLink: string;
  requiredRole: string | null;
}

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
})
export class DashboardComponent {
  readonly authService = inject(AuthService);

  // A single entry point into every bounded context this showcase implements. requiredRole is null for
  // endpoints that are genuinely public (permitAll) server-side (Cart, Reviews) - kept behind the app's login
  // shell purely for UX consistency, per the existing "must log in to see the nav at all" design.
  readonly cards: DashboardCard[] = [
    {
      title: 'Place an order',
      description:
        'Build an order from scratch and place it, including payment method selection.',
      routerLink: '/order',
      requiredRole: null,
    },
    {
      title: 'Orders',
      description:
        'Browse placed orders, inspect payment status, and cancel a confirmed order.',
      routerLink: '/orders',
      requiredRole: 'ORDER_READ',
    },
    {
      title: 'Catalog',
      description: 'Browse products by category.',
      routerLink: '/catalog',
      requiredRole: 'CATALOG_READ',
    },
    {
      title: 'Cart',
      description:
        'Build an anonymous shopping cart and manage its line items.',
      routerLink: '/cart',
      requiredRole: null,
    },
    {
      title: 'Inventory',
      description:
        'Look up stock levels and receive/reserve/release/fulfill stock.',
      routerLink: '/inventory',
      requiredRole: 'INVENTORY_READ',
    },
    {
      title: 'Reviews & Ratings',
      description:
        'Submit and browse product reviews, and moderate the pending queue.',
      routerLink: '/reviews',
      requiredRole: null,
    },
    {
      title: 'Analytics Assistant',
      description:
        'Ask an AI assistant natural-language questions about order data.',
      routerLink: '/analytics',
      requiredRole: 'ORDER_READ',
    },
  ];

  isVisible(card: DashboardCard): boolean {
    return (
      !card.requiredRole || this.authService.roles().includes(card.requiredRole)
    );
  }
}
