// Mirrors the backend's ProductDetailsResource (adapter/web/.../catalog/resource/ProductDetailsResource.java).
export interface ProductDetailsModel {
  sku: string;
  name: string;
  description: string;
  categorySlug: string;
  categoryName: string;
  unitPrice: number;
  imageUrl: string;
  active: boolean;
  created: string;
}

// Spring HATEOAS PagedModel envelope shape as serialized for GET /api/catalog/products.
export interface ProductPageModel {
  _embedded?: {
    productDetailsResourceList: ProductDetailsModel[];
  };
  page: {
    size: number;
    totalElements: number;
    totalPages: number;
    number: number;
  };
}
