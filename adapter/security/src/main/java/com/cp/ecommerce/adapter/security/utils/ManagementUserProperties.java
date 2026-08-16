package com.cp.ecommerce.adapter.security.utils;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties of the management server's user, bound from the {@code management.user.*} namespace via constructor binding.
 *
 * @param name management server basic-auth username.
 * @param password management server basic-auth password.
 */
@ConfigurationProperties(prefix = "management.user")
public record ManagementUserProperties(String name, String password) {

}
