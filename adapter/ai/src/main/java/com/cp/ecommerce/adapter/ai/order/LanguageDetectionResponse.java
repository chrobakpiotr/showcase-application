package com.cp.ecommerce.adapter.ai.order;

/**
 * Raw shape of the model's JSON response for a single language-detection call, mapped via Spring AI's
 * {@code ChatClient#entity(Class)} before {@link RemarksLanguageDetectorAdapter} translates it into a {@code SupportedLocale}.
 * Kept package-private and separate from the domain enum for the same reason as {@code RemarksClassificationResponse}: a
 * free-form string is easier for a small local model to reliably produce than a strict enum, and is validated afterwards rather
 * than trusted blindly.
 */
record LanguageDetectionResponse(String language) {

}
