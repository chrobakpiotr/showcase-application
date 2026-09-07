import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { CartComponent } from '@app/cart/cart.component';
import { CartModel } from '@app/cart/cart.model';
import { CartService } from '@app/cart/cart.service';

const CART_ID_STORAGE_KEY = 'ecommerce_cart_id';

describe('CartComponent', () => {
  let fixture: ComponentFixture<CartComponent>;
  let component: CartComponent;
  let cartServiceSpy: jasmine.SpyObj<CartService>;

  const cart: CartModel = {
    cartId: 'cart-1',
    items: [
      {
        sku: 'SKU-1',
        productName: 'Headphones',
        unitPrice: 99.99,
        quantity: 2,
        subtotal: 199.98,
      },
    ],
    subtotal: 199.98,
    couponCode: null,
    discountAmount: 0,
    total: 199.98,
    itemCount: 2,
  };

  function setup(): void {
    cartServiceSpy = jasmine.createSpyObj('CartService', [
      'createCart',
      'getCart',
      'addItem',
      'updateItemQuantity',
      'removeItem',
      'clearCart',
      'applyCoupon',
      'removeCoupon',
    ]);
    TestBed.configureTestingModule({
      imports: [CartComponent],
      providers: [{ provide: CartService, useValue: cartServiceSpy }],
    });
    fixture = TestBed.createComponent(CartComponent);
    component = fixture.componentInstance;
  }

  beforeEach(() => {
    sessionStorage.removeItem(CART_ID_STORAGE_KEY);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    sessionStorage.removeItem(CART_ID_STORAGE_KEY);
  });

  it('should create the component', () => {
    setup();
    cartServiceSpy.createCart.and.returnValue(of(cart));

    fixture.detectChanges();

    expect(component).toBeTruthy();
  });

  it('starts a new cart when nothing is stored', () => {
    setup();
    cartServiceSpy.createCart.and.returnValue(of(cart));

    fixture.detectChanges();

    expect(component.cart()).toEqual(cart);
    expect(sessionStorage.getItem(CART_ID_STORAGE_KEY)).toBe('cart-1');
  });

  it('sets an error message when starting a new cart fails', () => {
    setup();
    cartServiceSpy.createCart.and.returnValue(
      throwError(() => new Error('failed'))
    );

    fixture.detectChanges();

    expect(component.errorMessage()).toBe('Failed to start a new cart.');
  });

  it('loads an existing cart from storage', () => {
    sessionStorage.setItem(CART_ID_STORAGE_KEY, 'cart-1');
    setup();
    cartServiceSpy.getCart.and.returnValue(of(cart));

    fixture.detectChanges();

    expect(component.cart()).toEqual(cart);
  });

  it('starts a new cart when the stored cart id no longer exists', () => {
    sessionStorage.setItem(CART_ID_STORAGE_KEY, 'stale');
    setup();
    cartServiceSpy.getCart.and.returnValue(
      throwError(() => new Error('not found'))
    );
    cartServiceSpy.createCart.and.returnValue(of(cart));

    fixture.detectChanges();

    expect(cartServiceSpy.createCart).toHaveBeenCalled();
  });

  it('does not add an item when the form is invalid', () => {
    setup();
    cartServiceSpy.createCart.and.returnValue(of(cart));
    fixture.detectChanges();

    component.addItemForm.setValue({ sku: '', quantity: 1 });
    component.addItem();

    expect(cartServiceSpy.addItem).not.toHaveBeenCalled();
  });

  it('does not add an item when cart is unavailable', () => {
    setup();
    component.addItemForm.setValue({ sku: 'SKU-1', quantity: 1 });

    component.addItem();

    expect(cartServiceSpy.addItem).not.toHaveBeenCalled();
  });

  it('adds an item to the cart', () => {
    setup();
    cartServiceSpy.createCart.and.returnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.addItem.and.returnValue(of(cart));

    component.addItemForm.setValue({ sku: 'SKU-1', quantity: 2 });
    component.addItem();

    expect(cartServiceSpy.addItem).toHaveBeenCalledWith('cart-1', 'SKU-1', 2);
    expect(component.addItemForm.getRawValue()).toEqual({
      sku: '',
      quantity: 1,
    });
  });

  it('sets an error message when adding an item fails', () => {
    setup();
    cartServiceSpy.createCart.and.returnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.addItem.and.returnValue(
      throwError(() => new Error('failed'))
    );

    component.addItemForm.setValue({ sku: 'SKU-1', quantity: 2 });
    component.addItem();

    expect(component.errorMessage()).toContain('Failed to add item');
  });

  it('ignores a quantity update below 1', () => {
    setup();
    cartServiceSpy.createCart.and.returnValue(of(cart));
    fixture.detectChanges();

    component.updateQuantity('SKU-1', 0);

    expect(cartServiceSpy.updateItemQuantity).not.toHaveBeenCalled();
  });

  it('updates an item quantity', () => {
    setup();
    cartServiceSpy.createCart.and.returnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.updateItemQuantity.and.returnValue(of(cart));

    component.updateQuantity('SKU-1', 3);

    expect(cartServiceSpy.updateItemQuantity).toHaveBeenCalledWith(
      'cart-1',
      'SKU-1',
      3
    );
  });

  it('sets an error message when updating quantity fails', () => {
    setup();
    cartServiceSpy.createCart.and.returnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.updateItemQuantity.and.returnValue(
      throwError(() => new Error('failed'))
    );

    component.updateQuantity('SKU-1', 3);

    expect(component.errorMessage()).toBe('Failed to update quantity.');
  });

  it('does not remove an item when cart is unavailable', () => {
    setup();

    component.removeItem('SKU-1');

    expect(cartServiceSpy.removeItem).not.toHaveBeenCalled();
  });

  it('removes an item', () => {
    setup();
    cartServiceSpy.createCart.and.returnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.removeItem.and.returnValue(of(cart));

    component.removeItem('SKU-1');

    expect(cartServiceSpy.removeItem).toHaveBeenCalledWith('cart-1', 'SKU-1');
  });

  it('sets an error message when removing an item fails', () => {
    setup();
    cartServiceSpy.createCart.and.returnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.removeItem.and.returnValue(
      throwError(() => new Error('failed'))
    );

    component.removeItem('SKU-1');

    expect(component.errorMessage()).toBe('Failed to remove item.');
  });

  it('does not clear the cart when cart is unavailable', () => {
    setup();

    component.clearCart();

    expect(cartServiceSpy.clearCart).not.toHaveBeenCalled();
  });

  it('clears the cart', () => {
    setup();
    cartServiceSpy.createCart.and.returnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.clearCart.and.returnValue(
      of({
        ...cart,
        items: [],
        subtotal: 0,
        total: 0,
        itemCount: 0,
      })
    );

    component.clearCart();

    expect(cartServiceSpy.clearCart).toHaveBeenCalledWith('cart-1');
  });

  it('sets an error message when clearing the cart fails', () => {
    setup();
    cartServiceSpy.createCart.and.returnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.clearCart.and.returnValue(
      throwError(() => new Error('failed'))
    );

    component.clearCart();

    expect(component.errorMessage()).toBe('Failed to clear cart.');
  });

  it('applies a coupon', () => {
    setup();
    cartServiceSpy.createCart.and.returnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.applyCoupon.and.returnValue(
      of({ ...cart, couponCode: 'SAVE10', discountAmount: 10, total: 189.98 })
    );

    component.couponForm.setValue({ code: 'SAVE10' });
    component.applyCoupon();

    expect(cartServiceSpy.applyCoupon).toHaveBeenCalledWith('cart-1', 'SAVE10');
  });

  it('does not apply an invalid coupon form', () => {
    setup();
    cartServiceSpy.createCart.and.returnValue(of(cart));
    fixture.detectChanges();
    component.couponForm.setValue({ code: '' });

    component.applyCoupon();

    expect(cartServiceSpy.applyCoupon).not.toHaveBeenCalled();
  });

  it('does not apply a coupon when cart is unavailable', () => {
    setup();
    component.couponForm.setValue({ code: 'SAVE10' });

    component.applyCoupon();

    expect(cartServiceSpy.applyCoupon).not.toHaveBeenCalled();
  });

  it('sets an error when applying coupon fails', () => {
    setup();
    cartServiceSpy.createCart.and.returnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.applyCoupon.and.returnValue(
      throwError(() => new Error('failed'))
    );

    component.couponForm.setValue({ code: 'SAVE10' });
    component.applyCoupon();

    expect(component.errorMessage()).toBe('Failed to apply coupon.');
  });

  it('does not remove a coupon when cart is unavailable', () => {
    setup();

    component.removeCoupon();

    expect(cartServiceSpy.removeCoupon).not.toHaveBeenCalled();
  });

  it('removes a coupon', () => {
    setup();
    cartServiceSpy.createCart.and.returnValue(
      of({ ...cart, couponCode: 'SAVE10' })
    );
    fixture.detectChanges();
    cartServiceSpy.removeCoupon.and.returnValue(of(cart));

    component.removeCoupon();

    expect(cartServiceSpy.removeCoupon).toHaveBeenCalledWith('cart-1');
  });

  it('sets an error when removing coupon fails', () => {
    setup();
    cartServiceSpy.createCart.and.returnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.removeCoupon.and.returnValue(
      throwError(() => new Error('failed'))
    );

    component.removeCoupon();

    expect(component.errorMessage()).toBe('Failed to remove coupon.');
  });
});
