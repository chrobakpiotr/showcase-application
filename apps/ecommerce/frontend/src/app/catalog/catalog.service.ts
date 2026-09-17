import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import { CategoryModel } from '@app/catalog/category.model';
import { ProductPageModel } from '@app/catalog/product-details.model';

@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly httpClient = inject(HttpClient);

  listCategories(): Observable<CategoryModel[]> {
    return this.httpClient.get<CategoryModel[]>(
      `${environment.apiPrefix}/catalog/categories`
    );
  }

  listProducts(
    page: number,
    size: number,
    category: string | null
  ): Observable<ProductPageModel> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (category) {
      params = params.set('category', category);
    }
    return this.httpClient.get<ProductPageModel>(
      `${environment.apiPrefix}/catalog/products`,
      { params }
    );
  }
}
