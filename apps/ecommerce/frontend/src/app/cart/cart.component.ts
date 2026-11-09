import { HttpErrorResponse } from '@angular/common/http';
import { CurrencyPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { Observable, switchMap } from 'rxjs';

import { AuthService } from '@app/auth/auth.service';
import { CartModel } from '@app/cart/cart.model';
import { CartMutationService } from '@app/cart/cart-mutation.service';
import { CartSessionService } from '@app/cart/cart-session.service';
import { CartService } from '@app/cart/cart.service';
import { CartCheckoutDraft } from '@app/order/cart-checkout-draft.model';

@Component({
  selector: 'app-cart',
  templateUrl: './cart.component.html',
  styleUrls: ['./cart.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, ReactiveFormsModule],
})
export class CartComponent implements OnInit {
  private readonly cartService = inject(CartService);
  private readonly cartSession = inject(CartSessionService);
  private readonly cartMutations = inject(CartMutationService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly navigatingToOrder = signal(false);

  readonly cart = signal<CartModel | null>(null);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly busy = computed(
    () =>
      this.loading() ||
      this.navigatingToOrder() ||
      this.cartMutations.isPending(this.cart()?.cartId)
  );
  readonly canPlaceOrder = computed(() =>
    this.authService.roles().includes('ORDER_WRITE')
  );

  readonly addItemForm = new FormGroup({
    sku: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    quantity: new FormControl<number | null>(1, {
      validators: [Validators.required, Validators.min(1)],
    }),
  });

  readonly couponForm = new FormGroup({
    code: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  ngOnInit(): void {
    this.retryLoad();
  }

  private startNewCart(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.cartService
      .createCart()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
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

  retryLoad(): void {
    if (this.loading()) return;
    const storedCartId = this.cartSession.getCartId();
    if (storedCartId) {
      this.loadCart(storedCartId);
    } else {
      this.startNewCart();
    }
  }

  addItem(): void {
    if (this.addItemForm.invalid) return;
    const { sku, quantity } = this.addItemForm.getRawValue();
    this.mutate(
      (cartId) => this.cartService.addItem(cartId, sku, quantity!),
      'Failed to add item - check the SKU exists in the catalog.',
      () => this.addItemForm.reset({ sku: '', quantity: 1 })
    );
  }

  updateQuantity(sku: string, quantity: number): void {
    if (!(quantity >= 1)) return;
    this.mutate(
      (cartId) => this.cartService.updateItemQuantity(cartId, sku, quantity),
      'Failed to update quantity.'
    );
  }

  removeItem(sku: string): void {
    this.mutate(
      (cartId) => this.cartService.removeItem(cartId, sku),
      'Failed to remove item.'
    );
  }

  clearCart(): void {
    this.mutate(
      (cartId) => this.cartService.clearCart(cartId),
      'Failed to clear cart.'
    );
  }

  applyCoupon(): void {
    if (this.couponForm.invalid) return;
    const { code } = this.couponForm.getRawValue();
    this.mutate(
      (cartId) => this.cartService.applyCoupon(cartId, code),
      'Failed to apply coupon.'
    );
  }

  removeCoupon(): void {
    this.mutate(
      (cartId) => this.cartService.removeCoupon(cartId),
      'Failed to remove coupon.'
    );
  }

  continueToOrder(): void {
    const cart = this.cart();
    if (!cart?.items.length || this.busy() || !this.canPlaceOrder()) return;

    const info: CartCheckoutDraft = {
      kind: 'cart-checkout',
      items: cart.items.map(({ sku, productName, unitPrice, quantity }) => ({
        sku,
        productName,
        unitPrice,
        quantity,
      })),
      couponCode: cart.couponCode,
    };
    this.navigatingToOrder.set(true);
    void this.router
      .navigate(['/order'], { info })
      .catch(() =>
        this.errorMessage.set('Failed to open the order form. Try again.')
      )
      .finally(() => this.navigatingToOrder.set(false));
  }

  private mutate(
    request: (cartId: string) => Observable<CartModel>,
    errorMessage: string,
    onSuccess?: () => void
  ): void {
    const cart = this.cart();
    if (!cart || this.busy()) return;
    this.errorMessage.set(null);
    this.cartMutations
      .track(cart.cartId, request(cart.cartId))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.cart.set(updated);
          onSuccess?.();
        },
        error: () => this.loadCart(cart.cartId, errorMessage),
      });
  }

  private loadCart(cartId: string, mutationError: string | null = null): void {
    this.loading.set(true);
    this.cart.set(null);
    this.errorMessage.set(mutationError);
    this.cartMutations
      .whenSettled(cartId)
      .pipe(
        switchMap(() => this.cartService.getCart(cartId)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (cart) => {
          this.loading.set(false);
          this.cart.set(cart);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          if (
            !mutationError &&
            error instanceof HttpErrorResponse &&
            [404, 410].includes(error.status)
          ) {
            this.cartSession.clear();
            this.startNewCart();
            return;
          }

          const loadError =
            'Failed to load cart. Retry without replacing the existing cart.';
          this.errorMessage.set(
            mutationError ? `${mutationError} ${loadError}` : loadError
          );
        },
      });
  }

  private persistAndShow(cart: CartModel): void {
    this.cartSession.remember(cart.cartId);
    this.cart.set(cart);
  }
}
