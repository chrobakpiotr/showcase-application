package com.cp.ecommerce.adapter.mail.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;

import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.adapter.mail.pdf.utils.ClasspathResourceResolver;

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

                transformerFactory.newTransformer()
                        .transform(
                                new StreamSource(new StringReader(templateContent)),
                                new SAXResult(getFop(bos).getDefaultHandler()));
                return bos.toByteArray();
            } catch (final IOException | FOPException | TransformerException ex) {
                throw new TechnicalProblemException("Error generating the PDF for template '" + templateContent + "'", ex);
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

}
