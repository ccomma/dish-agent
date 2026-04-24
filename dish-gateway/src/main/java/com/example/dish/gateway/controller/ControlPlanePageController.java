package com.example.dish.gateway.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 控制面页面入口控制器。
 */
@Controller
public class ControlPlanePageController {

    @GetMapping("/control/dashboard")
    public String dashboardPage() {
        return "forward:/control-dashboard.html";
    }
}
