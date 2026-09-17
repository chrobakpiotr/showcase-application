import {
  provideRouter,
  ActivatedRoute,
  convertToParamMap,
} from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Subject, of, throwError } from 'rxjs';

import { AuthService } from '@app/auth/auth.service';
import { InventoryComponent } from '@app/inventory/inventory.component';
import { InventoryService } from '@app/inventory/inventory.service';
import { StockLevelModel } from '@app/inventory/stock-level.model';

describe('InventoryComponent', () => {
  let fixture: ComponentFixture<InventoryComponent>;
  let component: InventoryComponent;
  let inventoryServiceSpy: jasmine.SpyObj<InventoryService>;
  let authServiceStub: { roles: ReturnType<typeof signal<string[]>> };

  const stockLevel: StockLevelModel = {
    sku: 'SKU-1',
    quantityOnHand: 100,
    quantityReserved: 15,
    quantityAvailable: 85,
  };

  function setup(roles: string[] = [], sku: string | null = null): void {
    inventoryServiceSpy = jasmine.createSpyObj('InventoryService', [
      'getStockLevel',
      'receiveStock',
      'reserveStock',
      'releaseStock',
      'fulfillStock',
    ]);
    authServiceStub = { roles: signal(roles) };

    TestBed.configureTestingModule({
      imports: [InventoryComponent],
      providers: [
        { provide: InventoryService, useValue: inventoryServiceSpy },
        { provide: AuthService, useValue: authServiceStub },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap(sku === null ? {} : { sku }),
            },
          },
        },
      ],
    });

    fixture = TestBed.createComponent(InventoryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() =>
    TestBed.configureTestingModule({ providers: [provideRouter([])] })
  );

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should create the component', () => {
    setup();
    expect(component).toBeTruthy();
  });

  it('does not look up when the form is invalid', () => {
    setup();
    component.lookupForm.setValue({ sku: '' });
    component.lookup();
    expect(inventoryServiceSpy.getStockLevel).not.toHaveBeenCalled();
  });

  it('looks up a stock level', () => {
    setup();
    inventoryServiceSpy.getStockLevel.and.returnValue(of(stockLevel));
    component.lookupForm.setValue({ sku: 'SKU-1' });
    component.lookup();

    expect(inventoryServiceSpy.getStockLevel).toHaveBeenCalledWith('SKU-1');
    expect(component.stockLevel()).toEqual(stockLevel);
    expect(component.loading()).toBeFalse();
  });

  it('sets an error message when the lookup fails', () => {
    setup();
    inventoryServiceSpy.getStockLevel.and.returnValue(
      throwError(() => new Error('failed'))
    );
    component.lookupForm.setValue({ sku: 'SKU-1' });
    component.lookup();

    expect(component.errorMessage()).toBe('Failed to load stock level.');
    expect(component.loading()).toBeFalse();
  });

  it('reports canWrite false without the INVENTORY_WRITE role', () => {
    setup([]);
    expect(component.canWrite).toBeFalse();
  });

  it('reports canWrite true with the INVENTORY_WRITE role', () => {
    setup(['INVENTORY_WRITE']);
    expect(component.canWrite).toBeTrue();
  });

  it('does not adjust stock without a loaded stock level', () => {
    setup();
    component.adjust('receive');
    expect(inventoryServiceSpy.receiveStock).not.toHaveBeenCalled();
  });

  it('does not adjust stock when the adjustment form is invalid', () => {
    setup();
    inventoryServiceSpy.getStockLevel.and.returnValue(of(stockLevel));
    component.lookupForm.setValue({ sku: 'SKU-1' });
    component.lookup();
    component.adjustmentForm.setValue({ quantity: null });

    component.adjust('receive');

    expect(inventoryServiceSpy.receiveStock).not.toHaveBeenCalled();
  });

  it('serializes lookups and clears old stock even when the next lookup fails', () => {
    setup(['INVENTORY_WRITE']);
    component.stockLevel.set(stockLevel);
    const response = new Subject<StockLevelModel>();
    inventoryServiceSpy.getStockLevel.and.returnValue(response);
    component.lookupForm.setValue({ sku: 'SKU-2' });
    component.lookup();
    expect(component.stockLevel()).toBeNull();
    component.lookup();
    component.adjust('receive');
    expect(inventoryServiceSpy.getStockLevel).toHaveBeenCalledTimes(1);
    expect(inventoryServiceSpy.receiveStock).not.toHaveBeenCalled();
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('[data-testid="lookup"]').disabled
    ).toBeTrue();
    response.error(new Error('missing SKU'));
    expect(component.loading()).toBeFalse();
    expect(component.stockLevel()).toBeNull();
    inventoryServiceSpy.getStockLevel.and.returnValue(of(stockLevel));
    component.lookup();
    expect(component.stockLevel()).toEqual(stockLevel);
  });

  it('blocks duplicate adjustments and lookups until the adjustment succeeds', () => {
    setup(['INVENTORY_WRITE']);
    component.stockLevel.set(stockLevel);
    component.lookupForm.setValue({ sku: 'SKU-2' });
    const response = new Subject<StockLevelModel>();
    inventoryServiceSpy.receiveStock.and.returnValue(response);
    component.adjust('receive');
    component.adjust('receive');
    component.lookup();
    expect(inventoryServiceSpy.receiveStock).toHaveBeenCalledTimes(1);
    expect(inventoryServiceSpy.getStockLevel).not.toHaveBeenCalled();
    expect(inventoryServiceSpy.reserveStock).not.toHaveBeenCalled();
    expect(inventoryServiceSpy.releaseStock).not.toHaveBeenCalled();
    expect(inventoryServiceSpy.fulfillStock).not.toHaveBeenCalled();
    fixture.detectChanges();
    for (const action of [
      'receive',
      'reserve',
      'release',
      'fulfill',
      'lookup',
    ]) {
      expect(
        fixture.nativeElement.querySelector(`[data-testid="${action}"]`)
          .disabled
      ).toBeTrue();
    }
    response.next(stockLevel);
    response.complete();
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('[data-testid="receive"]').disabled
    ).toBeFalse();
    inventoryServiceSpy.receiveStock.and.returnValue(of(stockLevel));
    component.adjust('receive');
    expect(inventoryServiceSpy.receiveStock).toHaveBeenCalledTimes(2);
  });

  it('unlocks adjustments after a failure without retrying automatically', () => {
    setup(['INVENTORY_WRITE']);
    component.stockLevel.set(stockLevel);
    const response = new Subject<StockLevelModel>();
    inventoryServiceSpy.receiveStock.and.returnValue(response);
    component.adjust('receive');
    response.error(new Error('failed'));
    expect(inventoryServiceSpy.receiveStock).toHaveBeenCalledTimes(1);
    inventoryServiceSpy.receiveStock.and.returnValue(of(stockLevel));
    component.adjust('receive');
    expect(inventoryServiceSpy.receiveStock).toHaveBeenCalledTimes(2);
    expect(component.errorMessage()).toBeNull();
  });

  it('receives stock', () => {
    setup(['INVENTORY_WRITE']);
    inventoryServiceSpy.getStockLevel.and.returnValue(of(stockLevel));
    component.lookupForm.setValue({ sku: 'SKU-1' });
    component.lookup();

    const updated = { ...stockLevel, quantityOnHand: 110 };
    inventoryServiceSpy.receiveStock.and.returnValue(of(updated));
    component.adjust('receive');

    expect(inventoryServiceSpy.receiveStock).toHaveBeenCalledWith('SKU-1', 1);
    expect(component.stockLevel()).toEqual(updated);
  });

  it('reserves stock', () => {
    setup(['INVENTORY_WRITE']);
    inventoryServiceSpy.getStockLevel.and.returnValue(of(stockLevel));
    component.lookupForm.setValue({ sku: 'SKU-1' });
    component.lookup();

    inventoryServiceSpy.reserveStock.and.returnValue(of(stockLevel));
    component.adjust('reserve');

    expect(inventoryServiceSpy.reserveStock).toHaveBeenCalledWith('SKU-1', 1);
  });

  it('releases stock', () => {
    setup(['INVENTORY_WRITE']);
    inventoryServiceSpy.getStockLevel.and.returnValue(of(stockLevel));
    component.lookupForm.setValue({ sku: 'SKU-1' });
    component.lookup();

    inventoryServiceSpy.releaseStock.and.returnValue(of(stockLevel));
    component.adjust('release');

    expect(inventoryServiceSpy.releaseStock).toHaveBeenCalledWith('SKU-1', 1);
  });

  it('fulfills stock', () => {
    setup(['INVENTORY_WRITE']);
    inventoryServiceSpy.getStockLevel.and.returnValue(of(stockLevel));
    component.lookupForm.setValue({ sku: 'SKU-1' });
    component.lookup();

    inventoryServiceSpy.fulfillStock.and.returnValue(of(stockLevel));
    component.adjust('fulfill');

    expect(inventoryServiceSpy.fulfillStock).toHaveBeenCalledWith('SKU-1', 1);
  });

  it('sets an error message when an adjustment fails', () => {
    setup(['INVENTORY_WRITE']);
    inventoryServiceSpy.getStockLevel.and.returnValue(of(stockLevel));
    component.lookupForm.setValue({ sku: 'SKU-1' });
    component.lookup();

    inventoryServiceSpy.receiveStock.and.returnValue(
      throwError(() => new Error('failed'))
    );
    component.adjust('receive');

    expect(component.errorMessage()).toBe('Failed to receive stock for SKU-1.');
  });
  it('prefills a linked SKU without performing any stock mutation or lookup', () => {
    setup(['INVENTORY_WRITE'], 'DEMO-MOUSE-001');
    expect(component.lookupForm.controls.sku.value).toBe('DEMO-MOUSE-001');
    expect(inventoryServiceSpy.getStockLevel).not.toHaveBeenCalled();
    expect(inventoryServiceSpy.receiveStock).not.toHaveBeenCalled();
    expect(inventoryServiceSpy.reserveStock).not.toHaveBeenCalled();
  });
});
