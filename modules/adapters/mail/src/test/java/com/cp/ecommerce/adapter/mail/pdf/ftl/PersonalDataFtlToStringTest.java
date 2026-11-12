package com.cp.ecommerce.adapter.mail.pdf.ftl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Template models hold personal data, so their toString must not print it. */
class PersonalDataFtlToStringTest {

    @Test
    void shouldOmitPersonalDataFromTemplateModels() {

        final EmailFtl email = new EmailFtl("jan@example.com");
        final AddressFtl address = new AddressFtl("PL", "00-001", "Warsaw", "Main Street 1");
        final ContactFtl contact = new ContactFtl("Jan Kowalski", email, "+48 600 700 800", address, "Jan", "Kowalski");

        assertThat(email).hasToString("EmailFtl[personal data omitted]");
        assertThat(address).hasToString("AddressFtl[personal data omitted]");
        assertThat(contact).hasToString("ContactFtl[personal data omitted]");
    }
}
