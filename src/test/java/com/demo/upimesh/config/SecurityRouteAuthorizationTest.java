package com.demo.upimesh.config;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.AppUserRepository;
import com.demo.upimesh.service.SessionTokenService;
import com.demo.upimesh.service.AuthService;
import com.demo.upimesh.service.BrevoEmailService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "upi.mesh.demo.seed-accounts=false",
        "upi.mesh.demo.endpoints-enabled=false"
})
@AutoConfigureMockMvc
class SecurityRouteAuthorizationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository users;
    @Autowired private SessionTokenService sessions;
    @Autowired private AuthService authService;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockBean private BrevoEmailService email;

    @Test
    void anonymousSimulatorAndBridgeRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/mesh/state")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/bridge/ingest")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/app-lock")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/app-lock/enable").contentType("application/json")
                .content("{\"passwordType\":\"numeric\",\"password\":\"123456\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void simulatorRoutesRemainDisabledEvenForAuthenticatedUsersByDefault() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        AppUser user = users.saveAndFlush(new AppUser("Test User", suffix + "@example.test",
                "9" + suffix.substring(0, 9), "test-password-hash", suffix + "@meshpay"));
        String bearer = "Bearer " + sessions.issue(user.getId().toString());
        mockMvc.perform(get("/api/mesh/state").header("Authorization", bearer)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/mesh/reset").header("Authorization", bearer)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/demo/send").header("Authorization", bearer)).andExpect(status().isForbidden());
    }

    @Test
    void healthRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void enablingAppLockRestrictsProtectedRoutesUntilSessionUnlocks() throws Exception {
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,12);
        AppUser user=users.saveAndFlush(new AppUser("Lock User",suffix+"@example.test",
                "9"+suffix.substring(0,9),passwordEncoder.encode("account-pass"),suffix+"@meshpay"));
        String bearer="Bearer "+sessions.issue(user.getId().toString());
        sessions.setAppLockVerified(bearer.substring(7),user.getId().toString(),true);

        mockMvc.perform(get("/api/auth/me").header("Authorization",bearer)).andExpect(status().isOk());
        mockMvc.perform(post("/api/app-lock/enable").header("Authorization",bearer)
                .contentType("application/json").content("{\"accountPassword\":\"account-pass\",\"passwordType\":\"numeric\",\"password\":\"123456\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").header("Authorization",bearer)).andExpect(status().is(423));
        mockMvc.perform(get("/api/app-lock").header("Authorization",bearer)).andExpect(status().isOk());
        mockMvc.perform(post("/api/app-lock/verify").header("Authorization",bearer)
                .contentType("application/json").content("{\"password\":\"654321\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/auth/me").header("Authorization",bearer)).andExpect(status().is(423));
        mockMvc.perform(post("/api/app-lock/verify").header("Authorization",bearer)
                .contentType("application/json").content("{\"password\":\"123456\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").header("Authorization",bearer)).andExpect(status().isOk());
    }

    @Test
    void loginCompletesAfterPasswordVerifiedDeviceRegistration() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String email = suffix + "@example.test";
        String password = "StrongPass123!";
        authService.signup("Test User", email, "9" + suffix.substring(0, 9), password,
                UUID.randomUUID().toString(), "Test device", "web");
        var response = authService.login(email, password, UUID.randomUUID().toString(), "Test device", "web", "127.0.0.1");
        org.junit.jupiter.api.Assertions.assertNotNull(response.token());
    }
}
