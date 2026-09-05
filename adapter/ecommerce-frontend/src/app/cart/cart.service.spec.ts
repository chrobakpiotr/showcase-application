import { TestBed } from '@angular/core/testing';

import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import {
  provideHttpClient,
  withInterceptorsFromDi,
  withXhr,
} from '@angular/common/http';

import { CartModel } from '@app/cart/cart.model';
import { CartService } from '@app/cart/cart.service';
import { environment } from '@environments/environment';

describe('CartService', () => {
  let cartService: CartService;
  let httpTestingController: HttpTestingController;

  const cart: CartModel = {
    cartId: 'cart-1',
    items: [],
    total: 0,
    itemCount: 0,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        CartService,
        provideHttpClient(withXhr(), withInterceptorsFromDi()),
        provideHttpClientTesting(),
      ],
    });
    cartService = TestBed.inject(CartService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should be created', () => {
    expect(cartService).toBeTruthy();
  });

  it('creates a cart', () => {
    cartService.createCart().subscribe((data) => expect(data).toBe(cart));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/cart`
    );
    expect(req.request.method).toBe('POST');
    req.flush(cart);
  });

  it('gets a cart by id', () => {
    cartService.getCart('cart-1').subscribe((data) => expect(data).toBe(cart));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/cart/cart-1`
    );
    expect(req.request.method).toBe('GET');
    req.flush(cart);
  });

  it('adds an item to a cart', () => {
    cartService
      .addItem('cart-1', 'SKU-1', 2)
      .subscribe((data) => expect(data).toBe(cart));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/cart/cart-1/items`
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ sku: 'SKU-1', quantity: 2 });
    req.flush(cart);
  });

  it('updates an item quantity', () => {
    cartService
      .updateItemQuantity('cart-1', 'SKU-1', 5)
      .subscribe((data) => expect(data).toBe(cart));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/cart/cart-1/items/SKU-1`
    );
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ quantity: 5 });
    req.flush(cart);
  });

  it('removes an item', () => {
    cartService
      .removeItem('cart-1', 'SKU-1')
      .subscribe((data) => expect(data).toBe(cart));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/cart/cart-1/items/SKU-1`
    );
    expect(req.request.method).toBe('DELETE');
    req.flush(cart);
  });

  it('clears a cart', () => {
    cartService
      .clearCart('cart-1')
      .subscribe((data) => expect(data).toBe(cart));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/cart/cart-1`
    );
    expect(req.request.method).toBe('DELETE');
    req.flush(cart);
  });
});
