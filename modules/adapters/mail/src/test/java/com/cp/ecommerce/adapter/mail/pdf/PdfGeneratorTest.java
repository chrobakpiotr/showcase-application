package com.cp.ecommerce.adapter.mail.pdf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.xml.transform.TransformerException;

import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.adapter.mail.pdf.utils.ClasspathResourceResolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xml.sax.SAXException;

import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Test cases for {@link PdfGenerator} class.
 */
@ExtendWith(MockitoExtension.class)
public class PdfGeneratorTest {

    private static final String FOP_XML = "<fop version=\"1.0\"></fop>";

    private static final String TEMPLATE_FILE_PATH = "./src/test/resources/templates/test-pdf-template.fo.ftl";

    @Mock
    private transient Resource fopConfigXml;

    @Mock
    private transient ClasspathResourceResolver resourceResolver;

    @Test
    public void shouldWrapIoException() throws IOException {

        doThrow(IOException.class).when(fopConfigXml).getInputStream();
        assertThatThrownBy(() -> new PdfGenerator(fopConfigXml, resourceResolver)).isInstanceOf(TechnicalProblemException.class)
                .hasCauseInstanceOf(IOException.class)
                .hasMessage("Error instantiating the PDF generator");
    }

    @Test
    public void shouldWrapSaxException() throws IOException {

        when(fopConfigXml.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] {}));
        assertThatThrownBy(() -> new PdfGenerator(fopConfigXml, resourceResolver)).isInstanceOf(TechnicalProblemException.class)
                .hasCauseInstanceOf(SAXException.class)
                .hasMessage("Error instantiating the PDF generator");
    }

    @Test
    public void shouldThrowExceptionWhenTemplateSyntaxIsBad() throws Exception {

        when(fopConfigXml.getInputStream()).thenReturn(new ByteArrayInputStream(FOP_XML.getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> new PdfGenerator(fopConfigXml, resourceResolver).generatePdf("badly-formed-template"))
                .isInstanceOf(TechnicalProblemException.class)
                .hasCauseInstanceOf(TransformerException.class)
                .hasMessage("Error generating the PDF for template 'badly-formed-template'");
    }

    @Test
    public void shouldReturnEmptyByteArrayWhenTemplateIsNull() throws IOException {

        when(fopConfigXml.getInputStream()).thenReturn(new ByteArrayInputStream(FOP_XML.getBytes(StandardCharsets.UTF_8)));
        assertThat(new PdfGenerator(fopConfigXml, resourceResolver).generatePdf(null)).isEmpty();
    }

    @Test
    public void shouldGeneratePdf() throws Exception {

        when(fopConfigXml.getInputStream()).thenReturn(new ByteArrayInputStream(FOP_XML.getBytes(StandardCharsets.UTF_8)));
        assertThat(new PdfGenerator(fopConfigXml, resourceResolver).generatePdf(readTemplateFile())).isNotNull();
    }

    @Test
    public void shouldInitFopOnlyOnce() throws Exception {

        when(fopConfigXml.getInputStream()).thenReturn(new ByteArrayInputStream(FOP_XML.getBytes(StandardCharsets.UTF_8)));
        final PdfGenerator generator = new PdfGenerator(fopConfigXml, resourceResolver);
        final String template = readTemplateFile();
        assertThat(generator.isIccColorsInitialized()).isFalse();

        generator.generatePdf(template);
        assertThat(generator.isIccColorsInitialized()).isTrue();
        generator.generatePdf(template);
        assertThat(generator.isIccColorsInitialized()).isTrue();
    }

    private String readTemplateFile() throws IOException {

        final byte[] bytes = Files.readAllBytes(Paths.get(TEMPLATE_FILE_PATH));
        return new String(bytes, StandardCharsets.UTF_8); // SUPPRESS CHECKSTYLE IllegalInstantiation
    }

}
