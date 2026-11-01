package com.lazy.rest.user;

import com.lazy.rest.auth.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Creates the first administrator from {@code LAZYREST_ADMIN_USERNAME / _EMAIL / _PASSWORD} when the
 * database contains no users yet. Does nothing otherwise, so it is safe to leave configured.
 */
@Component
@Order(0)
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final AuthProperties properties;
    private final UserRepository users;
    private final UserService userService;

    public BootstrapAdminInitializer(AuthProperties properties, UserRepository users, UserService userService) {
        this.properties = properties;
        this.users = users;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        AuthProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (admin == null || !admin.isConfigured()) {
            if (users.count() == 0) {
                log.warn("No users exist and no bootstrap admin is configured. "
                        + "Set LAZYREST_ADMIN_USERNAME, LAZYREST_ADMIN_EMAIL and LAZYREST_ADMIN_PASSWORD.");
            }
            return;
        }
        if (users.count() > 0) {
            return;
        }
        if (admin.password().length() < 12) {
            throw new IllegalStateException("LAZYREST_ADMIN_PASSWORD must be at least 12 characters");
        }
        userService.createUser(admin.username(), admin.email(), admin.password(), "Administrator", Set.of(Role.ADMIN));
        log.info("Created bootstrap admin user '{}'", admin.username());
    }
}
