package com.annotation.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for the landing page
 */
@Controller
public class LandingController {
    
    /**
     * Handles the root URL and renders the landing page
     */
    @GetMapping("/")
    public String showLandingPage() {
        return "landing";
    }
} 