package com.demo.upimesh.config;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.AppUserRepository;
import com.demo.upimesh.service.SessionTokenService;
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

    @Test
    void anonymousSimulatorAndBridgeRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/mesh/state")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/bridge/ingest")).andExpect(status().isUnauthorized());
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
}
