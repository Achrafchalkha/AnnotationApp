package com.annotation.controller;

import com.annotation.model.ClassPossible;
import com.annotation.model.CoupleText;
import com.annotation.model.Task;
import com.annotation.model.User;
import com.annotation.repository.CoupleTextRepository;
import com.annotation.repository.TaskRepository;
import com.annotation.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.logging.Logger;

@Controller
@RequestMapping("/admin/tasks")
public class AdminTaskReviewController {
    private static final Logger logger = Logger.getLogger(AdminTaskReviewController.class.getName());

    @Autowired
    private TaskRepository taskRepository;
    
    @Autowired
    private CoupleTextRepository coupleTextRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * Display the review page for a specific task's annotations
     */
    @GetMapping("/{taskId}/review")
    @Transactional(readOnly = true)
    public String reviewTaskAnnotations(@PathVariable Long taskId, 
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "5") int size,
                                       Model model) {
        try {
            // Add the current username to the model
            String username = returnUsername();
            model.addAttribute("currentUserName", username);
            
            // Get task with all relationships loaded
            Task task = taskRepository.findByIdWithCouples(taskId);
            
            if (task == null) {
                model.addAttribute("errorMessage", "Task not found with ID: " + taskId);
                return "admin/task_review";
            }
            
            // Filter only annotated couples (text pairs)
            List<CoupleText> annotatedPairs = task.getCouples().stream()
                .filter(couple -> couple != null && couple.getClassAnnotation() != null && !couple.getClassAnnotation().isEmpty())
                .collect(Collectors.toList());
            
            // Calculate pagination values
            int totalPairs = annotatedPairs.size();
            int totalPages = (int) Math.ceil((double) totalPairs / size);
            
            // Apply pagination (simple in-memory pagination)
            List<CoupleText> paginatedPairs;
            if (!annotatedPairs.isEmpty()) {
                int fromIndex = Math.min(page * size, totalPairs);
                int toIndex = Math.min((page + 1) * size, totalPairs);
                paginatedPairs = annotatedPairs.subList(fromIndex, toIndex);
            } else {
                paginatedPairs = annotatedPairs;
            }
            
            // Calculate progress percentage
            int totalCouples = task.getCouples().size();
            int annotatedCount = annotatedPairs.size();
            int progress = totalCouples > 0 ? (annotatedCount * 100) / totalCouples : 0;
            
            // Get all available classes for the dataset
            List<ClassPossible> allClasses = new ArrayList<>();
            if (task.getDataset() != null && task.getDataset().getClassesPossibles() != null) {
                allClasses = new ArrayList<>(task.getDataset().getClassesPossibles());
            }
            
            // Log information for debugging
            logger.info("Task ID: " + taskId + " has " + totalCouples + 
                      " total couples and " + annotatedCount + " annotated couples");
            
            // Add data to the model
            model.addAttribute("task", task);
            model.addAttribute("annotatedPairs", paginatedPairs);
            model.addAttribute("allClasses", allClasses);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("progress", progress);
            
            return "admin/task_review";
        } catch (Exception e) {
            // Log any unexpected errors
            logger.severe("Error reviewing task annotations: " + e.getMessage());
            e.printStackTrace();
            
            // Show the error on the same page
            model.addAttribute("currentUserName", returnUsername());
            model.addAttribute("errorMessage", "An error occurred while loading task annotations: " + e.getMessage());
            
            return "admin/task_review";
        }
    }

    /**
     * Update the annotation for a text pair
     */
    @PostMapping("/{taskId}/review/update")
    @Transactional
    public String updateAnnotation(
            @PathVariable Long taskId,
            @RequestParam("pairId") Long pairId,
            @RequestParam(value = "classIds", required = false) List<Long> classIds,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Find the couple text by its ID
            Optional<CoupleText> coupleOptional = coupleTextRepository.findById(pairId);
            
            if (coupleOptional.isPresent()) {
                CoupleText couple = coupleOptional.get();
                
                // Get the task
                Task task = taskRepository.findById(taskId).orElse(null);
                if (task == null || task.getDataset() == null) {
                    redirectAttributes.addFlashAttribute("errorMessage", "Task or dataset not found");
                    return "redirect:/admin/tasks/" + taskId + "/review";
                }
                
                // Build new classification string from selected classes
                StringBuilder newClassification = new StringBuilder();
                
                if (classIds != null && !classIds.isEmpty()) {
                    // Get the class names from class IDs
                    List<String> classNames = new ArrayList<>();
                    
                    for (Long classId : classIds) {
                        for (ClassPossible classPossible : task.getDataset().getClassesPossibles()) {
                            if (classPossible.getId().equals(classId)) {
                                classNames.add(classPossible.getTextClass());
                                break;
                            }
                        }
                    }
                    
                    // Join class names with commas
                    newClassification.append(String.join(", ", classNames));
                }
                
                // Log before update
                logger.info("Updating classification for couple #" + pairId + 
                           " from '" + couple.getClassAnnotation() + "' to '" + newClassification + "'");
                
                // Update the classification
                couple.setClassAnnotation(newClassification.toString());
                
                // Save the updated couple
                coupleTextRepository.save(couple);
                
                // Add success message
                redirectAttributes.addFlashAttribute("successMessage", 
                    "Classification for pair #" + pairId + " updated successfully");
            } else {
                // Add error message if couple not found
                redirectAttributes.addFlashAttribute("errorMessage", "Text pair not found with ID: " + pairId);
            }
            
        } catch (Exception e) {
            // Log any unexpected errors
            logger.severe("Error updating classification: " + e.getMessage());
            e.printStackTrace();
            
            // Add error message
            redirectAttributes.addFlashAttribute("errorMessage", 
                "An error occurred while updating the classification: " + e.getMessage());
        }
        
        // Redirect back to the review page
        return "redirect:/admin/tasks/" + taskId + "/review";
    }
    
    /**
     * Helper method to get the current user's username
     */
    private String returnUsername() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        UserDetails user = (UserDetails) securityContext.getAuthentication().getPrincipal();
        User users = userRepository.findByUsername(user.getUsername()).orElse(null);
        return users != null ? users.getFirstname() : "";
    }
} 