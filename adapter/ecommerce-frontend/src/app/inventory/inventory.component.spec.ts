import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';

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

  function setup(roles: string[] = []): void {
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
      ],
    });

    fixture = TestBed.createComponent(InventoryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

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
});
