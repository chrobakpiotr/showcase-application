package com.cp.ecommerce.adapter.mail.configuration;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static java.lang.Integer.parseInt;

/**
 * Configuration class responsible for mailing functionality.
 */
@Configuration
@ComponentScan({ "com.cp.ecommerce.adapter.mail.message", "com.cp.ecommerce.adapter.security" })
public class MessageTemplateConfiguration {

    private static final String TRANSLATIONS_PATH = "i18n/translations";

    @Value("${spring.mail.host:}")
    private transient String host;

    @Value("${spring.mail.port:}")
    private transient String port;

    @Value("${spring.mail.username:}")
    private transient String username;

    @Value("${spring.mail.password:}")
    private transient String password;

    @Value("${spring.mail.transport.protocol:}")
    private transient String protocol;

    @Value("${spring.mail.properties.mail.smtp.auth:}")
    private transient String auth;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable:}")
    private transient String startTlsEnabled;

    @Value("${spring.mail.debug:}")
    private transient String debug;

    @Value("${spring.mail.default-encoding:}")
    private transient String defaultEncoding;

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertiesResolver() {

        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    public ResourceBundleMessageSource messageSource() {

        final ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames(TRANSLATIONS_PATH);
        source.setUseCodeAsDefaultMessage(true);
        source.setFallbackToSystemLocale(false);
        source.setDefaultEncoding(defaultEncoding);

        return source;
    }

    @Bean
    public JavaMailSender getJavaMailSender() {

        final JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(parseInt(port));
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        final Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", protocol);
        props.put("mail.smtp.auth", auth);
        props.put("mail.smtp.starttls.enable", startTlsEnabled);
        props.put("mail.debug", debug);

        return mailSender;
    }

}
