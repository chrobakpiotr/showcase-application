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

import { CatalogService } from '@app/catalog/catalog.service';
import { CategoryModel } from '@app/catalog/category.model';
import { ProductPageModel } from '@app/catalog/product-details.model';
import { environment } from '@environments/environment';

describe('CatalogService', () => {
  let catalogService: CatalogService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        CatalogService,
        provideHttpClient(withXhr(), withInterceptorsFromDi()),
        provideHttpClientTesting(),
      ],
    });
    catalogService = TestBed.inject(CatalogService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should be created', () => {
    expect(catalogService).toBeTruthy();
  });

  it('should list categories', () => {
    const categories: CategoryModel[] = [
      { name: 'Electronics', slug: 'electronics' },
    ];

    catalogService
      .listCategories()
      .subscribe((data) => expect(data).toBe(categories));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/catalog/categories`
    );
    expect(req.request.method).toBe('GET');
    req.flush(categories);
  });

  it('should list products without a category filter', () => {
    const page = {
      page: { size: 12, totalElements: 0, totalPages: 0, number: 0 },
    } as ProductPageModel;

    catalogService
      .listProducts(0, 12, null)
      .subscribe((data) => expect(data).toBe(page));

    const req = httpTestingController.expectOne(
      (request) =>
        request.url === `${environment.apiPrefix}/catalog/products` &&
        request.params.get('page') === '0' &&
        request.params.get('size') === '12' &&
        !request.params.has('category')
    );
    expect(req.request.method).toBe('GET');
    req.flush(page);
  });

  it('should list products filtered by category', () => {
    const page = {
      page: { size: 12, totalElements: 0, totalPages: 0, number: 0 },
    } as ProductPageModel;

    catalogService
      .listProducts(1, 12, 'electronics')
      .subscribe((data) => expect(data).toBe(page));

    const req = httpTestingController.expectOne(
      (request) =>
        request.url === `${environment.apiPrefix}/catalog/products` &&
        request.params.get('category') === 'electronics'
    );
    expect(req.request.method).toBe('GET');
    req.flush(page);
  });
});
