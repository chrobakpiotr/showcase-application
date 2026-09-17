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

import { environment } from '@environments/environment';
import { WishlistModel } from '@app/wishlist/wishlist.model';
import { WishlistService } from '@app/wishlist/wishlist.service';

describe('WishlistService', () => {
  let wishlistService: WishlistService;
  let httpTestingController: HttpTestingController;

  const wishlist: WishlistModel = {
    wishlistId: 'wishlist-1',
    items: [],
    itemCount: 0,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        WishlistService,
        provideHttpClient(withXhr(), withInterceptorsFromDi()),
        provideHttpClientTesting(),
      ],
    });
    wishlistService = TestBed.inject(WishlistService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should be created', () => {
    expect(wishlistService).toBeTruthy();
  });

  it('creates a wishlist', () => {
    wishlistService
      .createWishlist()
      .subscribe((data) => expect(data).toBe(wishlist));
    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/wishlist`
    );
    expect(req.request.method).toBe('POST');
    req.flush(wishlist);
  });

  it('gets a wishlist by id', () => {
    wishlistService
      .getWishlist('wishlist-1')
      .subscribe((data) => expect(data).toBe(wishlist));
    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/wishlist/wishlist-1`
    );
    expect(req.request.method).toBe('GET');
    req.flush(wishlist);
  });

  it('adds an item to a wishlist', () => {
    wishlistService
      .addItem('wishlist-1', 'SKU-1')
      .subscribe((data) => expect(data).toBe(wishlist));
    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/wishlist/wishlist-1/items`
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ sku: 'SKU-1' });
    req.flush(wishlist);
  });

  it('removes an item', () => {
    wishlistService
      .removeItem('wishlist-1', 'SKU-1')
      .subscribe((data) => expect(data).toBe(wishlist));
    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/wishlist/wishlist-1/items/SKU-1`
    );
    expect(req.request.method).toBe('DELETE');
    req.flush(wishlist);
  });

  it('moves an item to cart', () => {
    wishlistService
      .moveToCart('wishlist-1', 'SKU-1', 'cart-1')
      .subscribe((data) => expect(data).toBe(wishlist));
    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/wishlist/wishlist-1/items/SKU-1/move-to-cart`
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ cartId: 'cart-1' });
    req.flush(wishlist);
  });
});
