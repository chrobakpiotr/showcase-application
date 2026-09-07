import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { CartService } from '@app/cart/cart.service';
import { WishlistComponent } from '@app/wishlist/wishlist.component';
import { WishlistModel } from '@app/wishlist/wishlist.model';
import { WishlistService } from '@app/wishlist/wishlist.service';

const WISHLIST_ID_STORAGE_KEY = 'ecommerce_wishlist_id';
const CART_ID_STORAGE_KEY = 'ecommerce_cart_id';

describe('WishlistComponent', () => {
  let fixture: ComponentFixture<WishlistComponent>;
  let component: WishlistComponent;
  let wishlistServiceSpy: jasmine.SpyObj<WishlistService>;
  let cartServiceSpy: jasmine.SpyObj<CartService>;

  const wishlist: WishlistModel = {
    wishlistId: 'wishlist-1',
    items: [
      {
        sku: 'SKU-1',
        productName: 'Headphones',
        addedDate: '2026-09-07T12:00:00.000Z',
      },
    ],
    itemCount: 1,
  };

  function setup(): void {
    wishlistServiceSpy = jasmine.createSpyObj('WishlistService', [
      'createWishlist',
      'getWishlist',
      'addItem',
      'removeItem',
      'moveToCart',
    ]);
    cartServiceSpy = jasmine.createSpyObj('CartService', ['createCart']);
    TestBed.configureTestingModule({
      imports: [WishlistComponent],
      providers: [
        { provide: WishlistService, useValue: wishlistServiceSpy },
        { provide: CartService, useValue: cartServiceSpy },
      ],
    });
    fixture = TestBed.createComponent(WishlistComponent);
    component = fixture.componentInstance;
  }

  beforeEach(() => {
    sessionStorage.removeItem(WISHLIST_ID_STORAGE_KEY);
    sessionStorage.removeItem(CART_ID_STORAGE_KEY);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    sessionStorage.removeItem(WISHLIST_ID_STORAGE_KEY);
    sessionStorage.removeItem(CART_ID_STORAGE_KEY);
  });

  it('should create the component', () => {
    setup();
    wishlistServiceSpy.createWishlist.and.returnValue(of(wishlist));

    fixture.detectChanges();

    expect(component).toBeTruthy();
  });

  it('starts a new wishlist when nothing is stored', () => {
    setup();
    wishlistServiceSpy.createWishlist.and.returnValue(of(wishlist));

    fixture.detectChanges();

    expect(component.wishlist()).toEqual(wishlist);
    expect(sessionStorage.getItem(WISHLIST_ID_STORAGE_KEY)).toBe('wishlist-1');
  });

  it('sets an error message when starting a new wishlist fails', () => {
    setup();
    wishlistServiceSpy.createWishlist.and.returnValue(
      throwError(() => new Error('failed'))
    );

    fixture.detectChanges();

    expect(component.errorMessage()).toBe('Failed to start a new wishlist.');
  });

  it('loads an existing wishlist from storage', () => {
    sessionStorage.setItem(WISHLIST_ID_STORAGE_KEY, 'wishlist-1');
    setup();
    wishlistServiceSpy.getWishlist.and.returnValue(of(wishlist));

    fixture.detectChanges();

    expect(component.wishlist()).toEqual(wishlist);
  });

  it('starts a new wishlist when the stored wishlist id no longer exists', () => {
    sessionStorage.setItem(WISHLIST_ID_STORAGE_KEY, 'stale');
    setup();
    wishlistServiceSpy.getWishlist.and.returnValue(
      throwError(() => new Error('not found'))
    );
    wishlistServiceSpy.createWishlist.and.returnValue(of(wishlist));

    fixture.detectChanges();

    expect(wishlistServiceSpy.createWishlist).toHaveBeenCalled();
  });

  it('does not add an item when the form is invalid', () => {
    setup();
    wishlistServiceSpy.createWishlist.and.returnValue(of(wishlist));
    fixture.detectChanges();

    component.addItemForm.setValue({ sku: '' });
    component.addItem();

    expect(wishlistServiceSpy.addItem).not.toHaveBeenCalled();
  });

  it('does not add an item when wishlist is unavailable', () => {
    setup();
    component.addItemForm.setValue({ sku: 'SKU-1' });

    component.addItem();

    expect(wishlistServiceSpy.addItem).not.toHaveBeenCalled();
  });

  it('adds an item to the wishlist', () => {
    setup();
    wishlistServiceSpy.createWishlist.and.returnValue(of(wishlist));
    fixture.detectChanges();
    wishlistServiceSpy.addItem.and.returnValue(of(wishlist));

    component.addItemForm.setValue({ sku: 'SKU-1' });
    component.addItem();

    expect(wishlistServiceSpy.addItem).toHaveBeenCalledWith(
      'wishlist-1',
      'SKU-1'
    );
    expect(component.addItemForm.getRawValue()).toEqual({ sku: '' });
  });

  it('sets an error message when adding an item fails', () => {
    setup();
    wishlistServiceSpy.createWishlist.and.returnValue(of(wishlist));
    fixture.detectChanges();
    wishlistServiceSpy.addItem.and.returnValue(
      throwError(() => new Error('failed'))
    );

    component.addItemForm.setValue({ sku: 'SKU-1' });
    component.addItem();

    expect(component.errorMessage()).toContain('Failed to add item');
  });

  it('does not remove an item when wishlist is unavailable', () => {
    setup();

    component.removeItem('SKU-1');

    expect(wishlistServiceSpy.removeItem).not.toHaveBeenCalled();
  });

  it('removes an item', () => {
    setup();
    wishlistServiceSpy.createWishlist.and.returnValue(of(wishlist));
    fixture.detectChanges();
    wishlistServiceSpy.removeItem.and.returnValue(of(wishlist));

    component.removeItem('SKU-1');

    expect(wishlistServiceSpy.removeItem).toHaveBeenCalledWith(
      'wishlist-1',
      'SKU-1'
    );
  });

  it('sets an error message when removing an item fails', () => {
    setup();
    wishlistServiceSpy.createWishlist.and.returnValue(of(wishlist));
    fixture.detectChanges();
    wishlistServiceSpy.removeItem.and.returnValue(
      throwError(() => new Error('failed'))
    );

    component.removeItem('SKU-1');

    expect(component.errorMessage()).toBe('Failed to remove item.');
  });

  it('does not move an item when wishlist is unavailable', () => {
    setup();

    component.moveToCart('SKU-1');

    expect(wishlistServiceSpy.moveToCart).not.toHaveBeenCalled();
  });

  it('moves an item to an existing cart', () => {
    setup();
    sessionStorage.setItem(CART_ID_STORAGE_KEY, 'cart-1');
    wishlistServiceSpy.createWishlist.and.returnValue(of(wishlist));
    fixture.detectChanges();
    wishlistServiceSpy.moveToCart.and.returnValue(of(wishlist));

    component.moveToCart('SKU-1');

    expect(wishlistServiceSpy.moveToCart).toHaveBeenCalledWith(
      'wishlist-1',
      'SKU-1',
      'cart-1'
    );
    expect(component.successMessage()).toBe('Item moved to cart.');
  });

  it('creates a cart before moving when none is stored', () => {
    setup();
    wishlistServiceSpy.createWishlist.and.returnValue(of(wishlist));
    cartServiceSpy.createCart.and.returnValue(
      of({
        cartId: 'cart-1',
        items: [],
        subtotal: 0,
        couponCode: null,
        discountAmount: 0,
        total: 0,
        itemCount: 0,
      })
    );
    wishlistServiceSpy.moveToCart.and.returnValue(of(wishlist));
    fixture.detectChanges();

    component.moveToCart('SKU-1');

    expect(cartServiceSpy.createCart).toHaveBeenCalled();
    expect(sessionStorage.getItem(CART_ID_STORAGE_KEY)).toBe('cart-1');
    expect(wishlistServiceSpy.moveToCart).toHaveBeenCalledWith(
      'wishlist-1',
      'SKU-1',
      'cart-1'
    );
  });

  it('sets an error when creating a cart for move fails', () => {
    setup();
    wishlistServiceSpy.createWishlist.and.returnValue(of(wishlist));
    cartServiceSpy.createCart.and.returnValue(
      throwError(() => new Error('failed'))
    );
    fixture.detectChanges();

    component.moveToCart('SKU-1');

    expect(component.errorMessage()).toBe(
      'Failed to start a cart for moving the item.'
    );
  });

  it('sets an error when moving to cart fails', () => {
    setup();
    sessionStorage.setItem(CART_ID_STORAGE_KEY, 'cart-1');
    wishlistServiceSpy.createWishlist.and.returnValue(of(wishlist));
    fixture.detectChanges();
    wishlistServiceSpy.moveToCart.and.returnValue(
      throwError(() => new Error('failed'))
    );

    component.moveToCart('SKU-1');

    expect(component.errorMessage()).toBe('Failed to move item to cart.');
  });
});
