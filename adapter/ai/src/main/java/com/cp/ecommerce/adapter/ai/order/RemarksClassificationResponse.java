package com.cp.ecommerce.adapter.ai.order;

/**
 * Raw shape of the model's JSON response for a single remarks-classification call, mapped via Spring AI's
 * {@code ChatClient#entity(Class)} (backed by its {@code BeanOutputConverter}) before
 * {@link OllamaOrderRemarksClassifierAdapter} translates it into a {@code RemarksTriageResult}. Kept package-private and
 * separate from the domain's {@code RemarksTriageResult}: this record's shape is dictated by what is easy for a small local
 * model to reliably produce as JSON (a free-form category string, validated afterwards), not by the domain's stricter
 * {@code RemarksTriageCategory} enum.
 */
record RemarksClassificationResponse(String category, String rationale) {

}
