import type { MockedObject } from "vitest";
import { TestBed } from "@angular/core/testing";
import { of } from "rxjs";

import {
  CART_ID_STORAGE_KEY,
  CartSessionService,
} from "@app/cart/cart-session.service";
import { CartService } from "@app/cart/cart.service";
import { asMockedObject } from "../../test-support/mock-object";


describe("CartSessionService", () => {
  let service: CartSessionService;
  let cartServiceSpy: MockedObject<CartService>;

  beforeEach(() => {
    sessionStorage.removeItem(CART_ID_STORAGE_KEY);
    cartServiceSpy = asMockedObject<CartService>({
      createCart: vi.fn().mockName("CartService.createCart"),
    });
    TestBed.configureTestingModule({
      providers: [{ provide: CartService, useValue: cartServiceSpy }],
    });
    service = TestBed.inject(CartSessionService);
  });

  afterEach(() => {
    sessionStorage.removeItem(CART_ID_STORAGE_KEY);
    TestBed.resetTestingModule();
  });

  it("reuses an existing session cart without creating a replacement", () => {
    sessionStorage.setItem(CART_ID_STORAGE_KEY, "cart-existing");

    service.ensureCartId().subscribe((cartId) => {
      expect(cartId).toBe("cart-existing");
    });

    expect(cartServiceSpy.createCart).not.toHaveBeenCalled();
  });

  it("creates and remembers a cart when the session has none", () => {
    cartServiceSpy.createCart.mockReturnValue(
      of({
        cartId: "cart-new",
        items: [],
        subtotal: 0,
        couponCode: null,
        discountAmount: 0,
        total: 0,
        itemCount: 0,
      }),
    );

    service.ensureCartId().subscribe((cartId) => {
      expect(cartId).toBe("cart-new");
    });

    expect(sessionStorage.getItem(CART_ID_STORAGE_KEY)).toBe("cart-new");
  });

  it("clears a stale cart id explicitly", () => {
    sessionStorage.setItem(CART_ID_STORAGE_KEY, "cart-stale");

    service.clear();

    expect(service.getCartId()).toBeNull();
  });
});
