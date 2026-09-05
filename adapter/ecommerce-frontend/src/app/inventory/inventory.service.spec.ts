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

import { InventoryService } from '@app/inventory/inventory.service';
import { StockLevelModel } from '@app/inventory/stock-level.model';
import { environment } from '@environments/environment';

describe('InventoryService', () => {
  let inventoryService: InventoryService;
  let httpTestingController: HttpTestingController;

  const stockLevel: StockLevelModel = {
    sku: 'SKU-1',
    quantityOnHand: 100,
    quantityReserved: 15,
    quantityAvailable: 85,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        InventoryService,
        provideHttpClient(withXhr(), withInterceptorsFromDi()),
        provideHttpClientTesting(),
      ],
    });
    inventoryService = TestBed.inject(InventoryService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should be created', () => {
    expect(inventoryService).toBeTruthy();
  });

  it('gets the stock level for a sku', () => {
    inventoryService
      .getStockLevel('SKU-1')
      .subscribe((data) => expect(data).toBe(stockLevel));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/inventory/SKU-1`
    );
    expect(req.request.method).toBe('GET');
    req.flush(stockLevel);
  });

  it('receives stock', () => {
    inventoryService
      .receiveStock('SKU-1', 10)
      .subscribe((data) => expect(data).toBe(stockLevel));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/inventory/SKU-1/receive`
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ quantity: 10 });
    req.flush(stockLevel);
  });

  it('reserves stock', () => {
    inventoryService
      .reserveStock('SKU-1', 5)
      .subscribe((data) => expect(data).toBe(stockLevel));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/inventory/SKU-1/reserve`
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ quantity: 5 });
    req.flush(stockLevel);
  });

  it('releases stock', () => {
    inventoryService
      .releaseStock('SKU-1', 5)
      .subscribe((data) => expect(data).toBe(stockLevel));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/inventory/SKU-1/release`
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ quantity: 5 });
    req.flush(stockLevel);
  });

  it('fulfills stock', () => {
    inventoryService
      .fulfillStock('SKU-1', 5)
      .subscribe((data) => expect(data).toBe(stockLevel));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/inventory/SKU-1/fulfill`
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ quantity: 5 });
    req.flush(stockLevel);
  });
});
