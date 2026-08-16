package com.cp.ecommerce.adapter.mail.pdf.ftl;

import java.io.Serializable;

import org.apache.commons.lang3.StringUtils;

import lombok.Builder;

/**
 * FTL representation of clickable email entry.
 */
@Builder
public record EmailFtl(String address) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static EmailFtl of(final String email) {

        if (StringUtils.isBlank(email)) {
            return EmailFtl.builder().build();
        }
        return EmailFtl.builder().address(email).build();
    }

}
