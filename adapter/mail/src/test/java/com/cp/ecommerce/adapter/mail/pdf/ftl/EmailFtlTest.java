package com.cp.ecommerce.adapter.mail.pdf.ftl;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Test cases for {@link EmailFtl} class.
 */
public class EmailFtlTest {

    @Test
    public void shouldCreateEmailFtlNullFromNull() {

        final EmailFtl actual = EmailFtl.of(null);

        assertThat(actual.address(), is(nullValue()));
    }

}
