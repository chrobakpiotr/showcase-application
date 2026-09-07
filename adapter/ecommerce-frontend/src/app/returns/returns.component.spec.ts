import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';

import { AuthService } from '@app/auth/auth.service';
import { ReturnModel } from '@app/returns/return.model';
import { ReturnsComponent } from '@app/returns/returns.component';
import { ReturnsService } from '@app/returns/returns.service';

describe('ReturnsComponent', () => {
  let fixture: ComponentFixture<ReturnsComponent>;
  let component: ReturnsComponent;
  let returnsServiceSpy: jasmine.SpyObj<ReturnsService>;

  const returnRequest: ReturnModel = {
    returnNumber: 'RETURN-1',
    orderNumber: 'ORD-1',
    sku: 'SKU-1',
    quantity: 1,
    reason: 'Damaged',
    status: 'REQUESTED',
    requestedDate: '2024-03-15T10:30:00.000Z',
    decidedDate: null,
    refundAmount: 29.99,
  };

  function setup(roles: string[] = []): void {
    returnsServiceSpy = jasmine.createSpyObj('ReturnsService', [
      'listReturns',
      'listPendingReturns',
      'approveReturn',
      'rejectReturn',
    ]);
    returnsServiceSpy.listReturns.and.returnValue(
      of({ _embedded: { returnRequestResourceList: [returnRequest] } })
    );
    returnsServiceSpy.listPendingReturns.and.returnValue(
      of({ _embedded: { returnRequestResourceList: [returnRequest] } })
    );

    TestBed.configureTestingModule({
      imports: [ReturnsComponent],
      providers: [
        { provide: ReturnsService, useValue: returnsServiceSpy },
        { provide: AuthService, useValue: { roles: signal(roles) } },
      ],
    });

    fixture = TestBed.createComponent(ReturnsComponent);
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

  it('does not load returns without the RETURN_READ role', () => {
    setup();
    expect(returnsServiceSpy.listReturns).not.toHaveBeenCalled();
    expect(component.canRead).toBeFalse();
  });

  it('loads returns with the RETURN_READ role', () => {
    setup(['RETURN_READ']);
    expect(returnsServiceSpy.listReturns).toHaveBeenCalled();
    expect(returnsServiceSpy.listPendingReturns).toHaveBeenCalled();
    expect(component.returns()).toEqual([returnRequest]);
  });

  it('defaults both lists to empty arrays when embedded collections are missing', () => {
    returnsServiceSpy = jasmine.createSpyObj('ReturnsService', [
      'listReturns',
      'listPendingReturns',
      'approveReturn',
      'rejectReturn',
    ]);
    returnsServiceSpy.listReturns.and.returnValue(of({}));
    returnsServiceSpy.listPendingReturns.and.returnValue(of({}));

    TestBed.configureTestingModule({
      imports: [ReturnsComponent],
      providers: [
        { provide: ReturnsService, useValue: returnsServiceSpy },
        { provide: AuthService, useValue: { roles: signal(['RETURN_READ']) } },
      ],
    });

    fixture = TestBed.createComponent(ReturnsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.returns()).toEqual([]);
    expect(component.pendingReturns()).toEqual([]);
  });

  it('reports canWrite true with the RETURN_WRITE role', () => {
    setup(['RETURN_WRITE']);
    expect(component.canWrite).toBeTrue();
  });

  it('sets an error message when loading returns fails', () => {
    returnsServiceSpy = jasmine.createSpyObj('ReturnsService', [
      'listReturns',
      'listPendingReturns',
      'approveReturn',
      'rejectReturn',
    ]);
    returnsServiceSpy.listReturns.and.returnValue(
      throwError(() => new Error('failed'))
    );
    returnsServiceSpy.listPendingReturns.and.returnValue(
      of({ _embedded: { returnRequestResourceList: [] } })
    );
    TestBed.configureTestingModule({
      imports: [ReturnsComponent],
      providers: [
        { provide: ReturnsService, useValue: returnsServiceSpy },
        { provide: AuthService, useValue: { roles: signal(['RETURN_READ']) } },
      ],
    });
    fixture = TestBed.createComponent(ReturnsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.errorMessage()).toBe('Failed to load return requests.');
  });

  it('sets an error message when loading pending returns fails', () => {
    returnsServiceSpy = jasmine.createSpyObj('ReturnsService', [
      'listReturns',
      'listPendingReturns',
      'approveReturn',
      'rejectReturn',
    ]);
    returnsServiceSpy.listReturns.and.returnValue(
      of({ _embedded: { returnRequestResourceList: [] } })
    );
    returnsServiceSpy.listPendingReturns.and.returnValue(
      throwError(() => new Error('failed'))
    );
    TestBed.configureTestingModule({
      imports: [ReturnsComponent],
      providers: [
        { provide: ReturnsService, useValue: returnsServiceSpy },
        { provide: AuthService, useValue: { roles: signal(['RETURN_READ']) } },
      ],
    });
    fixture = TestBed.createComponent(ReturnsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.moderationErrorMessage()).toBe(
      'Failed to load pending return requests.'
    );
  });

  it('approves a return request and reloads both lists', () => {
    setup(['RETURN_READ', 'RETURN_WRITE']);
    returnsServiceSpy.approveReturn.and.returnValue(of(returnRequest));

    component.approve('RETURN-1');

    expect(returnsServiceSpy.approveReturn).toHaveBeenCalledWith('RETURN-1');
    expect(returnsServiceSpy.listReturns).toHaveBeenCalledTimes(2);
    expect(returnsServiceSpy.listPendingReturns).toHaveBeenCalledTimes(2);
  });

  it('sets an error message when approving fails', () => {
    setup(['RETURN_READ', 'RETURN_WRITE']);
    returnsServiceSpy.approveReturn.and.returnValue(
      throwError(() => new Error('failed'))
    );

    component.approve('RETURN-1');

    expect(component.moderationErrorMessage()).toBe(
      'Failed to approve return request.'
    );
  });

  it('rejects a return request and reloads both lists', () => {
    setup(['RETURN_READ', 'RETURN_WRITE']);
    returnsServiceSpy.rejectReturn.and.returnValue(of(returnRequest));

    component.reject('RETURN-1');

    expect(returnsServiceSpy.rejectReturn).toHaveBeenCalledWith('RETURN-1');
    expect(returnsServiceSpy.listReturns).toHaveBeenCalledTimes(2);
    expect(returnsServiceSpy.listPendingReturns).toHaveBeenCalledTimes(2);
  });

  it('sets an error message when rejecting fails', () => {
    setup(['RETURN_READ', 'RETURN_WRITE']);
    returnsServiceSpy.rejectReturn.and.returnValue(
      throwError(() => new Error('failed'))
    );

    component.reject('RETURN-1');

    expect(component.moderationErrorMessage()).toBe(
      'Failed to reject return request.'
    );
  });
});
