import { CurrencyPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';

import { CatalogService } from '@app/catalog/catalog.service';
import { CategoryModel } from '@app/catalog/category.model';
import { ProductDetailsModel } from '@app/catalog/product-details.model';

const PAGE_SIZE = 12;

@Component({
  selector: 'app-catalog',
  templateUrl: './catalog.component.html',
  styleUrls: ['./catalog.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe],
})
export class CatalogComponent implements OnInit {
  private readonly catalogService = inject(CatalogService);

  readonly categories = signal<CategoryModel[]>([]);
  readonly products = signal<ProductDetailsModel[]>([]);
  readonly selectedCategory = signal<string | null>(null);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadProducts();
    this.catalogService.listCategories().subscribe({
      next: (categories) => this.categories.set(categories),
      error: () => this.errorMessage.set('Failed to load categories.'),
    });
  }

  selectCategory(slug: string): void {
    this.selectedCategory.set(slug === '' ? null : slug);
    this.page.set(0);
    this.loadProducts();
  }

  previousPage(): void {
    if (this.page() === 0) return;
    this.page.set(this.page() - 1);
    this.loadProducts();
  }

  nextPage(): void {
    if (this.page() + 1 >= this.totalPages()) return;
    this.page.set(this.page() + 1);
    this.loadProducts();
  }

  private loadProducts(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.catalogService
      .listProducts(this.page(), PAGE_SIZE, this.selectedCategory())
      .subscribe({
        next: (result) => {
          this.loading.set(false);
          this.products.set(result._embedded?.productDetailsResourceList ?? []);
          this.totalPages.set(result.page.totalPages);
        },
        error: () => {
          this.loading.set(false);
          this.errorMessage.set('Failed to load products.');
        },
      });
  }
}
