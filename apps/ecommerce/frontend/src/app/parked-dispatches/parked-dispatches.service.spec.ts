import { TestBed } from "@angular/core/testing";
import {
  HttpTestingController,
  provideHttpClientTesting,
} from "@angular/common/http/testing";
import {
  provideHttpClient,
  withInterceptorsFromDi,
  withXhr,
} from "@angular/common/http";

import { environment } from "@environments/environment";
import { ParkedDispatchesService } from "@app/parked-dispatches/parked-dispatches.service";

describe("ParkedDispatchesService", () => {
  let service: ParkedDispatchesService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ParkedDispatchesService,
        provideHttpClient(withXhr(), withInterceptorsFromDi()),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(ParkedDispatchesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it("requests a bounded read-only page from the existing parked endpoint", () => {
    service.listParkedDispatches(3, 20).subscribe();
    const request = http.expectOne(
      `${environment.apiPrefix}/order-placement/dispatches/parked?page=3&size=20`,
    );
    expect(request.request.method).toBe("GET");
    request.flush({
      content: [],
      page: 3,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      oldestAgeSeconds: null,
    });
  });
});
