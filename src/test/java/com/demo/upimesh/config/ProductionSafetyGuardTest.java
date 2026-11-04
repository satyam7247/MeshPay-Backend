package com.demo.upimesh.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(OutputCaptureExtension.class)
class ProductionSafetyGuardTest {
    private MockEnvironment productionEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.datasource.url", "jdbc:mysql://db.example.test/upimesh");
        return environment;
    }

    @Test
    void refusesProductionWhenDemoAccountsAreEnabled() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("upi.mesh.demo.seed-accounts", "true");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ProductionSafetyGuard.validate(environment));

        assertTrue(error.getMessage().contains("demo accounts"));
    }

    @Test
    void refusesProductionWhenDemoEndpointsAreEnabled() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("upi.mesh.demo.endpoints-enabled", "true");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ProductionSafetyGuard.validate(environment));

        assertTrue(error.getMessage().contains("demo endpoints"));
    }

    @Test
    void refusesProductionH2Datasource() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("spring.datasource.url", "jdbc:h2:mem:production");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ProductionSafetyGuard.validate(environment));

        assertTrue(error.getMessage().contains("H2"));
    }

    @Test
    void warnsWhenProductionHasNoTrustedProxyButDoesNotFail(CapturedOutput output) {
        ProductionSafetyGuard.validate(productionEnvironment());

        assertTrue(output.getOut().contains("no trusted proxy addresses configured"));
        assertTrue(output.getOut().contains("all users may share one rate-limit IP"));
    }

    @Test
    void nonProductionProfileCanUseDemoSettingsAndH2() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        environment.setProperty("spring.datasource.url", "jdbc:h2:mem:development");
        environment.setProperty("upi.mesh.demo.seed-accounts", "true");

        assertDoesNotThrow(() -> ProductionSafetyGuard.validate(environment));
    }

    @Test
    void environmentPostProcessorCanBeInvokedBySpring() {
        assertDoesNotThrow(() -> new ProductionSafetyGuard().postProcessEnvironment(
                productionEnvironment(), new SpringApplication()));
    }
}
