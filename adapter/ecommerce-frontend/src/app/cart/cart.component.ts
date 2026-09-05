import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';

import { CartModel } from '@app/cart/cart.model';
import { CartService } from '@app/cart/cart.service';

// A cart is anonymous/session-based server-side (no persisted customer account - see ADR 0027), so the frontend
// holds onto the generated cartId across page reloads via sessionStorage, mirroring AuthService's own token storage.
const CART_ID_STORAGE_KEY = 'ecommerce_cart_id';

@Component({
  selector: 'app-cart',
  templateUrl: './cart.component.html',
  styleUrls: ['./cart.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, ReactiveFormsModule],
})
export class CartComponent implements OnInit {
  private readonly cartService = inject(CartService);

  readonly cart = signal<CartModel | null>(null);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly addItemForm = new FormGroup({
    sku: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    quantity: new FormControl<number | null>(1, {
      validators: [Validators.required, Validators.min(1)],
    }),
  });

  ngOnInit(): void {
    const storedCartId = sessionStorage.getItem(CART_ID_STORAGE_KEY);
    if (storedCartId) {
      this.loadCart(storedCartId);
    } else {
      this.startNewCart();
    }
  }

  startNewCart(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.cartService.createCart().subscribe({
      next: (cart) => {
        this.loading.set(false);
        this.persistAndShow(cart);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Failed to start a new cart.');
      },
    });
  }

  addItem(): void {
    if (this.addItemForm.invalid || !this.cart()) return;
    const { sku, quantity } = this.addItemForm.getRawValue();
    this.cartService.addItem(this.cart()!.cartId, sku, quantity!).subscribe({
      next: (cart) => {
        this.cart.set(cart);
        this.addItemForm.reset({ sku: '', quantity: 1 });
      },
      error: () =>
        this.errorMessage.set(
          'Failed to add item - check the SKU exists in the catalog.'
        ),
    });
  }

  updateQuantity(sku: string, quantity: number): void {
    if (!this.cart() || quantity < 1) return;
    this.cartService
      .updateItemQuantity(this.cart()!.cartId, sku, quantity)
      .subscribe({
        next: (cart) => this.cart.set(cart),
        error: () => this.errorMessage.set('Failed to update quantity.'),
      });
  }

  removeItem(sku: string): void {
    if (!this.cart()) return;
    this.cartService.removeItem(this.cart()!.cartId, sku).subscribe({
      next: (cart) => this.cart.set(cart),
      error: () => this.errorMessage.set('Failed to remove item.'),
    });
  }

  clearCart(): void {
    if (!this.cart()) return;
    this.cartService.clearCart(this.cart()!.cartId).subscribe({
      next: (cart) => this.cart.set(cart),
      error: () => this.errorMessage.set('Failed to clear cart.'),
    });
  }

  private loadCart(cartId: string): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.cartService.getCart(cartId).subscribe({
      next: (cart) => {
        this.loading.set(false);
        this.cart.set(cart);
      },
      // The stored cartId no longer exists server-side (e.g. a fresh backend/DB) - transparently start a new one
      // rather than surfacing an error for something the customer never did themselves.
      error: () => {
        this.loading.set(false);
        this.startNewCart();
      },
    });
  }

  private persistAndShow(cart: CartModel): void {
    sessionStorage.setItem(CART_ID_STORAGE_KEY, cart.cartId);
    this.cart.set(cart);
  }
}
