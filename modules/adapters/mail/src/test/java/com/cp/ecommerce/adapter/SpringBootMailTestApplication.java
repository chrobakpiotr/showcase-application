package com.cp.ecommerce.adapter;

import com.cp.ecommerce.adapter.mail.configuration.MessageTemplateConfiguration;
import com.cp.ecommerce.adapter.mail.pdf.PdfConfiguration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * Spring boot application needed for properly loading the mail adapter test context.
 *
 * <p>
 * Keep this context intentionally scoped to the mail adapter. Pulling unrelated web/application beans into mail tests makes the
 * module depend on ports it does not own and turns unrelated orchestration changes into mail-test failures.
 */
@SpringBootApplication
@ComponentScan(basePackages = { "com.cp.ecommerce.adapter.mail", "com.cp.ecommerce.adapter.common.resilience" })
@Import({ MessageTemplateConfiguration.class, PdfConfiguration.class })
public class SpringBootMailTestApplication {

}
