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
        "upi.mesh.demo.endpoints-enabled=true"
})
@AutoConfigureMockMvc
class DemoDashboardApiTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void localDashboardCanLoadSimulatorAndLedgerDataWhenDemoModeIsEnabled() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/api/mesh/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devices.length()").value(5));
        mockMvc.perform(get("/api/demo/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].balance").exists());
        mockMvc.perform(get("/api/demo/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
