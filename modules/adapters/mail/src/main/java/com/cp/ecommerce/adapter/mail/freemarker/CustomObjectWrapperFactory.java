package com.cp.ecommerce.adapter.mail.freemarker;

import freemarker.template.ObjectWrapper;

/**
 * Factory interface used for wrapping values in PDF template files.
 */
public interface CustomObjectWrapperFactory {

    ObjectWrapper createWrapperBasedOnModelType(
            final Object model,
            final FreeMarkerCustomObjectWrapper freemarkerCustomObjectWrapper);

}
