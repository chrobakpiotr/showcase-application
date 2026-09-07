import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';

import { AuthService } from '@app/auth/auth.service';
import { CouponModel } from '@app/coupons/coupon.model';
import { CouponsService } from '@app/coupons/coupons.service';

@Component({
  selector: 'app-coupons',
  templateUrl: './coupons.component.html',
  styleUrls: ['./coupons.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, CurrencyPipe, DatePipe],
})
export class CouponsComponent implements OnInit {
  private readonly couponsService = inject(CouponsService);
  private readonly authService = inject(AuthService);

  readonly coupons = signal<CouponModel[]>([]);
  readonly errorMessage = signal<string | null>(null);
  readonly createSuccess = signal(false);

  readonly createForm = new FormGroup({
    code: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(30)],
    }),
    discountType: new FormControl<'PERCENTAGE' | 'FIXED_AMOUNT'>('PERCENTAGE', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    discountValue: new FormControl<number | null>(10, {
      validators: [Validators.required, Validators.min(0.01)],
    }),
    minimumOrderAmount: new FormControl<number | null>(null),
    maxRedemptions: new FormControl<number | null>(null),
    expiresAt: new FormControl('', { nonNullable: true }),
    active: new FormControl(true, { nonNullable: true }),
  });

  get canRead(): boolean {
    return this.authService.roles().includes('COUPON_READ');
  }

  get canWrite(): boolean {
    return this.authService.roles().includes('COUPON_WRITE');
  }

  ngOnInit(): void {
    if (this.canRead) {
      this.loadCoupons();
    }
  }

  loadCoupons(): void {
    this.errorMessage.set(null);
    this.couponsService.listCoupons().subscribe({
      next: (page) =>
        this.coupons.set(page._embedded?.couponDetailsResourceList ?? []),
      error: () => this.errorMessage.set('Failed to load coupons.'),
    });
  }

  createCoupon(): void {
    if (!this.canWrite || this.createForm.invalid) return;
    this.errorMessage.set(null);
    this.createSuccess.set(false);
    const value = this.createForm.getRawValue();
    this.couponsService
      .createCoupon({
        code: value.code.trim().toUpperCase(),
        discountType: value.discountType,
        discountValue: value.discountValue!,
        minimumOrderAmount: value.minimumOrderAmount,
        maxRedemptions: value.maxRedemptions,
        expiresAt: value.expiresAt
          ? new Date(value.expiresAt).toISOString()
          : null,
        active: value.active,
      })
      .subscribe({
        next: () => {
          this.createSuccess.set(true);
          this.createForm.reset({
            code: '',
            discountType: 'PERCENTAGE',
            discountValue: 10,
            minimumOrderAmount: null,
            maxRedemptions: null,
            expiresAt: '',
            active: true,
          });
          this.loadCoupons();
        },
        error: () => this.errorMessage.set('Failed to create coupon.'),
      });
  }

  toggleActive(coupon: CouponModel): void {
    if (!this.canWrite) return;
    this.errorMessage.set(null);
    const request$ = coupon.active
      ? this.couponsService.deactivateCoupon(coupon.code)
      : this.couponsService.activateCoupon(coupon.code);
    request$.subscribe({
      next: () => this.loadCoupons(),
      error: () => this.errorMessage.set('Failed to update coupon status.'),
    });
  }
}
