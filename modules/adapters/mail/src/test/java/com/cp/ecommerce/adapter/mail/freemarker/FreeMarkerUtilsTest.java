package com.cp.ecommerce.adapter.mail.freemarker;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.Version;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import static com.cp.ecommerce.adapter.mail.freemarker.formatter.DefaultLocalDateFormatter.DEFAULT_DATE_PATTERN;

/**
 * Test cases for {@link FreeMarkerUtils} class.
 */
@ExtendWith(MockitoExtension.class)
public class FreeMarkerUtilsTest {

    private static final Version FREEMARKER_VERSION = Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS;

    private static final String TEST_DATE_PATTERN = "yyyy-MM-dd";

    @Test
    public void shouldReturnTemplateResult() throws Exception {

        final Map<String, String> model = ImmutableMap.of("customer", "test");
        final Template template = createTemplate("Hello ${customer}");

        assertThat(
                FreeMarkerUtils.INSTANCE
                        .processTemplateIntoString(template, model, new DefaultObjectWrapper(FREEMARKER_VERSION)),
                is("Hello test"));
    }

    @Test
    public void shouldConfigureEnvironment() throws Exception {

        final Date date = Date.from(LocalDate.of(2016, 1, 5).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
        final Map<String, Object> model = ImmutableMap.of("date", date);
        final Template template = createTemplate("${date?date}");

        assertThat(processTemplate(template, model, DEFAULT_DATE_PATTERN), is("05.01.2016"));
        assertThat(processTemplate(template, model, TEST_DATE_PATTERN), is("2016-01-05"));
    }

    private Template createTemplate(final String templateStr) throws IOException {

        return new Template("name", new StringReader(templateStr), new Configuration(FREEMARKER_VERSION));
    }

    private String processTemplate(final Template template, final Object model, final String pattern)
            throws TemplateException, IOException {

        return FreeMarkerUtils.INSTANCE.processTemplateIntoString(
                template,
                model,
                new DefaultObjectWrapper(FREEMARKER_VERSION),
                environment -> environment.setDateFormat(pattern));
    }

}
