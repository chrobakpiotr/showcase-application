import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import { CartModel } from '@app/cart/cart.model';

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly httpClient = inject(HttpClient);

  createCart(): Observable<CartModel> {
    return this.httpClient.post<CartModel>(`${environment.apiPrefix}/cart`, {});
  }

  getCart(cartId: string): Observable<CartModel> {
    return this.httpClient.get<CartModel>(
      `${environment.apiPrefix}/cart/${cartId}`
    );
  }

  addItem(
    cartId: string,
    sku: string,
    quantity: number
  ): Observable<CartModel> {
    return this.httpClient.post<CartModel>(
      `${environment.apiPrefix}/cart/${cartId}/items`,
      { sku, quantity }
    );
  }

  updateItemQuantity(
    cartId: string,
    sku: string,
    quantity: number
  ): Observable<CartModel> {
    return this.httpClient.put<CartModel>(
      `${environment.apiPrefix}/cart/${cartId}/items/${sku}`,
      { quantity }
    );
  }

  removeItem(cartId: string, sku: string): Observable<CartModel> {
    return this.httpClient.delete<CartModel>(
      `${environment.apiPrefix}/cart/${cartId}/items/${sku}`
    );
  }

  clearCart(cartId: string): Observable<CartModel> {
    return this.httpClient.delete<CartModel>(
      `${environment.apiPrefix}/cart/${cartId}`
    );
  }
}
