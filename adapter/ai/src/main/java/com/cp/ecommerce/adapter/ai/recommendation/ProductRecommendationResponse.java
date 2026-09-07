package com.cp.ecommerce.adapter.ai.recommendation;

/**
 * Raw shape of one AI-produced recommendation entry before adapter-side validation against known candidates.
 */
record ProductRecommendationResponse(String sku, String productName, String reason) {

}
