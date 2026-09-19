package com.cp.ecommerce.adapter;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring boot application needed for properly loading the security adapter test context.
 *
 * <p>
 * Keep this context intentionally scoped to security configuration. Authorization tests exercise the filter chain and request
 * matchers; they must not instantiate unrelated controllers or application orchestration beans.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.cp.ecommerce.adapter.security")
public class SpringBootSecurityTestApplication {

}
