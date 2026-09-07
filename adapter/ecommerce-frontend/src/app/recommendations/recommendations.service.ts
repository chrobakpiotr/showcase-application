import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import { RecommendationsResponseModel } from '@app/recommendations/recommendations-response.model';

@Injectable({ providedIn: 'root' })
export class RecommendationsService {
  private readonly httpClient = inject(HttpClient);

  getRecommendations(email: string): Observable<RecommendationsResponseModel> {
    return this.httpClient.get<RecommendationsResponseModel>(
      `${environment.apiPrefix}/recommendations`,
      { params: new HttpParams().set('email', email) }
    );
  }
}
