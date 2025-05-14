package com.annotation.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object errorMessage = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        
        model.addAttribute("status", status != null ? status : "Unknown");
        model.addAttribute("error", request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));
        model.addAttribute("message", errorMessage != null ? errorMessage : 
                          (exception != null ? exception.toString() : "An unexpected error occurred"));
        
        return "error";
    }
} 