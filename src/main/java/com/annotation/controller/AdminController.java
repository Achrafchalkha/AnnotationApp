package com.annotation.controller;


import com.annotation.model.Dataset;
import com.annotation.model.Task;
import com.annotation.model.User;
import com.annotation.model.CoupleText;
import com.annotation.repository.DatasetRepository;
import com.annotation.repository.TaskRepository;
import com.annotation.repository.UserRepository;
import com.annotation.repository.CoupleTextRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.logging.Logger;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private static final Logger logger = Logger.getLogger(AdminController.class.getName());

    @Autowired
    UserRepository userRepository;
    
    @Autowired
    TaskRepository taskRepository;
    
    @Autowired
    DatasetRepository datasetRepository;
    
    @Autowired
    CoupleTextRepository coupleTextRepository;

    @Autowired
    EntityManager entityManager;

    // Affichage du dashboard admin
    @GetMapping
    public String displayDashboard(Model model) {
        String username = returnUsername();
        model.addAttribute("currentUserName", username);
        return "admin/adminScreen";
    }

    // Display all tasks for all annotators
    @GetMapping("/tasks")
    @Transactional(readOnly = true)
    public String displayAllTasks(Model model) {
        try {
            logger.info("Starting displayAllTasks method");
            
            // Add the current username to the model
            String username = returnUsername();
            model.addAttribute("currentUserName", username);
            
            // ULTRA DIRECT approach to bypass any JPA/Hibernate issues
            List<Task> allTasks = new ArrayList<>();
            
            // First try with a simple count to see how many tasks exist
            Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM tasks");
            Number taskCount = (Number) countQuery.getSingleResult();
            logger.info("Total task count in database: " + taskCount);
            
            // Now let's see the specific task IDs in the database
            Query idQuery = entityManager.createNativeQuery("SELECT id FROM tasks ORDER BY id");
            List<Number> taskIds = idQuery.getResultList();
            logger.info("Found " + taskIds.size() + " task IDs in database:");
            for (Number id : taskIds) {
                logger.info("Task ID: " + id);
            }
            
            // Direct JDBC query without any mapping
            Query directQuery = entityManager.createNativeQuery(
                "SELECT t.id, t.date_limite, t.user_id, t.dataset_id FROM tasks t ORDER BY t.id");
            
            List<Object[]> rawResults = directQuery.getResultList();
            logger.info("DIRECT JDBC QUERY found " + rawResults.size() + " tasks");
            
            // Create tasks directly from raw data
            int taskCounter = 0;
            for (Object[] row : rawResults) {
                taskCounter++;
                Long taskId = ((Number)row[0]).longValue();
                Date deadline = (Date)row[1];
                Long userId = row[2] != null ? ((Number)row[2]).longValue() : null;
                Long datasetId = row[3] != null ? ((Number)row[3]).longValue() : null;
                
                logger.info("Processing task #" + taskCounter + " - ID: " + taskId + ", UserID: " + userId + ", DatasetID: " + datasetId);
                
                // Create and populate task object
                Task task = new Task();
                task.setId(taskId);
                task.setDateLimite(deadline);
                
                // Load user if available
                if (userId != null) {
                    User user = userRepository.findById(userId).orElse(null);
                    if (user != null) {
                        task.setUser(user);
                        logger.info("Set user: " + user.getFirstname() + " " + user.getLastname() + " for task: " + taskId);
                    }
                }
                
                // Load dataset if available
                if (datasetId != null) {
                    Dataset dataset = datasetRepository.findById(datasetId).orElse(null);
                    if (dataset != null) {
                        task.setDataset(dataset);
                        logger.info("Set dataset: " + dataset.getName() + " for task: " + taskId);
                    }
                }
                
                // Load couples for this task
                Query couplesQuery = entityManager.createNativeQuery(
                    "SELECT c.id, c.text_1, c.text_2, c.class_annotation " +
                    "FROM task_couple tc " +
                    "JOIN couple_text c ON tc.couple_id = c.id " +
                    "WHERE tc.task_id = :taskId");
                couplesQuery.setParameter("taskId", taskId);
                
                List<Object[]> couplesResult = couplesQuery.getResultList();
                List<CoupleText> couples = new ArrayList<>();
                
                for (Object[] coupleRow : couplesResult) {
                    CoupleText couple = new CoupleText();
                    couple.setId(((Number)coupleRow[0]).longValue());
                    couple.setText1((String)coupleRow[1]);
                    couple.setText2((String)coupleRow[2]);
                    couple.setClassAnnotation((String)coupleRow[3]);
                    couples.add(couple);
                }
                
                task.setCouples(couples);
                logger.info("Loaded " + couples.size() + " couples for task: " + taskId);
                
                allTasks.add(task);
                logger.info("Added task ID " + taskId + " to allTasks list. Current size: " + allTasks.size());
            }
            
            // Log all tasks for debugging
            logger.info("Total tasks loaded: " + allTasks.size());
            for (Task task : allTasks) {
                logger.info("Task ID: " + task.getId() + 
                          ", User: " + (task.getUser() != null ? task.getUser().getFirstname() + " " + task.getUser().getLastname() : "null") + 
                          ", Dataset: " + (task.getDataset() != null ? task.getDataset().getName() : "null") +
                          ", Couples: " + (task.getCouples() != null ? task.getCouples().size() : 0));
            }
            
            // Pre-calculate progress for each task
            Map<Long, Integer> taskProgress = new HashMap<>();
            
            for (Task task : allTasks) {
                try {
                    if (task.getCouples() != null && !task.getCouples().isEmpty()) {
                        int totalPairs = task.getCouples().size();
                        int annotatedPairs = 0;
                        
                        // Count annotated pairs - add null check to prevent NPE
                        annotatedPairs = (int) task.getCouples().stream()
                            .filter(couple -> couple != null && couple.getClassAnnotation() != null && !couple.getClassAnnotation().isEmpty())
                            .count();
                        
                        // Calculate progress percentage - safely handle division by zero
                        int progressPercentage = totalPairs > 0 ? (annotatedPairs * 100) / totalPairs : 0;
                        taskProgress.put(task.getId(), progressPercentage);
                        
                        logger.info("Task ID " + task.getId() + " progress calculation: " + 
                                  annotatedPairs + " annotated out of " + totalPairs + " pairs = " + progressPercentage + "%");
                    } else {
                        // If task has no couples, set progress to 0
                        taskProgress.put(task.getId(), 0);
                        logger.info("Task ID " + task.getId() + " has no couples, setting progress to 0%");
                    }
                } catch (Exception e) {
                    // If there's an error processing this task, log it and continue with the next one
                    logger.warning("Error calculating progress for task " + task.getId() + ": " + e.getMessage());
                    taskProgress.put(task.getId(), 0);
                }
            }
            
            // Get all datasets for filtering
            List<Dataset> datasets = datasetRepository.findAll();
            
            // Add data to the model
            model.addAttribute("allTasks", allTasks);
            model.addAttribute("taskCount", allTasks.size()); // Add explicit count for debugging
            model.addAttribute("taskProgress", taskProgress);
            model.addAttribute("datasets", datasets);
            
            logger.info("FINALIZED: Added " + allTasks.size() + " tasks to the model as 'allTasks' attribute");
            
            return "admin/all_tasks";
        } catch (Exception e) {
            // Log any unexpected errors
            logger.severe("Error displaying all tasks: " + e.getMessage());
            e.printStackTrace();
            
            // Show the error on the same page instead of redirecting to an error page
            model.addAttribute("currentUserName", returnUsername());
            model.addAttribute("errorMessage", "An error occurred while loading tasks: " + e.getMessage());
            model.addAttribute("allTasks", new ArrayList<>());
            model.addAttribute("taskProgress", new HashMap<>());
            model.addAttribute("datasets", new ArrayList<>());
            
            return "admin/all_tasks";
        }
    }

    // Simple test endpoint to verify template resolution
    @GetMapping("/tasks-test")
    public String tasksTest(Model model) {
        model.addAttribute("currentUserName", returnUsername());
        model.addAttribute("testMessage", "This is a test message");
        return "admin/all_tasks";
    }

    @GetMapping("/tasks-debug")
    public String tasksDebug(Model model) {
        try {
            // Add the current username to the model
            String username = returnUsername();
            model.addAttribute("currentUserName", username);
            model.addAttribute("testMessage", "Template rendering test");
            
            // Get all tasks with their relationships loaded eagerly using the new method
            List<Task> allTasks = taskRepository.findAllWithRelationships();
            logger.info("Found " + allTasks.size() + " tasks in the debug endpoint");
            
            // Pre-calculate progress for each task
            Map<Long, Integer> taskProgress = new HashMap<>();
            
            for (Task task : allTasks) {
                if (task.getCouples() != null) {
                    taskProgress.put(task.getId(), 0); // Simplified progress calculation for debug
                }
            }
            
            // Get all datasets for filtering
            List<Dataset> datasets = datasetRepository.findAll();
            
            // Add data to the model
            model.addAttribute("allTasks", allTasks);
            model.addAttribute("taskProgress", taskProgress);
            model.addAttribute("datasets", datasets);
        } catch (Exception e) {
            logger.severe("Error in tasks debug: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("errorMessage", "Debug error: " + e.getMessage());
        }
        
        return "admin/tasks_debug";
    }

    // New endpoint to review task annotations
    @GetMapping("/tasks/{taskId}/review")
    @Transactional(readOnly = true)
    public String reviewTaskAnnotations(@PathVariable Long taskId, Model model) {
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
            List<CoupleText> annotatedCouples = task.getCouples().stream()
                .filter(couple -> couple != null && couple.getClassAnnotation() != null && !couple.getClassAnnotation().isEmpty())
                .collect(Collectors.toList());
            
            // Log information for debugging
            logger.info("Task ID: " + taskId + " has " + (task.getCouples() != null ? task.getCouples().size() : 0) + 
                      " total couples and " + annotatedCouples.size() + " annotated couples");
            
            // Add task and annotated couples to the model
            model.addAttribute("task", task);
            model.addAttribute("annotatedCouples", annotatedCouples);
            
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
    
    // Endpoint to update the classification of a couple text
    @PostMapping("/tasks/{taskId}/update-classification")
    @Transactional
    public String updateClassification(
            @PathVariable Long taskId,
            @RequestParam("coupleId") Long coupleId,
            @RequestParam("classification") String classification,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Find the couple text by its ID
            Optional<CoupleText> coupleOptional = coupleTextRepository.findById(coupleId);
            
            if (coupleOptional.isPresent()) {
                CoupleText couple = coupleOptional.get();
                
                // Log before update
                logger.info("Updating classification for couple #" + coupleId + 
                           " from '" + couple.getClassAnnotation() + "' to '" + classification + "'");
                
                // Update the classification
                couple.setClassAnnotation(classification);
                
                // Save the updated couple
                coupleTextRepository.save(couple);
                
                // Add success message
                redirectAttributes.addFlashAttribute("successMessage", 
                    "Classification for pair #" + coupleId + " updated successfully to: " + classification);
            } else {
                // Add error message if couple not found
                redirectAttributes.addFlashAttribute("errorMessage", "Text pair not found with ID: " + coupleId);
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

    private String returnUsername() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        UserDetails user = (UserDetails) securityContext.getAuthentication().getPrincipal();
        User users = userRepository.findByUsername(user.getUsername()).orElse(null);
        return users != null ? users.getFirstname() : "";
    }
}
