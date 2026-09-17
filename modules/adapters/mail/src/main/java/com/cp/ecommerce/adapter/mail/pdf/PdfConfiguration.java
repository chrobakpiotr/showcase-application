package com.cp.ecommerce.adapter.mail.pdf;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for all PDF/FreeMarker related beans.
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan({
        "com.cp.ecommerce.adapter.mail.pdf",
        "com.cp.ecommerce.adapter.mail.freemarker",
        "com.cp.ecommerce.adapter.mail.configuration" })
public class PdfConfiguration {

}
