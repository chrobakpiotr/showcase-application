import { Injectable } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  BehaviorSubject,
  defer,
  filter,
  finalize,
  Observable,
  share,
  take,
} from 'rxjs';

import { CartModel } from '@app/cart/cart.model';

interface CartMutation {
  readonly cartId: string;
}

@Injectable({ providedIn: 'root' })
export class CartMutationService {
  private readonly pendingMutations = new BehaviorSubject<
    readonly CartMutation[]
  >([]);
  private readonly pending = toSignal(this.pendingMutations, {
    requireSync: true,
  });

  isPending(cartId: string | undefined): boolean {
    return this.pending().some((mutation) => mutation.cartId === cartId);
  }

  whenSettled(cartId: string): Observable<unknown> {
    return this.pendingMutations.pipe(
      filter(
        (pending) => !pending.some((mutation) => mutation.cartId === cartId)
      ),
      take(1)
    );
  }

  track(cartId: string, request: Observable<CartModel>): Observable<CartModel> {
    return defer(() => {
      const mutation = { cartId };
      this.pendingMutations.next([...this.pendingMutations.value, mutation]);
      return request.pipe(
        finalize(() =>
          this.pendingMutations.next(
            this.pendingMutations.value.filter(
              (pending) => pending !== mutation
            )
          )
        )
      );
    }).pipe(
      // The server may already be processing the write when the route closes.
      share({ resetOnRefCountZero: false })
    );
  }
}
