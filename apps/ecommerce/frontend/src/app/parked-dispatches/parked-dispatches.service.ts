import { HttpClient } from "@angular/common/http";
import { Injectable, inject } from "@angular/core";
import { Observable } from "rxjs";

import { environment } from "@environments/environment";

export interface ParkedDispatch {
  dispatchId: string;
  orderNumber: string;
  dispatchType: string;
  status: "PARKED";
  attempts: number;
  createdAt: string;
  nextAttemptAt: null;
  reasonCode: "ORDER_MISSING" | "ATTEMPT_BUDGET_EXHAUSTED" | "OTHER";
}

export interface ParkedDispatchPage {
  content: ParkedDispatch[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  oldestAgeSeconds: number | null;
}

@Injectable({ providedIn: "root" })
export class ParkedDispatchesService {
  private readonly httpClient = inject(HttpClient);

  listParkedDispatches(page = 0, size = 20): Observable<ParkedDispatchPage> {
    return this.httpClient.get<ParkedDispatchPage>(
      `${environment.apiPrefix}/order-placement/dispatches/parked`,
      { params: { page, size } },
    );
  }
}
