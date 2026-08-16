package com.cp.ecommerce.adapter.mail.message;

import java.util.Optional;

import com.cp.ecommerce.adapter.mail.freemarker.FreeMarkerTemplateProcessor;
import com.cp.ecommerce.adapter.mail.pdf.PdfGenerator;
import com.cp.ecommerce.adapter.mail.pdf.utils.ClasspathResourceResolver;
import com.cp.ecommerce.domain.order.Order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.Getter;
import lombok.Setter;

import static org.springframework.mail.javamail.MimeMessageHelper.MULTIPART_MODE_MIXED;

/**
 * Abstract class for creating message.
 */
@Component
abstract class AbstractMessageCreator implements MessageCreationStrategy {

    private static final String PDF_ATTACHMENT_FILENAME_PREFIX = "order_";

    private static final String PDF_ATTACHMENT_FILENAME_SUFFIX = "_confirmation.pdf";

    private final transient JavaMailSender emailSender;
    private final transient FreeMarkerTemplateProcessor freeMarkerTemplateProcessor;
    private final transient ClasspathResourceResolver resourceResolver;

    @Getter
    @Setter
    String templatePathEmail;
    @Getter
    @Setter
    String templateFileNameEmail;
    @Getter
    @Setter
    String templatePathPdf;
    @Getter
    @Setter
    String templateFileNamePdf;
    @Value("${spring.mail.username:}")
    private transient String from;
    @Value("${spring.mail.default-encoding:}")
    private transient String defaultEmailEncoding;
    @Value("${spring.mail.template.order-confirmation:}")
    private transient String templateFileName;
    @Value("${spring.mail.template.pdf-path}")
    private transient String templatePathName;
    @Value("classpath:/fop.xml")
    private transient Resource fopXml;

    protected AbstractMessageCreator(
            final JavaMailSender emailSender,
            final FreeMarkerTemplateProcessor freeMarkerTemplateProcessor,
            final ClasspathResourceResolver resourceResolver) {

        this.emailSender = emailSender;
        this.freeMarkerTemplateProcessor = freeMarkerTemplateProcessor;
        this.resourceResolver = resourceResolver;
    }

    public MimeMessage createMessage(final Order order) throws MessagingException {

        final MimeMessage message = emailSender.createMimeMessage();
        final MimeMessageHelper helper = new MimeMessageHelper(message, MULTIPART_MODE_MIXED, defaultEmailEncoding);

        helper.setFrom(from);
        helper.setTo(prepareRecipient(order));
        helper.setSubject(createSubject(order));
        helper.setText(getContentFromTemplateEmail(order), true);

        final Optional<byte[]> pdf = generatePdfAttachment(order);
        if (pdf.isPresent()) {
            helper.addAttachment(
                    PDF_ATTACHMENT_FILENAME_PREFIX + order.getOrderNumber() + PDF_ATTACHMENT_FILENAME_SUFFIX,
                    new ByteArrayDataSource(pdf.get(), "application/pdf"));
        }

        return message;
    }

    /**
     * Creating message body based on placed order and template file.
     *
     * @param order placed order.
     * @return content of message.
     */
    String getContentFromTemplateEmail(final Order order) {
        prepareTemplateInformationForEmail();
        return freeMarkerTemplateProcessor.processTemplate(getTemplateFileNameEmail(), getTemplatePathEmail(), order);
    }

    /**
     * Creating content for pdf based on placed order and template file.
     *
     * @param order placed order.
     * @return content of message.
     */
    String getContentFromTemplatePdf(final Order order) {
        prepareTemplateInformationForPdf();
        return freeMarkerTemplateProcessor.processTemplate(getTemplateFileNamePdf(), getTemplatePathPdf(), order);
    }

    /**
     * Set values for {@link #templateFileNameEmail} and {@link #templatePathEmail} while creating mail.
     */
    abstract void prepareTemplateInformationForEmail();

    /**
     * Set values for {@link #templateFileNamePdf} and {@link #templatePathPdf} while creating pdf.
     */
    void prepareTemplateInformationForPdf() {

        setTemplateFileNamePdf(templateFileName);
        setTemplatePathPdf(templatePathName);
    }

    /**
     * Generating PDF attachment for email.
     *
     * @param order placed order.
     * @return byte array.
     */
    Optional<byte[]> generatePdfAttachment(final Order order) {
        prepareTemplateInformationForPdf();
        return Optional.of(new PdfGenerator(fopXml, resourceResolver).generatePdf(getContentFromTemplatePdf(order)));
    }

    /**
     * Create subject of message.
     *
     * @param order placed order.
     * @return subject.
     */
    abstract String createSubject(Order order);

    /**
     * Prepare a mail address for the recipient of the message.
     *
     * @param order placed order.
     * @return mail address.
     */
    abstract String prepareRecipient(Order order);

}
