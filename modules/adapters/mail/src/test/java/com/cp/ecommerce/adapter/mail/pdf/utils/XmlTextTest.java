package com.cp.ecommerce.adapter.mail.pdf.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XmlTextTest {

    @Test
    void shouldRemoveOnlyCharactersXmlForbids() {

        final String text = "a\u0001b\tc\nd\re\u000B\uD800f￾g�h😀i";

        assertThat(XmlText.clean(text)).isEqualTo("ab\tc\nd\refg�h😀i");
        assertThat(XmlText.clean("Smith & Co")).isEqualTo("Smith & Co");
        assertThat(XmlText.clean(null)).isNull();
    }
}
