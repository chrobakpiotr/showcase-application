import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { CatalogComponent } from '@app/catalog/catalog.component';
import { CatalogService } from '@app/catalog/catalog.service';
import { CategoryModel } from '@app/catalog/category.model';
import { ProductPageModel } from '@app/catalog/product-details.model';

describe('CatalogComponent', () => {
  let fixture: ComponentFixture<CatalogComponent>;
  let component: CatalogComponent;
  let catalogServiceSpy: jasmine.SpyObj<CatalogService>;

  const categories: CategoryModel[] = [
    { name: 'Electronics', slug: 'electronics' },
  ];

  const page: ProductPageModel = {
    _embedded: {
      productDetailsResourceList: [
        {
          sku: 'SKU-1',
          name: 'Headphones',
          description: 'Over-ear',
          categorySlug: 'electronics',
          categoryName: 'Electronics',
          unitPrice: 99.99,
          imageUrl: 'https://example.com/img.png',
          active: true,
          created: '2024-03-15T10:30:00.000Z',
        },
      ],
    },
    page: { size: 12, totalElements: 1, totalPages: 1, number: 0 },
  };

  function setup(): void {
    catalogServiceSpy = jasmine.createSpyObj('CatalogService', [
      'listCategories',
      'listProducts',
    ]);
    catalogServiceSpy.listCategories.and.returnValue(of(categories));
    catalogServiceSpy.listProducts.and.returnValue(of(page));

    TestBed.configureTestingModule({
      imports: [CatalogComponent],
      providers: [{ provide: CatalogService, useValue: catalogServiceSpy }],
    });

    fixture = TestBed.createComponent(CatalogComponent);
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

  it('loads categories and products on init', () => {
    setup();
    expect(component.categories()).toEqual(categories);
    expect(component.products()).toEqual(
      page._embedded!.productDetailsResourceList
    );
    expect(component.totalPages()).toBe(1);
    expect(component.loading()).toBeFalse();
  });

  it('sets an error message when categories fail to load', () => {
    catalogServiceSpy = jasmine.createSpyObj('CatalogService', [
      'listCategories',
      'listProducts',
    ]);
    catalogServiceSpy.listCategories.and.returnValue(
      throwError(() => new Error('failed'))
    );
    catalogServiceSpy.listProducts.and.returnValue(of(page));
    TestBed.configureTestingModule({
      imports: [CatalogComponent],
      providers: [{ provide: CatalogService, useValue: catalogServiceSpy }],
    });
    fixture = TestBed.createComponent(CatalogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.errorMessage()).toBe('Failed to load categories.');
  });

  it('sets an error message when products fail to load', () => {
    catalogServiceSpy = jasmine.createSpyObj('CatalogService', [
      'listCategories',
      'listProducts',
    ]);
    catalogServiceSpy.listCategories.and.returnValue(of(categories));
    catalogServiceSpy.listProducts.and.returnValue(
      throwError(() => new Error('failed'))
    );
    TestBed.configureTestingModule({
      imports: [CatalogComponent],
      providers: [{ provide: CatalogService, useValue: catalogServiceSpy }],
    });
    fixture = TestBed.createComponent(CatalogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.errorMessage()).toBe('Failed to load products.');
    expect(component.loading()).toBeFalse();
  });

  it('defaults to an empty product list when the page has no embedded content', () => {
    catalogServiceSpy = jasmine.createSpyObj('CatalogService', [
      'listCategories',
      'listProducts',
    ]);
    catalogServiceSpy.listCategories.and.returnValue(of(categories));
    catalogServiceSpy.listProducts.and.returnValue(
      of({ page: { size: 12, totalElements: 0, totalPages: 0, number: 0 } })
    );
    TestBed.configureTestingModule({
      imports: [CatalogComponent],
      providers: [{ provide: CatalogService, useValue: catalogServiceSpy }],
    });
    fixture = TestBed.createComponent(CatalogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.products()).toEqual([]);
  });

  it('filters by the selected category and resets to the first page', () => {
    setup();
    component.page.set(2);

    component.selectCategory('electronics');

    expect(component.selectedCategory()).toBe('electronics');
    expect(component.page()).toBe(0);
    expect(catalogServiceSpy.listProducts).toHaveBeenCalledWith(
      0,
      12,
      'electronics'
    );
  });

  it('treats an empty category selection as no filter', () => {
    setup();
    component.selectCategory('');
    expect(component.selectedCategory()).toBeNull();
  });

  it('advances to the next page when not on the last page', () => {
    setup();
    component.totalPages.set(3);
    component.page.set(0);

    component.nextPage();

    expect(component.page()).toBe(1);
    expect(catalogServiceSpy.listProducts).toHaveBeenCalledWith(1, 12, null);
  });

  it('does not advance past the last page', () => {
    setup();
    component.totalPages.set(1);
    component.page.set(0);

    component.nextPage();

    expect(component.page()).toBe(0);
  });

  it('goes back to the previous page', () => {
    setup();
    component.page.set(1);

    component.previousPage();

    expect(component.page()).toBe(0);
  });

  it('does not go before the first page', () => {
    setup();
    component.page.set(0);

    component.previousPage();

    expect(component.page()).toBe(0);
  });
});
