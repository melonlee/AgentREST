package com.fantasyailab.agentrest.common.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * Fails the {@code prod} profile at startup - before any bean (DataSource, JWT, ...) is created -
 * with one clear message listing every missing environment variable.
 */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProdConfigurationGuard {

    static final List<String> REQUIRED = List.of("DB_URL", "DB_USERNAME", "JWT_SECRET");
    static final List<String> REQUIRED_MAY_BE_EMPTY = List.of("DB_PASSWORD");

    @Bean
    static BeanFactoryPostProcessor prodEnvironmentGuard() {
        return ProdConfigurationGuard::verify;
    }

    static void verify(ConfigurableListableBeanFactory beanFactory) {
        Environment environment = beanFactory.getBean(Environment.class);
        List<String> missing = new ArrayList<>();
        for (String key : REQUIRED) {
            String value = environment.getProperty(key);
            if (value == null || value.isBlank()) {
                missing.add(key);
            }
        }
        for (String key : REQUIRED_MAY_BE_EMPTY) {
            if (!environment.containsProperty(key)) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("The 'prod' profile requires these environment variables: "
                    + String.join(", ", missing) + ". See README > Configuration (or .env.example).");
        }
    }
}
