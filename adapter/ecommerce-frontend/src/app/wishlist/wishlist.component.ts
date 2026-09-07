import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';

import { CartService } from '@app/cart/cart.service';
import { WishlistModel } from '@app/wishlist/wishlist.model';
import { WishlistService } from '@app/wishlist/wishlist.service';

const WISHLIST_ID_STORAGE_KEY = 'ecommerce_wishlist_id';
const CART_ID_STORAGE_KEY = 'ecommerce_cart_id';

@Component({
  selector: 'app-wishlist',
  templateUrl: './wishlist.component.html',
  styleUrls: ['./wishlist.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, ReactiveFormsModule],
})
export class WishlistComponent implements OnInit {
  private readonly wishlistService = inject(WishlistService);
  private readonly cartService = inject(CartService);

  readonly wishlist = signal<WishlistModel | null>(null);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly addItemForm = new FormGroup({
    sku: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  ngOnInit(): void {
    const storedWishlistId = sessionStorage.getItem(WISHLIST_ID_STORAGE_KEY);
    if (storedWishlistId) {
      this.loadWishlist(storedWishlistId);
    } else {
      this.startNewWishlist();
    }
  }

  startNewWishlist(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.wishlistService.createWishlist().subscribe({
      next: (wishlist) => {
        this.loading.set(false);
        this.persistAndShow(wishlist);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Failed to start a new wishlist.');
      },
    });
  }

  addItem(): void {
    if (this.addItemForm.invalid || !this.wishlist()) return;
    const { sku } = this.addItemForm.getRawValue();
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.wishlistService.addItem(this.wishlist()!.wishlistId, sku).subscribe({
      next: (wishlist) => {
        this.wishlist.set(wishlist);
        this.addItemForm.reset({ sku: '' });
      },
      error: () =>
        this.errorMessage.set(
          'Failed to add item - check the SKU exists in the catalog.'
        ),
    });
  }

  removeItem(sku: string): void {
    if (!this.wishlist()) return;
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.wishlistService
      .removeItem(this.wishlist()!.wishlistId, sku)
      .subscribe({
        next: (wishlist) => this.wishlist.set(wishlist),
        error: () => this.errorMessage.set('Failed to remove item.'),
      });
  }

  moveToCart(sku: string): void {
    if (!this.wishlist()) return;
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.ensureCartId((cartId) => {
      this.wishlistService
        .moveToCart(this.wishlist()!.wishlistId, sku, cartId)
        .subscribe({
          next: (wishlist) => {
            this.wishlist.set(wishlist);
            this.successMessage.set('Item moved to cart.');
          },
          error: () => this.errorMessage.set('Failed to move item to cart.'),
        });
    });
  }

  private loadWishlist(wishlistId: string): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.wishlistService.getWishlist(wishlistId).subscribe({
      next: (wishlist) => {
        this.loading.set(false);
        this.wishlist.set(wishlist);
      },
      error: () => {
        this.loading.set(false);
        this.startNewWishlist();
      },
    });
  }

  private ensureCartId(consumer: (cartId: string) => void): void {
    const storedCartId = sessionStorage.getItem(CART_ID_STORAGE_KEY);
    if (storedCartId) {
      consumer(storedCartId);
      return;
    }
    this.cartService.createCart().subscribe({
      next: (cart) => {
        sessionStorage.setItem(CART_ID_STORAGE_KEY, cart.cartId);
        consumer(cart.cartId);
      },
      error: () =>
        this.errorMessage.set('Failed to start a cart for moving the item.'),
    });
  }

  private persistAndShow(wishlist: WishlistModel): void {
    sessionStorage.setItem(WISHLIST_ID_STORAGE_KEY, wishlist.wishlistId);
    this.wishlist.set(wishlist);
  }
}
