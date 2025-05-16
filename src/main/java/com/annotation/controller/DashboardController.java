package com.annotation.controller;

import com.annotation.model.CoupleText;
import com.annotation.model.Dataset;
import com.annotation.model.Task;
import com.annotation.model.User;
import com.annotation.repository.DatasetRepository;
import com.annotation.repository.TaskRepository;
import com.annotation.repository.UserRepository;
import com.annotation.service.DefaultUserServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {
    private static final Logger logger = Logger.getLogger(DashboardController.class.getName());

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private TaskRepository taskRepository;
    
    @Autowired
    private DefaultUserServiceImpl userService;
    
    @Autowired
    private DatasetRepository datasetRepository;
    
    @PersistenceContext
    private EntityManager entityManager;

    @GetMapping
    @Transactional(readOnly = true)
    public String displayDashboard(Model model) {
        // Get current user
        String username = userService.getCurrentUserName();
        User currentUser = userService.findUserByUsername(username);
        
        if (currentUser == null) {
            model.addAttribute("errorMessage", "User not found. Please log in again.");
            return "redirect:/login";
        }
        
        // Check if user is an admin
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
        
        List<Task> tasks;
        
        if (isAdmin) {
            // For admin users, use direct JDBC query to bypass any JPA/Hibernate issues
            logger.info("Admin user detected, loading all tasks using direct JDBC");
            tasks = new ArrayList<>();
            
            try {
                // Direct JDBC query without any mapping
                Query directQuery = entityManager.createNativeQuery(
                    "SELECT t.id, t.date_limite, t.user_id, t.dataset_id FROM tasks t");
                
                List<Object[]> rawResults = directQuery.getResultList();
                logger.info("DIRECT JDBC QUERY found " + rawResults.size() + " tasks");
                
                if (rawResults.isEmpty()) {
                    logger.warning("No tasks found in the database - query returned empty results!");
                }
                
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
                            logger.info("Task #" + taskCounter + " - Set user: " + user.getFirstname() + " " + user.getLastname() + " for task: " + taskId);
                        } else {
                            logger.warning("Task #" + taskCounter + " - User with ID " + userId + " not found!");
                        }
                    }
                    
                    // Load dataset if available
                    if (datasetId != null) {
                        Dataset dataset = datasetRepository.findById(datasetId).orElse(null);
                        if (dataset != null) {
                            task.setDataset(dataset);
                            logger.info("Task #" + taskCounter + " - Set dataset: " + dataset.getName() + " for task: " + taskId);
                        } else {
                            logger.warning("Task #" + taskCounter + " - Dataset with ID " + datasetId + " not found!");
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
                    logger.info("Task #" + taskCounter + " - Loaded " + couples.size() + " couples for task: " + taskId);
                    
                    tasks.add(task);
                    logger.info("Task #" + taskCounter + " - Added task to list, current size: " + tasks.size());
                }
                
                logger.info("FINAL ADMIN TASK LIST SIZE: " + tasks.size());
            } catch (Exception e) {
                logger.severe("Error fetching tasks with direct JDBC: " + e.getMessage());
                e.printStackTrace();
                tasks = new ArrayList<>(); // Reset to empty list in case of error
            }
        } else {
            // For regular users, only load their tasks
            logger.info("Regular user detected, loading user-specific tasks");
            tasks = taskRepository.findByUserIdWithAllRelations(currentUser.getId());
        }
        
        // Calculate task statistics
        int completedTasks = 0;
        int pendingTasks = 0;
        
        // Pre-calculate progress for each task
        Map<Long, Integer> taskProgress = new HashMap<>();
        
        logger.info("Starting to process " + (tasks != null ? tasks.size() : 0) + " tasks for progress calculation");
        
        if (tasks != null && !tasks.isEmpty()) {
            for (Task task : tasks) {
                logger.info("Processing task ID: " + task.getId() + " for progress calculation");
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
        if (tasks != null) {
            logger.info("Sorting " + tasks.size() + " tasks by deadline");
            Collections.sort(tasks, (t1, t2) -> {
                if (t1.getDateLimite() == null) return 1;
                if (t2.getDateLimite() == null) return -1;
                return t1.getDateLimite().compareTo(t2.getDateLimite());
            });
            logger.info("Sorting completed");
        }
        
        // Add attributes to model
        model.addAttribute("userDetails", currentUser.getFirstname());
        model.addAttribute("tasks", tasks);
        model.addAttribute("taskCount", tasks != null ? tasks.size() : 0); // Adding a separate counter for debugging
        model.addAttribute("completedTasks", completedTasks);
        model.addAttribute("pendingTasks", pendingTasks);
        model.addAttribute("taskProgress", taskProgress);
        model.addAttribute("isAdmin", isAdmin); // Add flag to indicate admin status
        
        logger.info("Added " + (tasks != null ? tasks.size() : 0) + " tasks to the model as 'tasks' attribute");
        
        return "dashboard";
    }
}
