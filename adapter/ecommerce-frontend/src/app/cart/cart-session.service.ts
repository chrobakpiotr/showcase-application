import { Injectable, inject } from '@angular/core';
import { Observable, map, of, tap } from 'rxjs';

import { CartService } from '@app/cart/cart.service';

export const CART_ID_STORAGE_KEY = 'ecommerce_cart_id';

@Injectable({ providedIn: 'root' })
export class CartSessionService {
  private readonly cartService = inject(CartService);

  getCartId(): string | null {
    return sessionStorage.getItem(CART_ID_STORAGE_KEY);
  }

  remember(cartId: string): void {
    sessionStorage.setItem(CART_ID_STORAGE_KEY, cartId);
  }

  clear(): void {
    sessionStorage.removeItem(CART_ID_STORAGE_KEY);
  }

  ensureCartId(): Observable<string> {
    const existing = this.getCartId();
    if (existing) return of(existing);

    return this.cartService.createCart().pipe(
      tap((cart) => this.remember(cart.cartId)),
      map((cart) => cart.cartId)
    );
  }
}
