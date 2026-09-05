import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import { StockLevelModel } from '@app/inventory/stock-level.model';

@Injectable({ providedIn: 'root' })
export class InventoryService {
  private readonly httpClient = inject(HttpClient);

  getStockLevel(sku: string): Observable<StockLevelModel> {
    return this.httpClient.get<StockLevelModel>(
      `${environment.apiPrefix}/inventory/${sku}`
    );
  }

  receiveStock(sku: string, quantity: number): Observable<StockLevelModel> {
    return this.adjust(sku, 'receive', quantity);
  }

  reserveStock(sku: string, quantity: number): Observable<StockLevelModel> {
    return this.adjust(sku, 'reserve', quantity);
  }

  releaseStock(sku: string, quantity: number): Observable<StockLevelModel> {
    return this.adjust(sku, 'release', quantity);
  }

  fulfillStock(sku: string, quantity: number): Observable<StockLevelModel> {
    return this.adjust(sku, 'fulfill', quantity);
  }

  private adjust(
    sku: string,
    action: 'receive' | 'reserve' | 'release' | 'fulfill',
    quantity: number
  ): Observable<StockLevelModel> {
    return this.httpClient.post<StockLevelModel>(
      `${environment.apiPrefix}/inventory/${sku}/${action}`,
      { quantity }
    );
  }
}
