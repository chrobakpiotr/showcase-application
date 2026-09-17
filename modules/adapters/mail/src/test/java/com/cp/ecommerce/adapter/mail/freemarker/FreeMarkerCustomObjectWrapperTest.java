package com.cp.ecommerce.adapter.mail.freemarker;

import java.util.Optional;

import com.cp.ecommerce.adapter.mail.pdf.i18n.TranslatableString;
import com.cp.ecommerce.adapter.mail.pdf.i18n.TranslatableStringConverter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import freemarker.template.SimpleScalar;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Test cases for {@link FreeMarkerCustomObjectWrapper} class.
 */
@ExtendWith(MockitoExtension.class)
class FreeMarkerCustomObjectWrapperTest {

    @Mock
    private transient TranslatableStringConverter translatableStringConverter;

    @Test
    public void shouldWrapTranslatableString() throws TemplateModelException {

        final TranslatableString string = TranslatableString.forKey("aaa", "bbb");
        when(translatableStringConverter.convert(string)).thenReturn("the translated text");
        final TemplateModel model = createTemplateModel(string);

        assertThat(model).isInstanceOf(SimpleScalar.class);
        assertThat(((SimpleScalar) model).getAsString()).isEqualTo("the translated text");
    }

    @Test
    public void shouldWrapOptionalWithString() throws TemplateModelException {

        final TemplateModel model = createTemplateModel(Optional.of("some string"));
        assertThat(model).isInstanceOf(SimpleScalar.class);
        assertThat(((SimpleScalar) model).getAsString()).isEqualTo("some string");
    }

    @Test
    public void shouldWrapEmptyOptional() throws TemplateModelException {

        assertThat(createTemplateModel(Optional.empty())).isNull();
    }

    private TemplateModel createTemplateModel(final Object arg) throws TemplateModelException {

        return new FreeMarkerCustomObjectWrapper(translatableStringConverter).wrap(arg);
    }

}
