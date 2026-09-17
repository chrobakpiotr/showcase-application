package com.cp.ecommerce.adapter.mail.utils;

import java.net.URI;

import com.cp.ecommerce.adapter.mail.pdf.utils.ClasspathResourceResolver;

import org.apache.xmlgraphics.io.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;

/**
 * Test cases for {@link ClasspathResourceResolver} class.
 */
@ExtendWith(MockitoExtension.class)
class ClasspathResourceResolverTest {

    private static final String RESOURCE_PATH = "/path/to/resource";

    @Mock
    private transient ResourceLoader resourceLoader;

    @Test
    public void shouldReturnResource() throws Exception {

        when(resourceLoader.getResource("classpath:" + RESOURCE_PATH)).thenReturn(new ByteArrayResource(new byte[] {}));
        try (Resource resource = new ClasspathResourceResolver(resourceLoader).getResource(new URI("file:" + RESOURCE_PATH))) {

            assertThat(resource, is(notNullValue()));
        }
    }

    @Test
    public void shouldThrowNotImplementedException() {

        assertThatThrownBy(() -> new ClasspathResourceResolver(resourceLoader).getOutputStream(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

}
