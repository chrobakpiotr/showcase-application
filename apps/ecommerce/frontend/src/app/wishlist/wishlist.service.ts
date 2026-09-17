import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import { WishlistModel } from '@app/wishlist/wishlist.model';

@Injectable({ providedIn: 'root' })
export class WishlistService {
  private readonly httpClient = inject(HttpClient);

  createWishlist(): Observable<WishlistModel> {
    return this.httpClient.post<WishlistModel>(
      `${environment.apiPrefix}/wishlist`,
      {}
    );
  }

  getWishlist(wishlistId: string): Observable<WishlistModel> {
    return this.httpClient.get<WishlistModel>(
      `${environment.apiPrefix}/wishlist/${wishlistId}`
    );
  }

  addItem(wishlistId: string, sku: string): Observable<WishlistModel> {
    return this.httpClient.post<WishlistModel>(
      `${environment.apiPrefix}/wishlist/${wishlistId}/items`,
      { sku }
    );
  }

  removeItem(wishlistId: string, sku: string): Observable<WishlistModel> {
    return this.httpClient.delete<WishlistModel>(
      `${environment.apiPrefix}/wishlist/${wishlistId}/items/${sku}`
    );
  }

  moveToCart(
    wishlistId: string,
    sku: string,
    cartId: string
  ): Observable<WishlistModel> {
    return this.httpClient.post<WishlistModel>(
      `${environment.apiPrefix}/wishlist/${wishlistId}/items/${sku}/move-to-cart`,
      { cartId }
    );
  }
}
