package com.cp.ecommerce.adapter.mail.freemarker;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.cp.ecommerce.adapter.mail.freemarker.formatter.DefaultLocalDateFormatter;
import com.cp.ecommerce.adapter.mail.pdf.constant.PdfConstants;
import com.cp.ecommerce.adapter.mail.pdf.ftl.ContactFtl;
import com.cp.ecommerce.domain.order.Order;

import org.apache.commons.lang3.StringUtils;

import org.springframework.stereotype.Component;

import freemarker.template.ObjectWrapper;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import lombok.RequiredArgsConstructor;

/**
 * Factory used for inputting values in order confirmation PDF template file.
 */
@Component
@RequiredArgsConstructor
public class OrderCustomObjectWrapperFactory implements CustomObjectWrapperFactory {

    private final transient FreeMarkerMessageSource messageSource;

    @Override
    public OrderCustomObjectWrapper createWrapperBasedOnModelType(
            final Object model,
            final FreeMarkerCustomObjectWrapper freemarkerCustomObjectWrapper) {

        if (model instanceof Order || model instanceof Map || isOptionalWithOrder(model)) {
            return new OrderCustomObjectWrapper(freemarkerCustomObjectWrapper);
        }
        return null;
    }

    private boolean isOptionalWithOrder(final Object object) {

        if (!(object instanceof Optional<?>)) {
            return false;
        }
        final Optional<?> optional = (Optional<?>) object;
        return optional.isPresent() && optional.get() instanceof Order;
    }

    public class OrderCustomObjectWrapper implements ObjectWrapper {

        private final transient FreeMarkerCustomObjectWrapper freemarkerCustomObjectWrapper;

        OrderCustomObjectWrapper(final FreeMarkerCustomObjectWrapper objectWrapper) {

            freemarkerCustomObjectWrapper = objectWrapper;
        }

        @Override
        public TemplateModel wrap(final Object obj) throws TemplateModelException {

            if (obj instanceof Order order) {
                return freemarkerCustomObjectWrapper.wrap(convertOrder(order));
            }
            return freemarkerCustomObjectWrapper.wrap(obj);
        }

        Object convertOrder(final Order order) {

            final Map<String, Object> parameters = new HashMap<>();
            parameters.put(PdfConstants.CURRENT_DATE, DefaultLocalDateFormatter.formatDate(LocalDate.now()));
            parameters.put(PdfConstants.CONTACT, ContactFtl.of(order.getCustomer()));
            parameters.put(PdfConstants.MESSAGE, messageSource.templateMethodModel());
            parameters.put(
                    PdfConstants.ORDER_NUMBER,
                    StringUtils.isNotBlank(order.getOrderNumber()) ? order.getOrderNumber() : 0);
            parameters.put(PdfConstants.REMARKS, order.getRemarks());
            return parameters;
        }

    }

}
