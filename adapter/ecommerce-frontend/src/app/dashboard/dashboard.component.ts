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
      title: 'Wishlist',
      description:
        'Remember products for later and move them into a cart when ready.',
      routerLink: '/wishlist',
      requiredRole: null,
    },
    {
      title: 'Personalized Recommendations',
      description:
        "Get AI-picked products based on a customer e-mail's orders, reviews and catalog matches.",
      routerLink: '/recommendations',
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
      title: 'Returns / RMA',
      description:
        'Request and moderate order returns, then trigger payment refunds.',
      routerLink: '/returns',
      requiredRole: 'RETURN_READ',
    },
    {
      title: 'Notifications',
      description:
        'Inspect the persisted notification log for order and returns flows.',
      routerLink: '/notifications',
      requiredRole: 'NOTIFICATION_READ',
    },
    {
      title: 'Shipping / Fulfillment Tracking',
      description:
        'Create shipments, follow tracking progress, and advance fulfillment status.',
      routerLink: '/shipments',
      requiredRole: 'SHIPMENT_READ',
    },
    {
      title: 'Coupons & Discounts',
      description:
        'Browse, create and activate discount coupons for cart and order flows.',
      routerLink: '/coupons',
      requiredRole: 'COUPON_READ',
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
