import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import { RecommendationsResponseModel } from '@app/recommendations/recommendations-response.model';

@Injectable({ providedIn: 'root' })
export class RecommendationsService {
  private readonly httpClient = inject(HttpClient);

  // The e-mail goes in the request body so it stays out of URLs, access logs and traces.
  getRecommendations(email: string): Observable<RecommendationsResponseModel> {
    return this.httpClient.post<RecommendationsResponseModel>(
      `${environment.apiPrefix}/recommendations`,
      { email }
    );
  }
}
