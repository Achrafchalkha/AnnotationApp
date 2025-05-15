package com.annotation.controller;

import com.annotation.model.CoupleText;
import com.annotation.model.Task;
import com.annotation.model.User;
import com.annotation.repository.TaskRepository;
import com.annotation.repository.UserRepository;
import com.annotation.service.DefaultUserServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private TaskRepository taskRepository;
    
    @Autowired
    private DefaultUserServiceImpl userService;

    @GetMapping
    public String displayDashboard(Model model) {
        // Get current user
        String username = userService.getCurrentUserName();
        User currentUser = userService.findUserByUsername(username);
        
        if (currentUser == null) {
            model.addAttribute("errorMessage", "User not found. Please log in again.");
            return "redirect:/login";
        }
        
        // Load user's tasks with all relations
        List<Task> userTasks = taskRepository.findByUserIdWithAllRelations(currentUser.getId());
        
        // Calculate task statistics
        int completedTasks = 0;
        int pendingTasks = 0;
        
        // Pre-calculate progress for each task
        java.util.Map<Long, Integer> taskProgress = new java.util.HashMap<>();
        
        if (userTasks != null && !userTasks.isEmpty()) {
            for (Task task : userTasks) {
                if (task.getCouples() != null && !task.getCouples().isEmpty()) {
                    // Calculate progress percentage
                    int totalPairs = task.getCouples().size();
                    int annotatedPairs = 0;
                    
                    // A task is considered completed when all text pairs have annotations
                    boolean allAnnotated = true;
                    
                    for (CoupleText couple : task.getCouples()) {
                        if (couple.getClassAnnotation() != null && !couple.getClassAnnotation().isEmpty()) {
                            annotatedPairs++;
                        } else {
                            allAnnotated = false;
                        }
                    }
                    
                    // Store progress percentage
                    int progressPercentage = totalPairs > 0 ? (annotatedPairs * 100) / totalPairs : 0;
                    taskProgress.put(task.getId(), progressPercentage);
                    
                    if (allAnnotated) {
                        completedTasks++;
                    } else {
                        pendingTasks++;
                    }
                } else {
                    // If task has no couples, consider it pending and set progress to 0
                    pendingTasks++;
                    taskProgress.put(task.getId(), 0);
                }
            }
        }
        
        // Sort tasks by deadline (most recent first)
        if (userTasks != null) {
            Collections.sort(userTasks, (t1, t2) -> {
                if (t1.getDateLimite() == null) return 1;
                if (t2.getDateLimite() == null) return -1;
                return t1.getDateLimite().compareTo(t2.getDateLimite());
            });
        }
        
        // Add attributes to model
        model.addAttribute("userDetails", currentUser.getFirstname());
        model.addAttribute("tasks", userTasks);
        model.addAttribute("completedTasks", completedTasks);
        model.addAttribute("pendingTasks", pendingTasks);
        model.addAttribute("taskProgress", taskProgress);
        
        return "dashboard";
    }
}
