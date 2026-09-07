export type DiscountType = 'PERCENTAGE' | 'FIXED_AMOUNT';

export interface CouponModel {
  code: string;
  discountType: DiscountType;
  discountValue: number;
  minimumOrderAmount: number | null;
  maxRedemptions: number | null;
  redemptionCount: number;
  expiresAt: string | null;
  active: boolean;
}

export interface CouponPageModel {
  _embedded?: {
    couponDetailsResourceList: CouponModel[];
  };
  page: {
    size: number;
    totalElements: number;
    totalPages: number;
    number: number;
  };
}

export interface CouponRequestModel {
  code: string;
  discountType: DiscountType;
  discountValue: number;
  minimumOrderAmount: number | null;
  maxRedemptions: number | null;
  expiresAt: string | null;
  active: boolean;
}
