import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, Subject, throwError } from 'rxjs';

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

  function setup(
    roles: string[] = [],
    pendingResponse: ReturnType<ReturnsService['listPendingReturns']> = of(
      pendingPage()
    ),
    listResponse: ReturnType<ReturnsService['listReturns']> = of(pendingPage())
  ): void {
    returnsServiceSpy = jasmine.createSpyObj('ReturnsService', [
      'listReturns',
      'listPendingReturns',
      'approveReturn',
      'rejectReturn',
    ]);
    returnsServiceSpy.listReturns.and.returnValue(listResponse);
    returnsServiceSpy.listPendingReturns.and.returnValue(pendingResponse);

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
  it('blocks conflicting actions until the mutation and queue refresh finish', () => {
    setup(['RETURN_READ', 'RETURN_WRITE']);
    const response = new Subject<ReturnModel>();
    const queue = new Subject<ReturnType<typeof pendingPage>>();
    returnsServiceSpy.approveReturn.and.returnValue(response);
    returnsServiceSpy.listPendingReturns.and.returnValue(queue);
    component.approve('RETURN-1');
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('[role="status"]')
    ).not.toBeNull();
    const buttons: NodeListOf<HTMLButtonElement> =
      fixture.nativeElement.querySelectorAll('.actions button');
    buttons.forEach((button) => expect(button.disabled).toBeTrue());
    component.approve('RETURN-1');
    component.reject('RETURN-1');
    expect(returnsServiceSpy.approveReturn).toHaveBeenCalledTimes(1);
    expect(returnsServiceSpy.rejectReturn).not.toHaveBeenCalled();
    response.next(returnRequest);
    response.complete();
    component.reject('RETURN-1');
    expect(returnsServiceSpy.rejectReturn).not.toHaveBeenCalled();
    queue.next(pendingPage());
    queue.complete();
    returnsServiceSpy.rejectReturn.and.returnValue(of(returnRequest));
    component.reject('RETURN-1');
    expect(returnsServiceSpy.rejectReturn).toHaveBeenCalledTimes(1);
  });

  function pendingPage() {
    return { _embedded: { returnRequestResourceList: [returnRequest] } };
  }
  it('blocks approve while the refreshed queue is loading and clears stale rows on failure', () => {
    setup(['RETURN_READ', 'RETURN_WRITE']);
    const queue = new Subject<ReturnType<typeof pendingPage>>();
    returnsServiceSpy.rejectReturn.and.returnValue(of(returnRequest));
    returnsServiceSpy.listPendingReturns.and.returnValue(queue);
    component.reject('RETURN-1');
    component.approve('RETURN-1');
    expect(returnsServiceSpy.approveReturn).not.toHaveBeenCalled();
    queue.error(new Error('unavailable'));
    expect(component.loadingPending()).toBeFalse();
    expect(component.pendingReturns()).toEqual([]);
    expect(component.moderationErrorMessage()).toBe(
      'Failed to load pending return requests.'
    );
  });

  it('does not retry failed moderation and releases the controls', () => {
    setup(['RETURN_READ', 'RETURN_WRITE']);
    const response = new Subject<ReturnModel>();
    returnsServiceSpy.rejectReturn.and.returnValue(response);
    component.reject('RETURN-1');
    response.error(new Error('unavailable'));
    fixture.detectChanges();
    expect(component.moderating()).toBeFalse();
    expect(returnsServiceSpy.rejectReturn).toHaveBeenCalledTimes(1);
    expect(
      fixture.nativeElement.querySelector('[data-testid="approve-return"]')
        .disabled
    ).toBeFalse();
    returnsServiceSpy.approveReturn.and.returnValue(
      throwError(() => new Error('unavailable'))
    );
    component.approve('RETURN-1');
    expect(component.moderating()).toBeFalse();
  });

  it('cancels a superseded list read so it cannot overwrite the refreshed list', () => {
    setup(['RETURN_READ', 'RETURN_WRITE']);
    const older = new Subject<ReturnType<typeof pendingPage>>();
    returnsServiceSpy.listReturns.and.returnValues(older, of({}));
    returnsServiceSpy.approveReturn.and.returnValue(of(returnRequest));
    returnsServiceSpy.rejectReturn.and.returnValue(of(returnRequest));
    component.approve('RETURN-1');
    component.reject('RETURN-1');
    older.next(pendingPage());
    expect(component.returns()).toEqual([]);
    expect(older.observed).toBeFalse();
  });

  it('cancels pending moderation when the view is destroyed', () => {
    setup(['RETURN_READ', 'RETURN_WRITE']);
    const response = new Subject<ReturnModel>();
    returnsServiceSpy.approveReturn.and.returnValue(response);
    component.approve('RETURN-1');
    fixture.destroy();
    expect(response.observed).toBeFalse();
    response.next(returnRequest);
    expect(returnsServiceSpy.listReturns).toHaveBeenCalledTimes(1);
  });

  it('blocks actions during initial loading, then allows an explicit read-only recovery', () => {
    const queue = new Subject<ReturnType<typeof pendingPage>>();
    setup(['RETURN_READ', 'RETURN_WRITE'], queue);
    component.approve('RETURN-1');
    component.reject('RETURN-1');
    component.refreshQueue();
    expect(returnsServiceSpy.approveReturn).not.toHaveBeenCalled();
    expect(returnsServiceSpy.rejectReturn).not.toHaveBeenCalled();
    expect(returnsServiceSpy.listPendingReturns).toHaveBeenCalledTimes(1);
    queue.error(new Error('unavailable'));
    returnsServiceSpy.listPendingReturns.and.returnValue(of(pendingPage()));
    fixture.detectChanges();
    fixture.nativeElement
      .querySelector('[data-testid="refresh-queue"]')
      .click();
    expect(component.pendingReturns()).toEqual([returnRequest]);
    expect(component.moderationErrorMessage()).toBeNull();
    const mutation = new Subject<ReturnModel>();
    returnsServiceSpy.rejectReturn.and.returnValue(mutation);
    component.reject('RETURN-1');
    component.refreshQueue();
    expect(returnsServiceSpy.listPendingReturns).toHaveBeenCalledTimes(2);
  });

  it('cancels both pending list reads when destroyed', () => {
    const queue = new Subject<ReturnType<typeof pendingPage>>();
    const history = new Subject<ReturnType<typeof pendingPage>>();
    setup(['RETURN_READ'], queue, history);
    fixture.destroy();
    expect(queue.observed).toBeFalse();
    expect(history.observed).toBeFalse();
  });

  it('covers return-history pagination in both directions and at boundaries', () => {
    setup(['RETURN_READ']);
    returnsServiceSpy.listReturns.calls.reset();

    component.historyPage.set(0);
    component.historyTotalPages.set(3);
    component.previousHistoryPage();
    expect(returnsServiceSpy.listReturns).not.toHaveBeenCalled();

    component.nextHistoryPage();
    expect(component.historyPage()).toBe(1);
    expect(returnsServiceSpy.listReturns).toHaveBeenCalledWith(1, 20);

    component.previousHistoryPage();
    expect(component.historyPage()).toBe(0);
    expect(returnsServiceSpy.listReturns).toHaveBeenCalledWith(0, 20);

    returnsServiceSpy.listReturns.calls.reset();
    component.historyPage.set(2);
    component.historyTotalPages.set(3);
    component.nextHistoryPage();
    expect(component.historyPage()).toBe(2);
    expect(returnsServiceSpy.listReturns).not.toHaveBeenCalled();
  });

  it('covers pending pagination boundaries and backs up from an empty trailing page', () => {
    setup(['RETURN_READ']);
    returnsServiceSpy.listPendingReturns.calls.reset();

    component.pendingPage.set(0);
    component.previousPendingPage();
    expect(returnsServiceSpy.listPendingReturns).not.toHaveBeenCalled();

    component.pendingPage.set(1);
    returnsServiceSpy.listPendingReturns.and.returnValue(of(pendingPage()));
    component.previousPendingPage();
    expect(component.pendingPage()).toBe(0);
    expect(returnsServiceSpy.listPendingReturns).toHaveBeenCalledWith(0, 20);

    returnsServiceSpy.listPendingReturns.calls.reset();
    component.pendingPage.set(1);
    component.pendingTotalPages.set(2);
    component.nextPendingPage();
    expect(component.pendingPage()).toBe(1);
    expect(returnsServiceSpy.listPendingReturns).not.toHaveBeenCalled();

    returnsServiceSpy.listPendingReturns.calls.reset();
    returnsServiceSpy.listPendingReturns.and.returnValues(
      of({ _embedded: { returnRequestResourceList: [] } }),
      of(pendingPage())
    );
    component.pendingPage.set(0);
    component.pendingTotalPages.set(2);

    component.nextPendingPage();

    expect(component.pendingPage()).toBe(0);
    expect(returnsServiceSpy.listPendingReturns.calls.count()).toBe(2);
    expect(returnsServiceSpy.listPendingReturns.calls.argsFor(0)).toEqual([
      1, 20,
    ]);
    expect(returnsServiceSpy.listPendingReturns.calls.argsFor(1)).toEqual([
      0, 20,
    ]);
    expect(component.pendingReturns()).toEqual([returnRequest]);
  });
});
