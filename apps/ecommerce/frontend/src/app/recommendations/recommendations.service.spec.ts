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

import { RecommendationsService } from '@app/recommendations/recommendations.service';
import { environment } from '@environments/environment';

describe('RecommendationsService', () => {
  let recommendationsService: RecommendationsService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        RecommendationsService,
        provideHttpClient(withXhr(), withInterceptorsFromDi()),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    recommendationsService = TestBed.inject(RecommendationsService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should GET recommendations with the email query parameter', () => {
    const response = {
      assistantAvailable: true,
      recommendations: [
        { sku: 'SKU-1', productName: 'Mouse', reason: 'Good fit.' },
      ],
    };

    recommendationsService
      .getRecommendations('john.doe@test.com')
      .subscribe((data) => expect(data).toEqual(response));

    const req = httpTestingController.expectOne(
      `${environment.apiPrefix}/recommendations?email=john.doe@test.com`
    );
    expect(req.request.method).toBe('GET');
    req.flush(response);
  });
});
