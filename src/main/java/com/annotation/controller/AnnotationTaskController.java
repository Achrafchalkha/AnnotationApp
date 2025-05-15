package com.annotation.controller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.RequestMapping;

import com.annotation.model.CoupleText;
import com.annotation.model.Dataset;
import com.annotation.model.Task;
import com.annotation.model.User;
import com.annotation.model.ClassPossible;
import com.annotation.repository.CoupleTextRepository;
import com.annotation.repository.TaskRepository;
import com.annotation.service.DefaultUserServiceImpl;

@Controller
@RequestMapping("/user")
public class AnnotationTaskController {
    
    private static final Logger logger = Logger.getLogger(AnnotationTaskController.class.getName());
    
    private final TaskRepository taskRepository;
    private final CoupleTextRepository coupleTextRepository;
    private final DefaultUserServiceImpl userService;
    private final DataSource dataSource;
    
    @Autowired
    public AnnotationTaskController(
            TaskRepository taskRepository,
            CoupleTextRepository coupleTextRepository,
            DefaultUserServiceImpl userService,
            DataSource dataSource) {
        this.taskRepository = taskRepository;
        this.coupleTextRepository = coupleTextRepository;
        this.userService = userService;
        this.dataSource = dataSource;
    }
    
    /**
     * Display all tasks assigned to the current user
     */
    @GetMapping("/tasks")
    public String showUserTasks(Model model) {
        // Get current user
        String username = userService.getCurrentUserName();
        User currentUser = userService.findUserByUsername(username);
        
        if (currentUser == null) {
            model.addAttribute("errorMessage", "User not found. Please log in again.");
            return "redirect:/login";
        }
        
        // Get tasks for this user with all relations loaded
        List<Task> userTasks = taskRepository.findByUserIdWithAllRelations(currentUser.getId());
        
        // Pre-calculate progress for each task
        java.util.Map<Long, Integer> taskProgress = new java.util.HashMap<>();
        for (Task task : userTasks) {
            if (task.getCouples() != null) {
                int totalPairs = task.getCouples().size();
                int annotatedPairs = 0;
                for (CoupleText pair : task.getCouples()) {
                    if (pair.getClassAnnotation() != null && !pair.getClassAnnotation().isEmpty()) {
                        annotatedPairs++;
                    }
                }
                int progressPercentage = totalPairs > 0 ? (annotatedPairs * 100) / totalPairs : 0;
                taskProgress.put(task.getId(), progressPercentage);
            }
        }
        
        model.addAttribute("tasks", userTasks);
        model.addAttribute("taskProgress", taskProgress);
        model.addAttribute("currentUser", currentUser);
        
        return "user/task_list";
    }
    
    /**
     * Display the annotation interface for a specific task
     */
    @GetMapping("/tasks/{taskId}")
    public String showTaskAnnotationInterface(@PathVariable Long taskId, Model model) {
        try {
            logger.info("Accessing task with ID: " + taskId);
            
            // Get current user
            String username = userService.getCurrentUserName();
            User currentUser = userService.findUserByUsername(username);
            
            if (currentUser == null) {
                logger.warning("Current user is null");
                model.addAttribute("errorMessage", "User not found. Please log in again.");
                return "redirect:/login";
            }
            
            logger.info("Current user: " + currentUser.getUsername() + " (ID: " + currentUser.getId() + ")");
            
            // Get the requested task with eagerly loaded couples to prevent LazyInitializationException
            Task task = taskRepository.findByIdWithCouples(taskId);
            
            if (task == null) {
                logger.warning("Task with ID " + taskId + " not found");
                model.addAttribute("errorMessage", "Task not found");
                return "redirect:/user/tasks";
            }
            
            logger.info("Found task: " + task.getId());
            
            // Check if task.getUser() is null
            if (task.getUser() == null) {
                logger.severe("Task " + taskId + " has no assigned user");
                model.addAttribute("errorMessage", "Task has no assigned user");
                return "redirect:/user/tasks";
            }
            
            // Security check: verify this task belongs to the current user
            if (!task.getUser().getId().equals(currentUser.getId())) {
                logger.warning("User " + currentUser.getId() + " attempted to access task " + taskId + " belonging to user " + task.getUser().getId());
                model.addAttribute("errorMessage", "You don't have permission to access this task");
                return "redirect:/user/tasks";
            }
            
            // Check if dataset is null
            if (task.getDataset() == null) {
                logger.severe("Task " + taskId + " has no associated dataset");
                model.addAttribute("errorMessage", "Task has no associated dataset");
                return "redirect:/user/tasks";
            }
            
            // Get text pairs for this task
            List<CoupleText> textPairs = task.getCouples();
            
            // Check if couples list is null
            if (textPairs == null) {
                logger.severe("Task " + taskId + " has null couples list");
                model.addAttribute("infoMessage", "No text pairs assigned to this task (null list)");
                textPairs = List.of(); // Initialize with empty list to avoid NPE
            } else {
                logger.info("Task " + taskId + " has " + textPairs.size() + " text pairs");
                
                // Check for any invalid text pairs
                for (int i = 0; i < textPairs.size(); i++) {
                    CoupleText couple = textPairs.get(i);
                    if (couple == null) {
                        logger.severe("Task " + taskId + " has null couple at index " + i);
                        continue;
                    }
                    
                    if (couple.getText1() == null || couple.getText2() == null) {
                        logger.severe("Task " + taskId + " has couple with null text: CoupleID=" + couple.getId());
                    }
                }
            }
            
            model.addAttribute("task", task);
            model.addAttribute("textPairs", textPairs);
            model.addAttribute("currentUser", currentUser);
            
            // If there are no text pairs, show an appropriate message
            if (textPairs.isEmpty()) {
                logger.warning("Task " + taskId + " has no text pairs");
                model.addAttribute("infoMessage", "No text pairs assigned to this task");
            }
            
            logger.info("Successfully prepared task annotation view for task " + taskId);
            return "user/task_annotation";
            
        } catch (Exception e) {
            logger.severe("Error accessing task " + taskId + ": " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("errorMessage", "An error occurred: " + e.getMessage());
            return "redirect:/user/tasks";
        }
    }
    
    /**
     * Annotate a single text pair and move to the next one
     */
    @PostMapping("/tasks/{taskId}/annotate")
    @Transactional
    public String annotateTextPair(
            @PathVariable Long taskId,
            @RequestParam("coupleId") Long coupleId,
            @RequestParam("annotation") String annotation,
            RedirectAttributes redirectAttributes) {
        
        try {
            logger.info("Annotating text pair " + coupleId + " in task " + taskId);
            
            // Get current user
            String username = userService.getCurrentUserName();
            User currentUser = userService.findUserByUsername(username);
            
            if (currentUser == null) {
                logger.warning("Current user is null during annotation");
                redirectAttributes.addFlashAttribute("errorMessage", "User not found. Please log in again.");
                return "redirect:/login";
            }
            
            // Get the task with couples loaded
            Task task = taskRepository.findByIdWithCouples(taskId);
            
            if (task == null) {
                logger.warning("Task " + taskId + " not found during annotation");
                redirectAttributes.addFlashAttribute("errorMessage", "Task not found");
                return "redirect:/user/tasks";
            }
            
            // Security check: verify this task belongs to the current user
            if (!task.getUser().getId().equals(currentUser.getId())) {
                logger.warning("User " + currentUser.getId() + " attempted to annotate task " + taskId + " belonging to user " + task.getUser().getId());
                redirectAttributes.addFlashAttribute("errorMessage", "You don't have permission to access this task");
                return "redirect:/user/tasks";
            }
            
            // Find the text pair to annotate
            CoupleText textPair = null;
            for (CoupleText couple : task.getCouples()) {
                if (couple.getId().equals(coupleId)) {
                    textPair = couple;
                    break;
                }
            }
            
            if (textPair == null) {
                logger.warning("Text pair " + coupleId + " not found in task " + taskId);
                redirectAttributes.addFlashAttribute("errorMessage", "Text pair not found in this task");
                return "redirect:/user/tasks/" + taskId;
            }
            
            // Save the annotation
            textPair.setClassAnnotation(annotation);
            
            // Save the annotation to the database
            try {
                coupleTextRepository.save(textPair);
                logger.info("Successfully saved annotation for text pair " + coupleId);
                redirectAttributes.addFlashAttribute("success", "Annotation saved successfully");
            } catch (Exception e) {
                logger.severe("Error saving annotation: " + e.getMessage());
                e.printStackTrace();
                redirectAttributes.addFlashAttribute("errorMessage", "Error saving annotation: " + e.getMessage());
                return "redirect:/user/tasks/" + taskId + "?coupleId=" + coupleId;
            }
            
            // Find the next text pair in the sequence
            CoupleText nextPair = findNextTextPair(task, coupleId);
            
            if (nextPair != null) {
                logger.info("Moving to next text pair " + nextPair.getId());
                // Redirect to the next pair
                return "redirect:/user/tasks/" + taskId + "?coupleId=" + nextPair.getId();
            } else {
                logger.info("All text pairs in task " + taskId + " have been annotated");
                // All pairs are annotated
                redirectAttributes.addFlashAttribute("success", "Task completed! All text pairs have been annotated.");
                return "redirect:/user/tasks";
            }
        } catch (Exception e) {
            logger.severe("Error in annotation process: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", "An error occurred during annotation: " + e.getMessage());
            return "redirect:/user/tasks";
        }
    }
    
    /**
     * Helper method to find the next text pair in the sequence
     */
    private CoupleText findNextTextPair(Task task, Long currentCoupleId) {
        boolean foundCurrent = false;
        
        for (CoupleText couple : task.getCouples()) {
            // If we previously found the current pair, this is the next one
            if (foundCurrent && couple.getClassAnnotation() == null) {
                return couple;
            }
            
            // Mark when we find the current pair
            if (couple.getId().equals(currentCoupleId)) {
                foundCurrent = true;
            }
        }
        
        // If we didn't find a next unannotated pair after the current one,
        // look for any unannotated pair from the beginning
        for (CoupleText couple : task.getCouples()) {
            if (couple.getClassAnnotation() == null && !couple.getId().equals(currentCoupleId)) {
                return couple;
            }
        }
        
        // All pairs are annotated
        return null;
    }
    
    /**
     * Diagnostic endpoint to help troubleshoot task issues
     */
    @GetMapping("/tasks/{taskId}/diagnostic")
    public String showTaskDiagnostic(@PathVariable Long taskId, Model model) {
        try {
            logger.info("Running diagnostic on task with ID: " + taskId);
            
            // Get current user
            String username = userService.getCurrentUserName();
            User currentUser = userService.findUserByUsername(username);
            
            if (currentUser == null) {
                logger.warning("Current user is null during diagnostic");
                model.addAttribute("errorMessage", "User not found. Please log in again.");
                return "redirect:/login";
            }
            
            // Get the task directly from the database using the basic method first
            Task basicTask = taskRepository.findById(taskId).orElse(null);
            
            if (basicTask == null) {
                logger.warning("Task " + taskId + " not found using basic findById");
                model.addAttribute("diagnosticResult", "Task not found using basic findById method");
                return "user/task_diagnostic";
            }
            
            StringBuilder diagnosticInfo = new StringBuilder();
            diagnosticInfo.append("Task ID: ").append(taskId).append("\n");
            diagnosticInfo.append("User ID: ").append(basicTask.getUser() != null ? basicTask.getUser().getId() : "null").append("\n");
            diagnosticInfo.append("Dataset ID: ").append(basicTask.getDataset() != null ? basicTask.getDataset().getId() : "null").append("\n");
            
            // Now try with our custom query
            Task fullTask = null;
            try {
                fullTask = taskRepository.findByIdWithCouples(taskId);
                diagnosticInfo.append("Query findByIdWithCouples succeeded\n");
            } catch (Exception e) {
                diagnosticInfo.append("Error in findByIdWithCouples: ").append(e.getMessage()).append("\n");
                logger.severe("Error in findByIdWithCouples for task " + taskId + ": " + e.getMessage());
                e.printStackTrace();
            }
            
            if (fullTask != null) {
                diagnosticInfo.append("Full Task ID: ").append(fullTask.getId()).append("\n");
                diagnosticInfo.append("Full Task User: ").append(fullTask.getUser() != null ? 
                    fullTask.getUser().getUsername() + " (ID: " + fullTask.getUser().getId() + ")" : "null").append("\n");
                    
                diagnosticInfo.append("Full Task Dataset: ").append(fullTask.getDataset() != null ? 
                    fullTask.getDataset().getName() + " (ID: " + fullTask.getDataset().getId() + ")" : "null").append("\n");
                    
                if (fullTask.getCouples() != null) {
                    int coupleCount = fullTask.getCouples().size();
                    diagnosticInfo.append("Text Pair Count: ").append(coupleCount).append("\n");
                    
                    // Check first 5 couples
                    int checkCount = Math.min(5, coupleCount);
                    diagnosticInfo.append("\nFirst ").append(checkCount).append(" Text Pairs:\n");
                    
                    int i = 0;
                    for (CoupleText couple : fullTask.getCouples()) {
                        if (i >= checkCount) break;
                        
                        diagnosticInfo.append("Pair ").append(i + 1).append(" - ID: ").append(couple.getId()).append("\n");
                        
                        // Check text1
                        if (couple.getText1() == null) {
                            diagnosticInfo.append("  Text1: null\n");
                        } else {
                            String text1Preview = couple.getText1().length() > 50 ? 
                                couple.getText1().substring(0, 50) + "..." : couple.getText1();
                            diagnosticInfo.append("  Text1: ").append(text1Preview).append("\n");
                            diagnosticInfo.append("  Text1 Length: ").append(couple.getText1().length()).append("\n");
                        }
                        
                        // Check text2
                        if (couple.getText2() == null) {
                            diagnosticInfo.append("  Text2: null\n");
                        } else {
                            String text2Preview = couple.getText2().length() > 50 ? 
                                couple.getText2().substring(0, 50) + "..." : couple.getText2();
                            diagnosticInfo.append("  Text2: ").append(text2Preview).append("\n");
                            diagnosticInfo.append("  Text2 Length: ").append(couple.getText2().length()).append("\n");
                        }
                        
                        i++;
                    }
                } else {
                    diagnosticInfo.append("Text Pairs: null\n");
                }
            }
            
            // Add diagnostic information to the model
            model.addAttribute("diagnosticResult", diagnosticInfo.toString());
            model.addAttribute("currentUser", currentUser);
            
            return "user/task_diagnostic";
        } catch (Exception e) {
            logger.severe("Error in diagnostic for task " + taskId + ": " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("diagnosticResult", "Error in diagnostic: " + e.getMessage());
            return "user/task_diagnostic";
        }
    }
    
    /**
     * Direct database access test endpoint
     */
    @GetMapping("/tasks/{taskId}/direct-test")
    public String testDirectDatabaseAccess(@PathVariable Long taskId, Model model) {
        try {
            logger.info("Testing direct database access for task ID: " + taskId);
            
            // Get current user for the template
            String username = userService.getCurrentUserName();
            User currentUser = userService.findUserByUsername(username);
            model.addAttribute("currentUser", currentUser);
            
            StringBuilder result = new StringBuilder();
            result.append("Direct Database Test for Task ID: ").append(taskId).append("\n\n");
            
            // Try to find the task using JPA repository
            boolean exists = taskRepository.existsById(taskId);
            result.append("Task exists in repository: ").append(exists).append("\n");
            
            // Try to get the task using the repository
            Task task = taskRepository.findById(taskId).orElse(null);
            if (task != null) {
                result.append("Task found via repository:\n");
                result.append("  ID: ").append(task.getId()).append("\n");
                result.append("  Deadline: ").append(task.getDateLimite()).append("\n");
                result.append("  User ID: ").append(task.getUser() != null ? task.getUser().getId() : "null").append("\n");
                result.append("  Dataset ID: ").append(task.getDataset() != null ? task.getDataset().getId() : "null").append("\n");
            } else {
                result.append("Task NOT found via repository\n");
            }
            
            // Try direct JDBC query
            result.append("\nDirect JDBC Query Test:\n");
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM tasks WHERE id = ?")) {
                stmt.setLong(1, taskId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        result.append("Task found via JDBC:\n");
                        result.append("  ID: ").append(rs.getLong("id")).append("\n");
                        result.append("  Deadline: ").append(rs.getTimestamp("date_limite")).append("\n");
                        result.append("  User ID: ").append(rs.getLong("user_id")).append("\n");
                        result.append("  Dataset ID: ").append(rs.getLong("dataset_id")).append("\n");
                    } else {
                        result.append("Task NOT found via JDBC\n");
                    }
                }
                
                // Check task_couple relationships
                result.append("\nText Pair Relationships:\n");
                try (PreparedStatement coupleStmt = conn.prepareStatement(
                        "SELECT COUNT(*) as count FROM task_couple WHERE task_id = ?")) {
                    coupleStmt.setLong(1, taskId);
                    try (ResultSet rs = coupleStmt.executeQuery()) {
                        if (rs.next()) {
                            int count = rs.getInt("count");
                            result.append("  Text Pairs Count: ").append(count).append("\n");
                        }
                    }
                }
            }
            
            // Add the result to the model
            model.addAttribute("diagnosticResult", result.toString());
            
            return "user/task_diagnostic";
        } catch (Exception e) {
            logger.severe("Error in direct database test: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("diagnosticResult", "Error in direct database test: " + e.getMessage() + "\n\n" + e.toString());
            return "user/task_diagnostic";
        }
    }
    
    /**
     * Emergency fix endpoint - creates a direct access to task pairs
     */
    @GetMapping("/tasks/{taskId}/emergency-fix")
    public String emergencyFixTask(@PathVariable Long taskId, Model model) {
        try {
            logger.info("Emergency fix for task ID: " + taskId);
            
            // Get current user for the template
            String username = userService.getCurrentUserName();
            User currentUser = userService.findUserByUsername(username);
            model.addAttribute("currentUser", currentUser);
            
            StringBuilder result = new StringBuilder();
            result.append("Emergency Fix for Task ID: ").append(taskId).append("\n\n");
            
            // Try direct JDBC query to get task data
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM tasks WHERE id = ?")) {
                stmt.setLong(1, taskId);
                Long userId = null;
                Long datasetId = null;
                java.sql.Timestamp deadline = null;
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        userId = rs.getLong("user_id");
                        datasetId = rs.getLong("dataset_id");
                        deadline = rs.getTimestamp("date_limite");
                        result.append("Task data retrieved from database:\n");
                        result.append("  User ID: ").append(userId).append("\n");
                        result.append("  Dataset ID: ").append(datasetId).append("\n");
                        result.append("  Deadline: ").append(deadline).append("\n");
                    } else {
                        result.append("ERROR: Task not found in database\n");
                        model.addAttribute("diagnosticResult", result.toString());
                        return "user/task_diagnostic";
                    }
                }
                
                // Get text pairs
                result.append("\nRetrieving text pairs...\n");
                try (PreparedStatement coupleStmt = conn.prepareStatement(
                        "SELECT c.* FROM couple_text c " +
                        "JOIN task_couple tc ON c.id = tc.couple_id " +
                        "WHERE tc.task_id = ? LIMIT 10")) {
                    coupleStmt.setLong(1, taskId);
                    try (ResultSet rs = coupleStmt.executeQuery()) {
                        int count = 0;
                        while (rs.next() && count < 10) {
                            count++;
                            Long coupleId = rs.getLong("id");
                            String text1Preview = rs.getString("text_1");
                            if (text1Preview != null && text1Preview.length() > 50) {
                                text1Preview = text1Preview.substring(0, 50) + "...";
                            }
                            
                            result.append("  Pair ").append(count).append(" (ID: ").append(coupleId)
                                  .append("): ").append(text1Preview).append("\n");
                        }
                        result.append("  Total pairs shown: ").append(count).append("\n");
                    }
                }
            }
            
            // Add the result to the model
            model.addAttribute("diagnosticResult", result.toString());
            
            return "user/task_diagnostic";
        } catch (Exception e) {
            logger.severe("Error in emergency fix: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("diagnosticResult", "Error in emergency fix: " + e.getMessage() + "\n\n" + e.toString());
            return "user/task_diagnostic";
        }
    }
    
    /**
     * Direct access to text pairs for a task
     */
    @GetMapping("/tasks/{taskId}/direct-access")
    public String directAccessTaskPairs(@PathVariable Long taskId, 
                                      @RequestParam(value = "coupleId", required = false) Long requestedCoupleId,
                                      Model model) {
        try {
            logger.info("Direct access to text pairs for task ID: " + taskId + (requestedCoupleId != null ? ", requested couple ID: " + requestedCoupleId : ""));
            
            // Get current user
            String username = userService.getCurrentUserName();
            User currentUser = userService.findUserByUsername(username);
            
            if (currentUser == null) {
                logger.severe("Current user is null in direct-access");
                model.addAttribute("errorMessage", "User not found. Please log in again.");
                return "redirect:/login";
            }
            
            logger.info("Current user: " + currentUser.getUsername());
            
            // Get task data directly from JDBC
            Long userId = null;
            Long datasetId = null;
            java.util.Date deadline = null;
            String datasetName = "Unknown";
            
            try (Connection conn = dataSource.getConnection()) {
                logger.info("Got database connection");
                
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT t.*, d.name as dataset_name FROM tasks t LEFT JOIN dataset d ON t.dataset_id = d.id WHERE t.id = ?")) {
                    stmt.setLong(1, taskId);
                    logger.info("Executing query for task: " + taskId);
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            userId = rs.getLong("user_id");
                            datasetId = rs.getLong("dataset_id");
                            deadline = rs.getTimestamp("date_limite");
                            datasetName = rs.getString("dataset_name");
                            logger.info("Found task data: userId=" + userId + ", datasetId=" + datasetId);
                        } else {
                            logger.severe("Task not found in database: " + taskId);
                            model.addAttribute("errorMessage", "Task not found in database");
                            return "redirect:/user/tasks";
                        }
                    }
                } catch (Exception e) {
                    logger.severe("Error querying task: " + e.getMessage());
                    throw e;
                }
            } catch (Exception e) {
                logger.severe("Database connection error: " + e.getMessage());
                throw e;
            }
            
            // Security check
            if (!userId.equals(currentUser.getId())) {
                logger.severe("Unauthorized access: task belongs to user " + userId + " but current user is " + currentUser.getId());
                model.addAttribute("errorMessage", "You don't have permission to access this task");
                return "redirect:/user/tasks";
            }
            
            // Get text pairs
            List<CoupleText> textPairs = new ArrayList<>();
            
            try (Connection conn = dataSource.getConnection()) {
                logger.info("Getting text pairs for task " + taskId);
                
                try (PreparedStatement stmt = conn.prepareStatement(
                     "SELECT c.* FROM couple_text c " +
                     "JOIN task_couple tc ON c.id = tc.couple_id " +
                     "WHERE tc.task_id = ?")) {
                    stmt.setLong(1, taskId);
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        int count = 0;
                        while (rs.next()) {
                            count++;
                            CoupleText couple = new CoupleText();
                            couple.setId(rs.getLong("id"));
                            
                            // Get text with null check
                            String text1 = rs.getString("text_1");
                            String text2 = rs.getString("text_2");
                            
                            couple.setText1(text1);
                            couple.setText2(text2);
                            couple.setClassAnnotation(rs.getString("class_annotation"));
                            textPairs.add(couple);
                            
                            // Log first few pairs for debugging
                            if (count <= 3) {
                                logger.info("Pair " + count + " - ID: " + couple.getId() + 
                                          ", Text1: " + (text1 != null ? text1.substring(0, Math.min(20, text1.length())) + "..." : "null") +
                                          ", Text2: " + (text2 != null ? text2.substring(0, Math.min(20, text2.length())) + "..." : "null"));
                            }
                        }
                        logger.info("Retrieved " + count + " text pairs");
                    }
                } catch (Exception e) {
                    logger.severe("Error retrieving text pairs: " + e.getMessage());
                    throw e;
                }
            } catch (Exception e) {
                logger.severe("Database connection error for text pairs: " + e.getMessage());
                throw e;
            }
            
            // Create a minimal task object
            Task task = new Task();
            task.setId(taskId);
            task.setDateLimite(deadline);
            task.setUser(currentUser);
            
            // Create minimal dataset
            Dataset dataset = new Dataset();
            dataset.setId(datasetId);
            dataset.setName(datasetName);
            
            // Fetch the possible classes for this dataset
            Set<ClassPossible> classesPossibles = new HashSet<>();
            try (Connection conn = dataSource.getConnection()) {
                // First, let's check the table structure
                try {
                    ResultSet columns = conn.getMetaData().getColumns(null, null, "class_possible", null);
                    while (columns.next()) {
                        logger.info("Column in class_possible: " + columns.getString("COLUMN_NAME"));
                    }
                } catch (Exception e) {
                    logger.warning("Error getting column metadata: " + e.getMessage());
                }
                
                try (PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, text_class, dataset_id FROM class_possible WHERE dataset_id = ?")) {
                    stmt.setLong(1, datasetId);
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            ClassPossible classPossible = new ClassPossible();
                            classPossible.setId(rs.getLong("id"));
                            classPossible.setTextClass(rs.getString("text_class"));
                            classPossible.setDataset(dataset);
                            classesPossibles.add(classPossible);
                        }
                        logger.info("Retrieved " + classesPossibles.size() + " possible classes for dataset " + datasetId);
                    }
                }
            } catch (Exception e) {
                logger.warning("Error retrieving possible classes: " + e.getMessage());
                // Non-fatal error, continue without classes
            }
            
            dataset.setClassesPossibles(classesPossibles);
            task.setDataset(dataset);
            
            // Also set the couples field for better compatibility with the template
            task.setCouples(textPairs);
            
            // Calculate progress for the template to avoid complex expressions
            int totalPairs = textPairs.size();
            long annotatedPairs = 0;
            for (CoupleText pair : textPairs) {
                if (pair.getClassAnnotation() != null && !pair.getClassAnnotation().isEmpty()) {
                    annotatedPairs++;
                }
            }
            int progressPercentage = totalPairs > 0 ? (int)((annotatedPairs * 100) / totalPairs) : 0;
            
            // Set up the model
            model.addAttribute("task", task);
            model.addAttribute("textPairs", textPairs);
            model.addAttribute("currentUser", currentUser);
            model.addAttribute("totalPairs", totalPairs);
            model.addAttribute("annotatedPairs", annotatedPairs);
            model.addAttribute("progressPercentage", progressPercentage);
            
            // Handle the requested couple ID
            if (requestedCoupleId != null) {
                // Verify the requested couple ID belongs to this task
                boolean coupleFound = false;
                for (CoupleText pair : textPairs) {
                    if (pair.getId().equals(requestedCoupleId)) {
                        coupleFound = true;
                        break;
                    }
                }
                
                if (coupleFound) {
                    // Set the requested couple ID as the current one to display
                    model.addAttribute("highlightCoupleId", requestedCoupleId);
                    logger.info("Using requested couple ID: " + requestedCoupleId);
                } else {
                    logger.warning("Requested couple ID " + requestedCoupleId + " not found in task " + taskId);
                    // Fall back to the first unannotated pair
                }
            }
            
            // If no specific couple was requested or the requested one wasn't found,
            // try to find the first unannotated one
            if (!model.containsAttribute("highlightCoupleId") && !textPairs.isEmpty()) {
                // Find the first unannotated text pair to focus on initially
                CoupleText firstUnannotatedPair = null;
                for (CoupleText pair : textPairs) {
                    if (pair.getClassAnnotation() == null || pair.getClassAnnotation().isEmpty()) {
                        firstUnannotatedPair = pair;
                        break;
                    }
                }
                
                // If we found an unannotated pair, highlight it in the model
                if (firstUnannotatedPair != null) {
                    model.addAttribute("highlightCoupleId", firstUnannotatedPair.getId());
                    logger.info("Using first unannotated couple ID: " + firstUnannotatedPair.getId());
                } else {
                    // If all pairs are annotated, just use the first one
                    model.addAttribute("highlightCoupleId", textPairs.get(0).getId());
                    logger.info("All pairs annotated, using first pair ID: " + textPairs.get(0).getId());
                }
            }
            
            logger.info("Successfully prepared direct access view for task " + taskId);
            return "user/task_annotation";
            
        } catch (Exception e) {
            logger.severe("Error in direct access: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("diagnosticResult", "Error in direct access: " + e.getMessage() + "\n\n" + e.toString());
            return "user/task_diagnostic"; // Return diagnostic view instead of redirecting
        }
    }
    
    /**
     * Direct annotation saving endpoint
     */
    @PostMapping("/tasks/{taskId}/direct-annotate")
    public String directSaveAnnotation(
            @PathVariable Long taskId,
            @RequestParam("coupleId") Long coupleId,
            @RequestParam("annotation") String annotation,
            RedirectAttributes redirectAttributes) {
        
        try {
            logger.info("Direct annotation save for task " + taskId + ", couple " + coupleId);
            
            // Get current user
            String username = userService.getCurrentUserName();
            User currentUser = userService.findUserByUsername(username);
            
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "User not found. Please log in again.");
                return "redirect:/login";
            }
            
            // Verify task belongs to user
            boolean authorized = false;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT user_id FROM tasks WHERE id = ?")) {
                stmt.setLong(1, taskId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Long userId = rs.getLong("user_id");
                        authorized = userId.equals(currentUser.getId());
                    }
                }
            }
            
            if (!authorized) {
                redirectAttributes.addFlashAttribute("errorMessage", "You don't have permission to access this task");
                return "redirect:/user/tasks";
            }
            
            // Verify couple belongs to task
            boolean coupleInTask = false;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT 1 FROM task_couple WHERE task_id = ? AND couple_id = ?")) {
                stmt.setLong(1, taskId);
                stmt.setLong(2, coupleId);
                try (ResultSet rs = stmt.executeQuery()) {
                    coupleInTask = rs.next();
                }
            }
            
            if (!coupleInTask) {
                redirectAttributes.addFlashAttribute("errorMessage", "Text pair not found in this task");
                return "redirect:/user/tasks/" + taskId + "/direct-access";
            }
            
            // Save the annotation directly to the database
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE couple_text SET class_annotation = ? WHERE id = ?")) {
                stmt.setString(1, annotation);
                stmt.setLong(2, coupleId);
                int updated = stmt.executeUpdate();
                
                if (updated <= 0) {
                    logger.warning("Failed to update annotation for text pair " + coupleId);
                    redirectAttributes.addFlashAttribute("errorMessage", "Failed to save annotation");
                    return "redirect:/user/tasks/" + taskId + "/direct-access?coupleId=" + coupleId;
                }
            }
            
            // Find the next unannotated text pair
            Long nextCoupleId = null;
            boolean foundCurrent = false;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT c.id, c.class_annotation FROM couple_text c " +
                     "JOIN task_couple tc ON c.id = tc.couple_id " +
                     "WHERE tc.task_id = ? " +
                     "ORDER BY c.id")) {
                stmt.setLong(1, taskId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    // First pass: look for the next unannotated pair after the current one
                    while (rs.next()) {
                        Long id = rs.getLong("id");
                        String classAnnotation = rs.getString("class_annotation");
                        
                        if (foundCurrent && (classAnnotation == null || classAnnotation.isEmpty())) {
                            nextCoupleId = id;
                            break;
                        }
                        
                        if (id.equals(coupleId)) {
                            foundCurrent = true;
                        }
                    }
                }
                
                // If we didn't find a next unannotated pair, look for any unannotated pair
                if (nextCoupleId == null) {
                    try (PreparedStatement stmt2 = conn.prepareStatement(
                         "SELECT c.id FROM couple_text c " +
                         "JOIN task_couple tc ON c.id = tc.couple_id " +
                         "WHERE tc.task_id = ? AND (c.class_annotation IS NULL OR c.class_annotation = '') " +
                         "AND c.id != ? " +
                         "ORDER BY c.id LIMIT 1")) {
                        stmt2.setLong(1, taskId);
                        stmt2.setLong(2, coupleId);
                        
                        try (ResultSet rs = stmt2.executeQuery()) {
                            if (rs.next()) {
                                nextCoupleId = rs.getLong("id");
                            }
                        }
                    }
                }
            }
            
            if (nextCoupleId != null) {
                logger.info("Moving to next text pair " + nextCoupleId);
                // Redirect to the direct-access endpoint with the next couple ID
                // But don't include any success message
                return "redirect:/user/tasks/" + taskId + "/direct-access?coupleId=" + nextCoupleId;
            } else {
                logger.info("All text pairs in task " + taskId + " have been annotated");
                // All pairs are annotated
                redirectAttributes.addFlashAttribute("success", "Task completed! All text pairs have been annotated.");
                return "redirect:/user/tasks";
            }
            
        } catch (Exception e) {
            logger.severe("Error in direct annotation: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", "An error occurred: " + e.getMessage());
            return "redirect:/user/tasks/" + taskId + "/direct-access";
        }
    }
    
    /**
     * Mark a task as complete and return to tasks list
     */
    @GetMapping("/tasks/{taskId}/mark-complete")
    public String markTaskComplete(@PathVariable Long taskId, RedirectAttributes redirectAttributes) {
        try {
            logger.info("Marking task " + taskId + " as complete");
            
            // Get current user
            String username = userService.getCurrentUserName();
            User currentUser = userService.findUserByUsername(username);
            
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "User not found. Please log in again.");
                return "redirect:/login";
            }
            
            // Verify task belongs to user
            boolean authorized = false;
            Long userId = null;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT user_id FROM tasks WHERE id = ?")) {
                stmt.setLong(1, taskId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        userId = rs.getLong("user_id");
                        authorized = userId.equals(currentUser.getId());
                    }
                }
            }
            
            if (!authorized) {
                redirectAttributes.addFlashAttribute("errorMessage", "You don't have permission to access this task");
                return "redirect:/user/tasks";
            }
            
            // Calculate the progress for this task
            int totalPairs = 0;
            int annotatedPairs = 0;
            
            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement countStmt = conn.prepareStatement(
                        "SELECT COUNT(*) AS total FROM task_couple WHERE task_id = ?")) {
                    countStmt.setLong(1, taskId);
                    try (ResultSet rs = countStmt.executeQuery()) {
                        if (rs.next()) {
                            totalPairs = rs.getInt("total");
                        }
                    }
                }
                
                try (PreparedStatement annotatedStmt = conn.prepareStatement(
                        "SELECT COUNT(*) AS annotated FROM couple_text c " +
                        "JOIN task_couple tc ON c.id = tc.couple_id " +
                        "WHERE tc.task_id = ? AND c.class_annotation IS NOT NULL AND c.class_annotation != ''")) {
                    annotatedStmt.setLong(1, taskId);
                    try (ResultSet rs = annotatedStmt.executeQuery()) {
                        if (rs.next()) {
                            annotatedPairs = rs.getInt("annotated");
                        }
                    }
                }
            }
            
            int progressPercentage = totalPairs > 0 ? (annotatedPairs * 100) / totalPairs : 0;
            
            // Add success message with progress information
            redirectAttributes.addFlashAttribute("success", 
                "Task #" + taskId + " marked as complete. " + 
                annotatedPairs + " of " + totalPairs + " text pairs annotated (" + progressPercentage + "%)");
                
            return "redirect:/user/tasks";
            
        } catch (Exception e) {
            logger.severe("Error marking task as complete: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", "An error occurred: " + e.getMessage());
            return "redirect:/user/tasks";
        }
    }
} 