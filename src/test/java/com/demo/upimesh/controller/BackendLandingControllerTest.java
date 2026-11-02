package com.demo.upimesh.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "upi.mesh.demo.seed-accounts=true",
        "upi.mesh.demo.endpoints-enabled=false"
})
@AutoConfigureMockMvc
class BackendLandingControllerTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void apiLandingRemainsAvailableWhenDemoDashboardIsDisabled() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.health").value("/api/health"));
        mockMvc.perform(get("/api/demo/accounts")).andExpect(status().isUnauthorized());
    }
}
