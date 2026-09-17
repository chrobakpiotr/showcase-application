package com.cp.ecommerce.adapter.mail.freemarker;

import java.util.Collections;

import com.cp.ecommerce.adapter.mail.pdf.i18n.TranslatableStringConverter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import freemarker.template.ObjectWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test cases for {@link FreeMarkerCustomObjectWrapperFactory} class.
 */
@ExtendWith(MockitoExtension.class)
class FreeMarkerCustomObjectWrapperFactoryTest {

    @Mock
    private transient CustomObjectWrapperFactory customObjectWrapperFactory;

    @Mock
    private transient TranslatableStringConverter translatableStringConverter;

    @Mock
    private transient ObjectWrapper objectWrapper;

    @Test
    public void shouldCreateDefaultWrapper() {

        assertThat(createObjectWrapper()).isNotEqualTo(objectWrapper);
    }

    @Test
    public void shouldCreateWrapperBasedOnModelType() {

        when(
                customObjectWrapperFactory
                        .createWrapperBasedOnModelType(ArgumentMatchers.anyString(), any(FreeMarkerCustomObjectWrapper.class)))
                .thenReturn(objectWrapper);
        assertThat(createObjectWrapper()).isEqualTo(objectWrapper);
    }

    private ObjectWrapper createObjectWrapper() {

        return new FreeMarkerCustomObjectWrapperFactory(
                Collections.singletonList(customObjectWrapperFactory),
                translatableStringConverter).createWrapper("model");
    }

}
