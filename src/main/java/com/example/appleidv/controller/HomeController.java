package com.example.appleidv.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index(@RequestParam(value = "session", required = false) String sessionId,
                        Model model) {
        model.addAttribute("prefilledSessionId", sessionId);
        model.addAttribute("title", "Apple IDV Wallet Demo");
        return "index";
    }
}

