package com.demo.upimesh.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnProperty(prefix="upi.mesh.demo", name={"seed-accounts", "endpoints-enabled"}, havingValue="true")
public class DashboardController {

    @GetMapping("/")
    public String home() {
        return "dashboard";
    }
}
