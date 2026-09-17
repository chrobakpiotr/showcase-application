package com.cp.ecommerce.adapter.mail.pdf.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import org.apache.commons.lang3.StringUtils;
import org.apache.xmlgraphics.io.Resource;
import org.apache.xmlgraphics.io.ResourceResolver;

import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Custom resource resolver for files in the classpath.
 */
@RequiredArgsConstructor
@Component
public class ClasspathResourceResolver implements ResourceResolver {

    private final transient ResourceLoader resourceLoader;

    @Override
    public Resource getResource(final URI uri) throws IOException {

        return new Resource(
                resourceLoader.getResource("classpath:" + StringUtils.stripStart(uri.toString(), "file:")).getInputStream());
    }

    @Override
    public OutputStream getOutputStream(final URI uri) {

        throw new UnsupportedOperationException();
    }

}
