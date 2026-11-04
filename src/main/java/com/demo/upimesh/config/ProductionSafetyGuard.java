package com.demo.upimesh.config;

import java.util.Arrays;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/** Fail-fast checks for settings that must never be enabled on a production profile. */
public final class ProductionSafetyGuard implements EnvironmentPostProcessor, Ordered {
    private static final Logger log = LoggerFactory.getLogger(ProductionSafetyGuard.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        validate(environment);
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }

    public static void validate(ConfigurableEnvironment environment) {
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile));
        if (!production) return;

        if (environment.getProperty("upi.mesh.demo.seed-accounts", Boolean.class, false)
                || environment.getProperty("upi.mesh.demo.endpoints-enabled", Boolean.class, false)) {
            throw new IllegalStateException(
                    "Production startup blocked: demo accounts and demo endpoints must both be disabled.");
        }

        String datasourceUrl = environment.getProperty("spring.datasource.url", "").trim().toLowerCase(Locale.ROOT);
        if (datasourceUrl.startsWith("jdbc:h2:")) {
            throw new IllegalStateException(
                    "Production startup blocked: H2 is not a supported production database; configure DB_URL for MySQL.");
        }

        if (environment.getProperty("app.security.trusted-proxy-addresses", "").isBlank()) {
            log.warn("Production has no trusted proxy addresses configured. Behind a load balancer, all users may share one rate-limit IP.");
        }
    }
}
