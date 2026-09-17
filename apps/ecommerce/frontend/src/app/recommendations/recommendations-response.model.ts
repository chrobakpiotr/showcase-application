import { RecommendationModel } from '@app/recommendations/recommendation.model';

export interface RecommendationsResponseModel {
  recommendations: RecommendationModel[];
  assistantAvailable: boolean;
}
