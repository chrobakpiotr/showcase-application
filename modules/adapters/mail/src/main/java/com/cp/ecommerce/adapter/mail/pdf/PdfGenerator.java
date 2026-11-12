package com.cp.ecommerce.adapter.mail.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import com.cp.ecommerce.adapter.mail.pdf.utils.ClasspathResourceResolver;
import com.cp.ecommerce.foundation.exception.FailureMessages;
import com.cp.ecommerce.foundation.exception.TechnicalProblemException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.fop.apps.FOPException;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopConfParser;
import org.apache.fop.apps.FopFactory;
import org.xml.sax.SAXException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static org.apache.xmlgraphics.util.MimeConstants.MIME_PDF;

/**
 * PDF generator used in FreeMarker templates' processing.
 */
@Slf4j
@RequiredArgsConstructor
public class PdfGenerator {

    static final String GENERATION_ERROR = "Error generating the PDF from a rendered template of %d characters (causes=%s)";

    private final transient TransformerFactory transformerFactory;

    private final transient FopFactory fopFactory;

    private final transient Object iccColorsLock = new Object();

    @Getter(AccessLevel.PACKAGE)
    private boolean iccColorsInitialized = false;

    public PdfGenerator(
            @Value("classpath:/fop.xml") final Resource fopConfigXml,
            final ClasspathResourceResolver resourceResolver) {

        transformerFactory = TransformerFactory.newInstance();

        try {
            final FopConfParser parser = new FopConfParser(
                    fopConfigXml.getInputStream(),
                    URI.create("file:/"),
                    resourceResolver);
            fopFactory = parser.getFopFactoryBuilder().build();
        } catch (final IOException | SAXException ex) {
            throw new TechnicalProblemException("Error instantiating the PDF generator", ex);
        }
    }

    /**
     * Generates a PDF based upon a given template.
     */
    public byte[] generatePdf(final String templateContent) {

        if (StringUtils.isNotBlank(templateContent)) {

            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

                final Transformer transformer = transformerFactory.newTransformer();
                // The default listener prints parser errors, which can quote the rendered template, to standard error.
                transformer.setErrorListener(new QuietErrorListener());
                final StreamSource source = new StreamSource(new StringReader(templateContent));
                transformer.transform(source, new SAXResult(getFop(bos).getDefaultHandler()));
                return bos.toByteArray();
            } catch (final IOException | FOPException | TransformerException ex) {
                // The rendered template holds customer data, and FOP messages can quote it, so the failure names only the
                // template's size and the cause classes and does not keep the cause.
                throw new TechnicalProblemException(
                        GENERATION_ERROR.formatted(templateContent.length(), FailureMessages.causeNames(ex)));
            }
        }
        log.error("Template content is null or empty - cannot proceed with PDF generation");
        return ArrayUtils.EMPTY_BYTE_ARRAY;
    }

    private Fop getFop(final ByteArrayOutputStream bos) throws FOPException, IOException {

        synchronized (iccColorsLock) {
            if (!iccColorsInitialized) {
                try (ByteArrayOutputStream unusedBos = new ByteArrayOutputStream()) {
                    fopFactory.newFop(MIME_PDF, unusedBos);
                    iccColorsInitialized = true;
                }
            }
        }
        return fopFactory.newFop(MIME_PDF, bos);
    }

    /** Reports transformation problems only by throwing them, so nothing from the document reaches standard error. */
    static final class QuietErrorListener implements ErrorListener {

        @Override
        public void warning(final TransformerException exception) {
            // Warnings do not stop the transformation and their text can quote the document, so they are dropped.
        }

        @Override
        public void error(final TransformerException exception) throws TransformerException {

            throw exception;
        }

        @Override
        public void fatalError(final TransformerException exception) throws TransformerException {

            throw exception;
        }
    }
}
