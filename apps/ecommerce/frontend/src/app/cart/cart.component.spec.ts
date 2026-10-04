import type { MockedObject } from "vitest";
import { HttpErrorResponse } from "@angular/common/http";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { of, throwError } from "rxjs";

import { CartComponent } from "@app/cart/cart.component";
import { CartModel } from "@app/cart/cart.model";
import { CartService } from "@app/cart/cart.service";
import { asMockedObject } from "../../test-support/mock-object";


const CART_ID_STORAGE_KEY = "ecommerce_cart_id";

describe("CartComponent", () => {
  let fixture: ComponentFixture<CartComponent>;
  let component: CartComponent;
  let cartServiceSpy: MockedObject<CartService>;

  const cart: CartModel = {
    cartId: "cart-1",
    items: [
      {
        sku: "SKU-1",
        productName: "Headphones",
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
    cartServiceSpy = asMockedObject<CartService>({
      createCart: vi.fn().mockName("CartService.createCart"),
      getCart: vi.fn().mockName("CartService.getCart"),
      addItem: vi.fn().mockName("CartService.addItem"),
      updateItemQuantity: vi.fn().mockName("CartService.updateItemQuantity"),
      removeItem: vi.fn().mockName("CartService.removeItem"),
      clearCart: vi.fn().mockName("CartService.clearCart"),
      applyCoupon: vi.fn().mockName("CartService.applyCoupon"),
      removeCoupon: vi.fn().mockName("CartService.removeCoupon"),
    });
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

  it("should create the component", () => {
    setup();
    cartServiceSpy.createCart.mockReturnValue(of(cart));

    fixture.detectChanges();

    expect(component).toBeTruthy();
  });

  it("starts a new cart when nothing is stored", () => {
    setup();
    cartServiceSpy.createCart.mockReturnValue(of(cart));

    fixture.detectChanges();

    expect(component.cart()).toEqual(cart);
    expect(sessionStorage.getItem(CART_ID_STORAGE_KEY)).toBe("cart-1");
  });

  it("sets an error message when starting a new cart fails", () => {
    setup();
    cartServiceSpy.createCart.mockReturnValue(
      throwError(() => new Error("failed")),
    );

    fixture.detectChanges();

    expect(component.errorMessage()).toBe("Failed to start a new cart.");
  });

  it("loads an existing cart from storage", () => {
    sessionStorage.setItem(CART_ID_STORAGE_KEY, "cart-1");
    setup();
    cartServiceSpy.getCart.mockReturnValue(of(cart));

    fixture.detectChanges();

    expect(component.cart()).toEqual(cart);
  });

  it("starts a new cart when the stored cart id no longer exists", () => {
    sessionStorage.setItem(CART_ID_STORAGE_KEY, "stale");
    setup();
    cartServiceSpy.getCart.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );
    cartServiceSpy.createCart.mockReturnValue(of(cart));

    fixture.detectChanges();

    expect(cartServiceSpy.createCart).toHaveBeenCalled();
  });

  it("preserves the stored cart on transient load failures and retries it", () => {
    sessionStorage.setItem(CART_ID_STORAGE_KEY, "cart-1");
    setup();
    cartServiceSpy.getCart.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 503 })),
    );

    fixture.detectChanges();

    expect(cartServiceSpy.createCart).not.toHaveBeenCalled();
    expect(sessionStorage.getItem(CART_ID_STORAGE_KEY)).toBe("cart-1");
    expect(component.errorMessage()).toContain("Retry without replacing");

    cartServiceSpy.getCart.mockReturnValue(of(cart));
    component.retryLoad();

    expect(cartServiceSpy.getCart).toHaveBeenCalledTimes(2);
    expect(component.cart()).toEqual(cart);
  });

  it("does not add an item when the form is invalid", () => {
    setup();
    cartServiceSpy.createCart.mockReturnValue(of(cart));
    fixture.detectChanges();

    component.addItemForm.setValue({ sku: "", quantity: 1 });
    component.addItem();

    expect(cartServiceSpy.addItem).not.toHaveBeenCalled();
  });

  it("does not add an item when cart is unavailable", () => {
    setup();
    component.addItemForm.setValue({ sku: "SKU-1", quantity: 1 });

    component.addItem();

    expect(cartServiceSpy.addItem).not.toHaveBeenCalled();
  });

  it("adds an item to the cart", () => {
    setup();
    cartServiceSpy.createCart.mockReturnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.addItem.mockReturnValue(of(cart));

    component.addItemForm.setValue({ sku: "SKU-1", quantity: 2 });
    component.addItem();

    expect(cartServiceSpy.addItem).toHaveBeenCalledWith("cart-1", "SKU-1", 2);
    expect(component.addItemForm.getRawValue()).toEqual({
      sku: "",
      quantity: 1,
    });
  });

  it("sets an error message when adding an item fails", () => {
    setup();
    cartServiceSpy.createCart.mockReturnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.addItem.mockReturnValue(
      throwError(() => new Error("failed")),
    );

    component.addItemForm.setValue({ sku: "SKU-1", quantity: 2 });
    component.addItem();

    expect(component.errorMessage()).toContain("Failed to add item");
  });

  it("ignores a quantity update below 1", () => {
    setup();
    cartServiceSpy.createCart.mockReturnValue(of(cart));
    fixture.detectChanges();

    component.updateQuantity("SKU-1", 0);

    expect(cartServiceSpy.updateItemQuantity).not.toHaveBeenCalled();
  });

  it("updates an item quantity", () => {
    setup();
    cartServiceSpy.createCart.mockReturnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.updateItemQuantity.mockReturnValue(of(cart));

    component.updateQuantity("SKU-1", 3);

    expect(cartServiceSpy.updateItemQuantity).toHaveBeenCalledWith(
      "cart-1",
      "SKU-1",
      3,
    );
  });

  it("sets an error message when updating quantity fails", () => {
    setup();
    cartServiceSpy.createCart.mockReturnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.updateItemQuantity.mockReturnValue(
      throwError(() => new Error("failed")),
    );

    component.updateQuantity("SKU-1", 3);

    expect(component.errorMessage()).toBe("Failed to update quantity.");
  });

  it("does not remove an item when cart is unavailable", () => {
    setup();

    component.removeItem("SKU-1");

    expect(cartServiceSpy.removeItem).not.toHaveBeenCalled();
  });

  it("removes an item", () => {
    setup();
    cartServiceSpy.createCart.mockReturnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.removeItem.mockReturnValue(of(cart));

    component.removeItem("SKU-1");

    expect(cartServiceSpy.removeItem).toHaveBeenCalledWith("cart-1", "SKU-1");
  });

  it("sets an error message when removing an item fails", () => {
    setup();
    cartServiceSpy.createCart.mockReturnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.removeItem.mockReturnValue(
      throwError(() => new Error("failed")),
    );

    component.removeItem("SKU-1");

    expect(component.errorMessage()).toBe("Failed to remove item.");
  });

  it("does not clear the cart when cart is unavailable", () => {
    setup();

    component.clearCart();

    expect(cartServiceSpy.clearCart).not.toHaveBeenCalled();
  });

  it("clears the cart", () => {
    setup();
    cartServiceSpy.createCart.mockReturnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.clearCart.mockReturnValue(
      of({
        ...cart,
        items: [],
        subtotal: 0,
        total: 0,
        itemCount: 0,
      }),
    );

    component.clearCart();

    expect(cartServiceSpy.clearCart).toHaveBeenCalledWith("cart-1");
  });

  it("sets an error message when clearing the cart fails", () => {
    setup();
    cartServiceSpy.createCart.mockReturnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.clearCart.mockReturnValue(
      throwError(() => new Error("failed")),
    );

    component.clearCart();

    expect(component.errorMessage()).toBe("Failed to clear cart.");
  });

  it("applies a coupon", () => {
    setup();
    cartServiceSpy.createCart.mockReturnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.applyCoupon.mockReturnValue(
      of({ ...cart, couponCode: "SAVE10", discountAmount: 10, total: 189.98 }),
    );

    component.couponForm.setValue({ code: "SAVE10" });
    component.applyCoupon();

    expect(cartServiceSpy.applyCoupon).toHaveBeenCalledWith("cart-1", "SAVE10");
  });

  it("does not apply an invalid coupon form", () => {
    setup();
    cartServiceSpy.createCart.mockReturnValue(of(cart));
    fixture.detectChanges();
    component.couponForm.setValue({ code: "" });

    component.applyCoupon();

    expect(cartServiceSpy.applyCoupon).not.toHaveBeenCalled();
  });

  it("does not apply a coupon when cart is unavailable", () => {
    setup();
    component.couponForm.setValue({ code: "SAVE10" });

    component.applyCoupon();

    expect(cartServiceSpy.applyCoupon).not.toHaveBeenCalled();
  });

  it("sets an error when applying coupon fails", () => {
    setup();
    cartServiceSpy.createCart.mockReturnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.applyCoupon.mockReturnValue(
      throwError(() => new Error("failed")),
    );

    component.couponForm.setValue({ code: "SAVE10" });
    component.applyCoupon();

    expect(component.errorMessage()).toBe("Failed to apply coupon.");
  });

  it("does not remove a coupon when cart is unavailable", () => {
    setup();

    component.removeCoupon();

    expect(cartServiceSpy.removeCoupon).not.toHaveBeenCalled();
  });

  it("removes a coupon", () => {
    setup();
    cartServiceSpy.createCart.mockReturnValue(
      of({ ...cart, couponCode: "SAVE10" }),
    );
    fixture.detectChanges();
    cartServiceSpy.removeCoupon.mockReturnValue(of(cart));

    component.removeCoupon();

    expect(cartServiceSpy.removeCoupon).toHaveBeenCalledWith("cart-1");
  });

  it("sets an error when removing coupon fails", () => {
    setup();
    cartServiceSpy.createCart.mockReturnValue(of(cart));
    fixture.detectChanges();
    cartServiceSpy.removeCoupon.mockReturnValue(
      throwError(() => new Error("failed")),
    );

    component.removeCoupon();

    expect(component.errorMessage()).toBe("Failed to remove coupon.");
  });

  it("preserves the stored cart when loading fails with a non-HTTP error", () => {
    sessionStorage.setItem(CART_ID_STORAGE_KEY, "cart-1");
    setup();
    cartServiceSpy.getCart.mockReturnValue(
      throwError(() => new Error("offline")),
    );

    fixture.detectChanges();

    expect(cartServiceSpy.createCart).not.toHaveBeenCalled();
    expect(sessionStorage.getItem(CART_ID_STORAGE_KEY)).toBe("cart-1");
    expect(component.errorMessage()).toContain("Retry without replacing");
  });

  it("replaces the stored cart when the server reports it gone", () => {
    sessionStorage.setItem(CART_ID_STORAGE_KEY, "gone");
    setup();
    cartServiceSpy.getCart.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 410 })),
    );
    cartServiceSpy.createCart.mockReturnValue(of(cart));

    fixture.detectChanges();

    expect(cartServiceSpy.createCart).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem(CART_ID_STORAGE_KEY)).toBe("cart-1");
  });
});
