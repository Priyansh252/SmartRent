package com.smartrent.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Server-rendered Thymeleaf shells. All data is loaded by the page scripts from the secured REST API. */
@Controller
public class PageController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/vehicles")
    public String vehicles() {
        return "vehicles";
    }

    @GetMapping("/vehicles/{id}")
    public String vehicle(@PathVariable Long id, Model model) {
        model.addAttribute("vehicleId", id);
        return "vehicle-detail";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/recommend")
    public String recommend() {
        return "recommend";
    }

    @GetMapping("/account")
    public String account() {
        return "account";
    }

    @GetMapping("/account/bookings/{id}")
    public String booking(@PathVariable Long id, Model model) {
        model.addAttribute("bookingId", id);
        return "booking-detail";
    }

    @GetMapping("/staff")
    public String staff() {
        return "staff";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }

    @GetMapping("/admin/pricing")
    public String adminPricing() {
        return "admin-pricing";
    }

    @GetMapping("/admin/reports")
    public String adminReports() {
        return "admin-reports";
    }
}
