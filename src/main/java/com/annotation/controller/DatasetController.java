package com.annotation.controller;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.annotation.service.DefaultUserServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

// Add missing imports for service implementations
import com.annotation.service.DatasetServiceImpl;
import com.annotation.service.CoupleTextServiceImpl;
import com.annotation.service.AsyncDatasetParserService;
import com.annotation.model.Dataset;
import com.annotation.model.CoupleText;
import com.annotation.model.User;
import com.annotation.model.Task;
import com.annotation.model.Role;
import com.annotation.repository.UserRepository;
import com.annotation.repository.RoleRepository;
import com.annotation.repository.TaskRepository;
import com.annotation.service.TaskAssignmentService;
import com.annotation.repository.DatasetRepository;

@Controller
@RequestMapping("/admin")
public class DatasetController {

    private final DatasetServiceImpl datasetService;
    private final CoupleTextServiceImpl coupleTextService;
    private final AsyncDatasetParserService asyncDatasetParserService;
    private final DefaultUserServiceImpl userService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TaskRepository taskRepository;
    private final TaskAssignmentService taskAssignmentService;
    private final DatasetRepository datasetRepository;
    @PersistenceContext
    private EntityManager entityManager;

    // Constructor-based injection for both services
    @Autowired
    public DatasetController(
        DatasetServiceImpl datasetService, 
        CoupleTextServiceImpl coupleTextService, 
        AsyncDatasetParserService asyncDatasetParserService,
        DefaultUserServiceImpl userService,
        UserRepository userRepository,
        RoleRepository roleRepository,
        TaskRepository taskRepository,
        TaskAssignmentService taskAssignmentService,
        DatasetRepository datasetRepository) {
        this.datasetService = datasetService;
        this.coupleTextService = coupleTextService;
        this.asyncDatasetParserService = asyncDatasetParserService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.taskRepository = taskRepository;
        this.taskAssignmentService = taskAssignmentService;
        this.datasetRepository = datasetRepository;
    }

    @GetMapping("/datasets")
    public String showDatasetHome(Model model) {
        String currentUserName = StringUtils.capitalize(userService.getCurrentUserName());
        model.addAttribute("currentUserName", currentUserName);
        
        List<Dataset> datasets = datasetService.findAllDatasets();
        System.out.println("Found " + datasets.size() + " datasets in the database");
        
        // Debug dataset info if any exist
        if (!datasets.isEmpty()) {
            for (Dataset dataset : datasets) {
                System.out.println("Dataset ID: " + dataset.getId() + ", Name: " + dataset.getName());
            }
        } else {
            System.out.println("No datasets found in database");
        }
        
        model.addAttribute("datasets", datasets);
        return "admin/datasets_management/datasets";
    }

    @GetMapping("/datasets/details/{id}")
    public String DatasetDetails(@PathVariable Long id, Model model,
                                 @RequestParam(name = "page", defaultValue = "0") int page,
                                 @RequestParam(name = "size", defaultValue = "25") int size) {

        String currentUserName = StringUtils.capitalize(userService.getCurrentUserName());
        model.addAttribute("currentUserName", currentUserName);
        
        Dataset dataset = datasetService.findDatasetById(id);
        if (dataset == null) {
            model.addAttribute("errorMessage", "Dataset not found");
            return "redirect:/admin/datasets";
        }
        
        Page<CoupleText> coupleTextsPage = coupleTextService.getCoupleTextsByDatasetId(id, page, size);

        int totalPages = coupleTextsPage.getTotalPages();
        int currentPage = page;

        // Pagination window control (show up to 5 pages max, centered around current)
        int startPage = Math.max(0, currentPage - 2);
        int endPage = Math.min(totalPages - 1, currentPage + 2);

        // Get assigned annotators (users with tasks for this dataset)
        // Use enhanced query method to fetch all relations in one go
        List<Task> tasks = null;
        try {
            tasks = taskRepository.findByDatasetIdWithAllRelations(id);
            System.out.println("Found " + tasks.size() + " tasks for dataset ID: " + id);
            
            // Add debug information for each task
            for (Task task : tasks) {
                User user = task.getUser();
                if (user != null) {
                    System.out.println("Task ID: " + task.getId() + 
                        ", User: " + user.getFirstname() + " " + user.getLastname() + 
                        " (ID: " + user.getId() + ")" +
                        ", Couples: " + (task.getCouples() != null ? task.getCouples().size() : 0));
                } else {
                    System.out.println("Task ID: " + task.getId() + " has no user assigned!");
                }
            }
        } catch (Exception e) {
            System.out.println("Error fetching tasks: " + e.getMessage());
            e.printStackTrace();
            tasks = new ArrayList<>();
        }

        model.addAttribute("tasks", tasks != null ? tasks : new ArrayList<>());
        model.addAttribute("coupleTextsPage", coupleTextsPage);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        model.addAttribute("dataset", dataset);

        // Check processing status
        AsyncDatasetParserService.ProcessingStatus status = asyncDatasetParserService.getProcessingStatus(id);
        boolean hasTextPairs = coupleTextsPage.getTotalElements() > 0;
        
        // Add processing message based on status
        if (!hasTextPairs || status == AsyncDatasetParserService.ProcessingStatus.PROCESSING) {
            model.addAttribute("processingMessage", "Your dataset is currently being processed. Please wait a moment to see the text pairs.");
            model.addAttribute("processingStatus", status.toString());
        } else if (status == AsyncDatasetParserService.ProcessingStatus.FAILED) {
            model.addAttribute("errorMessage", "Dataset processing failed. Please try reprocessing the dataset.");
        }

        return "admin/datasets_management/dataset_view";
    }

    // New endpoint to check processing status
    @GetMapping("/datasets/status/{id}")
    @ResponseBody
    public Map<String, Object> checkProcessingStatus(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        
        Dataset dataset = datasetService.findDatasetById(id);
        if (dataset == null) {
            response.put("success", false);
            response.put("message", "Dataset not found");
            return response;
        }
        
        long textPairsCount = coupleTextService.countCoupleTextsByDatasetId(id);
        AsyncDatasetParserService.ProcessingStatus status = asyncDatasetParserService.getProcessingStatus(id);
        
        response.put("success", true);
        response.put("textPairsCount", textPairsCount);
        response.put("isProcessed", textPairsCount > 0);
        response.put("status", status.toString());
        response.put("isProcessing", status == AsyncDatasetParserService.ProcessingStatus.PROCESSING);
        response.put("isFailed", status == AsyncDatasetParserService.ProcessingStatus.FAILED);
        response.put("isCompleted", status == AsyncDatasetParserService.ProcessingStatus.COMPLETED || textPairsCount > 0);
        
        return response;
    }

    @GetMapping("/datasets/add")
    public String addDataset(Model model) {
        String currentUserName = StringUtils.capitalize(userService.getCurrentUserName());
        model.addAttribute("currentUserName", currentUserName);
        model.addAttribute("dataset", new Dataset());
        return "admin/datasets_management/addDataset";
    }

    @PostMapping("/datasets/save")
    public String saveDataset(@ModelAttribute Dataset dataset,
                              @RequestParam("classes") String classesRaw,
                              @RequestParam("file") MultipartFile file,
                              RedirectAttributes redirectAttributes) throws IOException {

        try {
            System.out.println("Creating new dataset: " + dataset.getName());
            Dataset savedDataset = datasetService.createDataset(dataset.getName(), dataset.getDescription(), file, classesRaw);
            
            if (savedDataset == null || savedDataset.getId() == null) {
                throw new RuntimeException("Failed to create dataset, ID is null");
            }
            
            System.out.println("Dataset created with ID: " + savedDataset.getId());
            
            // Explicitly save the dataset to ensure it's persisted
            datasetService.SaveDataset(savedDataset);
            
            // Start asynchronous processing
            System.out.println("Starting asynchronous processing for dataset ID: " + savedDataset.getId());
            asyncDatasetParserService.parseDatasetAsync(savedDataset);
            
            // Add success message with processing information
            redirectAttributes.addFlashAttribute("success", 
                "Dataset added successfully. The file is being processed in the background. " +
                "Please wait a moment before viewing the dataset details.");
            
            // Return to datasets list
            return "redirect:/admin/datasets";
        } catch (Exception e) {
            System.out.println("Error creating dataset: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to upload dataset: " + e.getMessage());
            return "redirect:/admin/datasets";
        }
    }

    // Add a manual processing endpoint
    @GetMapping("/datasets/process/{id}")
    public String processDataset(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetService.findDatasetById(id);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Dataset not found");
            return "redirect:/admin/datasets";
        }
        
        try {
            // Start asynchronous processing
            asyncDatasetParserService.parseDatasetAsync(dataset);
            redirectAttributes.addFlashAttribute("success", "Dataset processing started");
        } catch (Exception e) {
            System.out.println("Error processing dataset: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error processing dataset: " + e.getMessage());
        }
        
        return "redirect:/admin/datasets/details/" + id;
    }
    
    // Dataset deletion endpoint
    @PostMapping("/datasets/delete/{id}")
    public String deleteDataset(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Dataset dataset = datasetService.findDatasetById(id);
            if (dataset == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Dataset not found");
                return "redirect:/admin/datasets";
            }
            
            // Delete the dataset
            datasetService.deleteDataset(id);
            redirectAttributes.addFlashAttribute("success", "Dataset deleted successfully");
        } catch (Exception e) {
            System.out.println("Error deleting dataset: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting dataset: " + e.getMessage());
        }
        
        return "redirect:/admin/datasets";
    }

    // Add diagnostic endpoint
    @GetMapping("/datasets/debug")
    @ResponseBody
    public Map<String, Object> debugDatasets() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Count datasets
            long datasetCount = datasetService.countDatasets();
            response.put("datasetCount", datasetCount);
            
            // Get all datasets
            List<Dataset> datasets = datasetService.findAllDatasets();
            List<Map<String, Object>> datasetInfo = new ArrayList<>();
            
            for (Dataset dataset : datasets) {
                Map<String, Object> info = new HashMap<>();
                info.put("id", dataset.getId());
                info.put("name", dataset.getName());
                info.put("filePath", dataset.getFilePath());
                info.put("fileExists", dataset.getFilePath() != null && new File(dataset.getFilePath()).exists());
                info.put("textPairCount", coupleTextService.countCoupleTextsByDatasetId(dataset.getId()));
                datasetInfo.add(info);
            }
            
            response.put("datasets", datasetInfo);
            response.put("success", true);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return response;
    }
    
    // Improved endpoint to show annotator assignment modal
    @GetMapping("/datasets/assign-annotators/{id}")
    public String showAssignAnnotatorsModal(@PathVariable Long id, Model model) {
        String currentUserName = StringUtils.capitalize(userService.getCurrentUserName());
        model.addAttribute("currentUserName", currentUserName);
        
        Dataset dataset = datasetService.findDatasetById(id);
        if (dataset == null) {
            model.addAttribute("errorMessage", "Dataset not found");
            return "redirect:/admin/datasets";
        }
        
        // Ensure USER role exists
        ensureUserRoleExists();
        
        // Get all users with USER role (available annotators)
        Optional<Role> userRoleOpt = roleRepository.findByRole("USER");
        List<User> annotators = new ArrayList<>();
        if (userRoleOpt.isPresent()) {
            Role userRole = userRoleOpt.get();
            annotators = userRepository.findAllByRolesContaining(userRole);
            System.out.println("Found " + annotators.size() + " annotators with USER role");
        } else {
            System.out.println("USER role not found in the database");
        }
        
        // Get already assigned annotator IDs
        List<Long> assignedAnnotatorIds = new ArrayList<>();
        List<Task> existingTasks = taskRepository.findByDatasetId(id);
        
        if (existingTasks != null && !existingTasks.isEmpty()) {
            // Extract unique annotator IDs from tasks
            assignedAnnotatorIds = existingTasks.stream()
                .filter(task -> task.getUser() != null)
                .map(task -> task.getUser().getId())
                .distinct()
                .collect(Collectors.toList());
                
            System.out.println("Found " + assignedAnnotatorIds.size() + " already assigned annotators");
        }
        
        // Get current deadline (if tasks exist)
        Date deadlineDate = null;
        if (!existingTasks.isEmpty()) {
            deadlineDate = existingTasks.get(0).getDateLimite();
            System.out.println("Current deadline: " + deadlineDate);
        }
        
        model.addAttribute("dataset", dataset);
        model.addAttribute("annotators", annotators);
        model.addAttribute("assignedAnnotatorIds", assignedAnnotatorIds);
        model.addAttribute("deadlineDate", deadlineDate);
        
        return "admin/datasets_management/assign_annotators";
    }
    
    // Helper method to ensure USER role exists
    private void ensureUserRoleExists() {
        // Check if USER role exists
        Optional<Role> userRoleOpt = roleRepository.findByRole("USER");
        Role userRole;
        
        if (!userRoleOpt.isPresent()) {
            // Create USER role if it doesn't exist
            System.out.println("Creating USER role");
            userRole = new Role();
            userRole.setRole("USER");
            userRole = roleRepository.save(userRole);
        } else {
            userRole = userRoleOpt.get();
        }
        
        // Check if we have at least 3 users with USER role
        List<User> users = userRepository.findAllByRolesContaining(userRole);
        if (users.size() < 3) {
            // Create test users if needed
            for (int i = users.size() + 1; i <= 3; i++) {
                User user = new User();
                user.setFirstname("Test");
                user.setLastname("User " + i);
                user.setUsername("testuser" + i);
                user.setPassword("password"); // In real app, would be encoded
                user.getRoles().add(userRole);
                userRepository.save(user);
                System.out.println("Created test user: " + user.getFirstname() + " " + user.getLastname());
            }
        }
    }
    
    // Enhanced endpoint to process annotator assignment
    @PostMapping("/datasets/assign-annotators/{id}")
    public String assignAnnotatorsToDataset(
            @PathVariable Long id,
            @RequestParam("annotatorIds") List<Long> annotatorIds,
            @RequestParam("deadline") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date deadline,
            RedirectAttributes redirectAttributes) {
        
        System.out.println("=== ASSIGNING ANNOTATORS TO DATASET " + id + " ===");
        System.out.println("Selected annotators: " + annotatorIds);
        System.out.println("Deadline: " + deadline);
        
        // First verify the dataset exists
        if (!datasetRepository.existsById(id)) {
            System.out.println("ERROR: Dataset with ID " + id + " does not exist in database");
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Dataset with ID " + id + " does not exist in database. Please verify dataset ID.");
            return "redirect:/admin/datasets";
        }
        
        if (annotatorIds.isEmpty()) {
            System.out.println("ERROR: No annotators selected");
            redirectAttributes.addFlashAttribute("errorMessage", "Please select at least one annotator");
            return "redirect:/admin/datasets/assign-annotators/" + id;
        }
        
        Dataset dataset = datasetService.findDatasetById(id);
        if (dataset == null) {
            System.out.println("ERROR: Dataset not found with ID " + id);
            redirectAttributes.addFlashAttribute("errorMessage", "Dataset not found");
            return "redirect:/admin/datasets";
        }
        
        System.out.println("Dataset found: " + dataset.getName() + " (ID: " + dataset.getId() + ")");
        
        try {
            // Get all text couples for this dataset
            List<CoupleText> allCouples = coupleTextService.findAllCoupleTextsByDatasetId(id);
            System.out.println("Found " + allCouples.size() + " text pairs in dataset " + id);
            
            if (allCouples.isEmpty()) {
                System.out.println("ERROR: No text pairs found in dataset " + id);
                redirectAttributes.addFlashAttribute("errorMessage", "No text pairs found in this dataset");
                return "redirect:/admin/datasets/details/" + id;
            }
            
            // Get selected annotators
            List<User> selectedAnnotators = userRepository.findAllById(annotatorIds);
            System.out.println("Found " + selectedAnnotators.size() + " annotators out of " + annotatorIds.size() + " requested");
            
            if (selectedAnnotators.isEmpty()) {
                System.out.println("ERROR: No valid annotators found");
                redirectAttributes.addFlashAttribute("errorMessage", "No valid annotators selected");
                return "redirect:/admin/datasets/assign-annotators/" + id;
            }
            
            // Debug log annotators
            for (User annotator : selectedAnnotators) {
                System.out.println("  Annotator: " + annotator.getUsername() + " (ID: " + annotator.getId() + ")");
            }
            
            // Get the list of current annotators to check for changes
            List<Task> existingTasks = taskRepository.findByDatasetId(id);
            List<Long> currentAnnotatorIds = existingTasks.stream()
                .filter(task -> task.getUser() != null)
                .map(task -> task.getUser().getId())
                .distinct()
                .collect(Collectors.toList());
            
            // Compare current and selected annotators to see if there are any changes
            boolean annotatorListChanged = !currentAnnotatorIds.containsAll(annotatorIds) 
                                        || !annotatorIds.containsAll(currentAnnotatorIds);
            
            // Check if deadline changed
            boolean deadlineChanged = false;
            if (!existingTasks.isEmpty() && existingTasks.get(0).getDateLimite() != null) {
                Date currentDeadline = existingTasks.get(0).getDateLimite();
                deadlineChanged = !currentDeadline.equals(deadline);
            }
            
            if (annotatorListChanged || deadlineChanged || existingTasks.isEmpty()) {
                // Use the dedicated service to assign tasks to annotators
                System.out.println("Changes detected - reassigning tasks to annotators");
                taskAssignmentService.assignTasksToAnnotators(selectedAnnotators, dataset, deadline);
                
                // Debug: Check if tasks were actually created
                List<Task> createdTasks = taskRepository.findByDatasetIdWithAllRelations(id);
                System.out.println("After assignment, found " + createdTasks.size() + " tasks for dataset " + id);
                
                for (Task task : createdTasks) {
                    System.out.println("  Task ID: " + task.getId() + 
                                    ", User: " + (task.getUser() != null ? task.getUser().getUsername() : "null") + 
                                    ", Couples: " + (task.getCouples() != null ? task.getCouples().size() : 0));
                }
                
                redirectAttributes.addFlashAttribute("success", 
                    "Successfully assigned " + allCouples.size() + " text pairs to " + selectedAnnotators.size() + " annotators");
            } else {
                System.out.println("No changes detected in annotator assignments or deadline");
                redirectAttributes.addFlashAttribute("success", "No changes needed to current assignments");
            }
            
        } catch (Exception e) {
            System.out.println("ERROR assigning annotators: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", "Error assigning annotators: " + e.getMessage());
        }
        
        System.out.println("=== COMPLETED ANNOTATOR ASSIGNMENT PROCESS ===");
        return "redirect:/admin/datasets/details/" + id;
    }

    // Add an endpoint to remove an annotator from a dataset
    @PostMapping("/datasets/{datasetId}/remove-annotator/{userId}")
    public String removeAnnotatorFromDataset(
            @PathVariable Long datasetId,
            @PathVariable Long userId,
            RedirectAttributes redirectAttributes) {
        
        Dataset dataset = datasetService.findDatasetById(datasetId);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Dataset not found");
            return "redirect:/admin/datasets";
        }
        
        try {
            // Use the service to remove the annotator from the dataset
            taskAssignmentService.removeAnnotatorFromDataset(datasetId, userId);
            
            redirectAttributes.addFlashAttribute("success", "Annotator removed from dataset successfully");
        } catch (Exception e) {
            System.out.println("Error removing annotator: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", "Error removing annotator: " + e.getMessage());
        }
        
        return "redirect:/admin/datasets/details/" + datasetId;
    }

    @GetMapping("/debug/database")
    @ResponseBody
    public Map<String, Object> debugDatabase() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Database tables info
            List<Map<String, Object>> tables = new ArrayList<>();
            
            // Check tasks table
            Query tasksQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM tasks");
            long taskCount = ((Number) tasksQuery.getSingleResult()).longValue();
            
            Map<String, Object> tasksInfo = new HashMap<>();
            tasksInfo.put("table", "tasks");
            tasksInfo.put("count", taskCount);
            
            if (taskCount > 0) {
                Query taskDetailsQuery = entityManager.createNativeQuery(
                    "SELECT t.id, t.date_limite, t.dataset_id, t.user_id FROM tasks t");
                List<Object[]> taskRows = taskDetailsQuery.getResultList();
                
                List<Map<String, Object>> tasksList = new ArrayList<>();
                for (Object[] row : taskRows) {
                    Map<String, Object> task = new HashMap<>();
                    task.put("id", row[0]);
                    task.put("date_limite", row[1]);
                    task.put("dataset_id", row[2]);
                    task.put("user_id", row[3]);
                    tasksList.add(task);
                }
                tasksInfo.put("rows", tasksList);
            }
            tables.add(tasksInfo);
            
            // Check task_couple junction table
            Query taskCoupleQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM task_couple");
            long taskCoupleCount = 0;
            try {
                taskCoupleCount = ((Number) taskCoupleQuery.getSingleResult()).longValue();
            } catch (Exception e) {
                System.out.println("Error querying task_couple: " + e.getMessage());
            }
            
            Map<String, Object> taskCoupleInfo = new HashMap<>();
            taskCoupleInfo.put("table", "task_couple");
            taskCoupleInfo.put("count", taskCoupleCount);
            
            if (taskCoupleCount > 0) {
                Query taskCoupleDetailsQuery = entityManager.createNativeQuery(
                    "SELECT tc.task_id, tc.couple_id FROM task_couple tc");
                List<Object[]> taskCoupleRows = taskCoupleDetailsQuery.getResultList();
                
                List<Map<String, Object>> taskCouplesList = new ArrayList<>();
                for (Object[] row : taskCoupleRows) {
                    Map<String, Object> taskCouple = new HashMap<>();
                    taskCouple.put("task_id", row[0]);
                    taskCouple.put("couple_id", row[1]);
                    taskCouplesList.add(taskCouple);
                }
                taskCoupleInfo.put("rows", taskCouplesList);
            }
            tables.add(taskCoupleInfo);
            
            result.put("tables", tables);
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
    
    // Debug endpoint to diagnose task creation issues
    @GetMapping("/debug/task-diagnostics/{datasetId}")
    @ResponseBody
    public Map<String, Object> taskDiagnostics(@PathVariable Long datasetId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get dataset info
            Dataset dataset = datasetService.findDatasetById(datasetId);
            if (dataset == null) {
                result.put("error", "Dataset not found with ID: " + datasetId);
                return result;
            }
            
            Map<String, Object> datasetInfo = new HashMap<>();
            datasetInfo.put("id", dataset.getId());
            datasetInfo.put("name", dataset.getName());
            datasetInfo.put("description", dataset.getDescription());
            result.put("dataset", datasetInfo);
            
            // Get text pairs
            List<CoupleText> textPairs = coupleTextService.findAllCoupleTextsByDatasetId(datasetId);
            result.put("textPairCount", textPairs.size());
            
            // Get tasks for this dataset
            List<Task> tasks = taskRepository.findByDatasetIdWithAllRelations(datasetId);
            result.put("taskCount", tasks.size());
            
            // Get detailed task info
            List<Map<String, Object>> taskDetails = new ArrayList<>();
            for (Task task : tasks) {
                Map<String, Object> taskInfo = new HashMap<>();
                taskInfo.put("id", task.getId());
                taskInfo.put("deadline", task.getDateLimite());
                
                if (task.getUser() != null) {
                    Map<String, Object> userInfo = new HashMap<>();
                    userInfo.put("id", task.getUser().getId());
                    userInfo.put("username", task.getUser().getUsername());
                    userInfo.put("name", task.getUser().getFirstname() + " " + task.getUser().getLastname());
                    taskInfo.put("user", userInfo);
                } else {
                    taskInfo.put("userError", "Task has no assigned user");
                }
                
                if (task.getCouples() != null) {
                    taskInfo.put("coupleCount", task.getCouples().size());
                    
                    // Sample of first 5 couples
                    List<Map<String, Object>> sampleCouples = new ArrayList<>();
                    int count = 0;
                    for (CoupleText couple : task.getCouples()) {
                        if (count++ >= 5) break;
                        
                        Map<String, Object> coupleInfo = new HashMap<>();
                        coupleInfo.put("id", couple.getId());
                        coupleInfo.put("text1Sample", couple.getText1() != null ? 
                                     (couple.getText1().length() > 30 ? 
                                      couple.getText1().substring(0, 30) + "..." : 
                                      couple.getText1()) : null);
                        sampleCouples.add(coupleInfo);
                    }
                    taskInfo.put("coupleSamples", sampleCouples);
                } else {
                    taskInfo.put("coupleError", "Task has null couples list");
                }
                
                taskDetails.add(taskInfo);
            }
            result.put("tasks", taskDetails);
            
            // Database consistency check
            Query taskCountQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM tasks WHERE dataset_id = :datasetId");
            taskCountQuery.setParameter("datasetId", datasetId);
            long dbTaskCount = ((Number) taskCountQuery.getSingleResult()).longValue();
            result.put("databaseTaskCount", dbTaskCount);
            
            Query taskCoupleCountQuery = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM task_couple tc " +
                "JOIN tasks t ON tc.task_id = t.id " +
                "WHERE t.dataset_id = :datasetId");
            taskCoupleCountQuery.setParameter("datasetId", datasetId);
            long dbTaskCoupleCount = 0;
            try {
                dbTaskCoupleCount = ((Number) taskCoupleCountQuery.getSingleResult()).longValue();
            } catch (Exception e) {
                result.put("taskCoupleQueryError", e.getMessage());
            }
            result.put("databaseTaskCoupleCount", dbTaskCoupleCount);
            
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }

    /**
     * Debug endpoint to diagnose UI display issues
     */
    @GetMapping("/debug/ui-diagnostics/{datasetId}")
    @ResponseBody
    public Map<String, Object> uiDiagnostics(@PathVariable Long datasetId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get dataset
            Dataset dataset = datasetService.findDatasetById(datasetId);
            if (dataset == null) {
                result.put("error", "Dataset not found with ID: " + datasetId);
                return result;
            }
            
            // Test each method used by the UI to fetch tasks
            
            // Method 1: Direct query from task repository
            List<Task> simpleTasks = taskRepository.findByDatasetId(datasetId);
            
            // Method 2: Enhanced query with user and dataset
            List<Task> withRelationsTasks = taskRepository.findByDatasetIdWithUserAndDataset(datasetId);
            
            // Method 3: Full query with all relations
            List<Task> fullRelationsTasks = taskRepository.findByDatasetIdWithAllRelations(datasetId);
            
            // Method 4: Test through dataset's tasks relationship
            List<Task> datasetTasks = new ArrayList<>();
            if (dataset.getTasks() != null) {
                datasetTasks.addAll(dataset.getTasks());
            }
            
            // Method 5: Direct SQL query
            Query nativeQuery = entityManager.createNativeQuery(
                "SELECT t.id, t.date_limite, t.user_id, t.dataset_id " +
                "FROM tasks t WHERE t.dataset_id = :datasetId");
            nativeQuery.setParameter("datasetId", datasetId);
            List<?> nativeResults = new ArrayList<>();
            try {
                nativeResults = nativeQuery.getResultList();
                result.put("nativeTaskCount", nativeResults.size());
            } catch (Exception e) {
                result.put("nativeQueryError", e.getMessage());
                result.put("nativeTaskCount", 0);
            }
            
            // Results 
            result.put("simpleTaskCount", simpleTasks.size());
            result.put("withRelationsTaskCount", withRelationsTasks.size());
            result.put("fullRelationsTaskCount", fullRelationsTasks.size());
            result.put("datasetTasksCount", datasetTasks.size());
            
            // Get some sample data for each task
            List<Map<String, Object>> simpleSamples = new ArrayList<>();
            for (Task task : simpleTasks.subList(0, Math.min(3, simpleTasks.size()))) {
                Map<String, Object> sample = new HashMap<>();
                sample.put("id", task.getId());
                sample.put("hasUser", task.getUser() != null);
                sample.put("hasDataset", task.getDataset() != null);
                sample.put("couplePairCount", task.getCouples() != null ? task.getCouples().size() : 0);
                simpleSamples.add(sample);
            }
            result.put("simpleSamples", simpleSamples);
            
            // Check dataset-task bidirectional relationship
            boolean tasksInDataset = dataset.getTasks() != null && !dataset.getTasks().isEmpty();
            result.put("tasksInDataset", tasksInDataset);
            
            if (tasksInDataset) {
                boolean datasetsInTasks = true;
                for (Task task : dataset.getTasks()) {
                    if (task.getDataset() == null || !task.getDataset().getId().equals(datasetId)) {
                        datasetsInTasks = false;
                        break;
                    }
                }
                result.put("consistentBidirectionalRelationship", datasetsInTasks);
            }
            
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }

    // Add an endpoint to fix dataset references
    @GetMapping("/datasets/fix-references/{id}")
    @ResponseBody
    public Map<String, Object> fixDatasetReferences(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // First verify the dataset actually exists
            boolean datasetExists = datasetRepository.existsById(id);
            result.put("datasetExists", datasetExists);
            
            if (!datasetExists) {
                // List available datasets
                List<Object[]> availableDatasets = entityManager.createNativeQuery(
                    "SELECT id, name FROM dataset ORDER BY id")
                    .getResultList();
                
                List<Map<String, Object>> datasetsList = new ArrayList<>();
                for (Object[] row : availableDatasets) {
                    Map<String, Object> datasetInfo = new HashMap<>();
                    datasetInfo.put("id", row[0]);
                    datasetInfo.put("name", row[1]);
                    datasetsList.add(datasetInfo);
                }
                
                result.put("availableDatasets", datasetsList);
                result.put("message", "The specified dataset ID " + id + " does not exist in the database. " +
                          "Please use one of the available dataset IDs listed above.");
                result.put("success", false);
                return result;
            }
            
            Dataset dataset = datasetRepository.findById(id).get();
            result.put("datasetName", dataset.getName());
            
            // Use the service to fix any broken dataset references
            int fixedCount = taskAssignmentService.fixBrokenDatasetReferences(id);
            result.put("fixedTaskCount", fixedCount);
            
            if (fixedCount > 0) {
                result.put("message", "Fixed " + fixedCount + " tasks with broken dataset references");
            } else {
                result.put("message", "No broken dataset references found that need fixing");
            }
            
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }

    // Add a diagnostic endpoint for task creation
    @GetMapping("/datasets/check-tasks/{id}")
    @ResponseBody
    public Map<String, Object> checkTaskAssignments(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Dataset dataset = datasetService.findDatasetById(id);
            if (dataset == null) {
                result.put("success", false);
                result.put("error", "Dataset not found with ID: " + id);
                return result;
            }
            
            result.put("datasetId", dataset.getId());
            result.put("datasetName", dataset.getName());
            
            // Check if dataset entity has tasks
            List<Task> tasksFromDataset = dataset.getTasks();
            result.put("tasksInDatasetEntity", tasksFromDataset != null ? tasksFromDataset.size() : 0);
            
            // Get tasks from repository
            List<Task> tasksFromRepo = taskRepository.findByDatasetId(id);
            result.put("tasksInRepository", tasksFromRepo.size());
            
            // Check for couples
            List<CoupleText> coupleTexts = coupleTextService.findAllCoupleTextsByDatasetId(id);
            result.put("totalCoupleTexts", coupleTexts.size());
            
            // Check if any couples have null dataset reference
            long couplesWithNullDataset = coupleTexts.stream()
                .filter(c -> c.getDataset() == null)
                .count();
            result.put("couplesWithNullDataset", couplesWithNullDataset);
            
            // Check task-couple relationships
            List<Task> tasksWithCouples = taskRepository.findByDatasetIdWithAllRelations(id);
            
            List<Map<String, Object>> taskDetails = new ArrayList<>();
            for (Task task : tasksWithCouples) {
                Map<String, Object> taskInfo = new HashMap<>();
                taskInfo.put("id", task.getId());
                taskInfo.put("userId", task.getUser() != null ? task.getUser().getId() : null);
                taskInfo.put("datasetId", task.getDataset() != null ? task.getDataset().getId() : null);
                
                // Check couples in this task
                if (task.getCouples() != null) {
                    taskInfo.put("coupleCount", task.getCouples().size());
                    
                    // Check if any couples have issues
                    long invalidCouples = task.getCouples().stream()
                        .filter(c -> c.getId() == null || c.getDataset() == null)
                        .count();
                    taskInfo.put("invalidCouples", invalidCouples);
                } else {
                    taskInfo.put("coupleCount", 0);
                    taskInfo.put("couplesNull", true);
                }
                
                taskDetails.add(taskInfo);
            }
            result.put("taskDetails", taskDetails);
            
            // Get users with USER role
            Optional<Role> userRoleOpt = roleRepository.findByRole("USER");
            if (userRoleOpt.isPresent()) {
                Role userRole = userRoleOpt.get();
                List<User> annotators = userRepository.findAllByRolesContaining(userRole);
                result.put("availableAnnotators", annotators.size());
            } else {
                result.put("availableAnnotators", 0);
                result.put("roleError", "USER role not found");
            }
            
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }

    // Add an endpoint to manually create a test task
    @GetMapping("/datasets/create-test-task/{id}")
    @ResponseBody
    public Map<String, Object> createTestTask(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get dataset
            Dataset dataset = datasetService.findDatasetById(id);
            if (dataset == null) {
                result.put("success", false);
                result.put("error", "Dataset not found with ID: " + id);
                return result;
            }
            
            // Get a user with USER role
            Optional<Role> userRoleOpt = roleRepository.findByRole("USER");
            if (!userRoleOpt.isPresent()) {
                result.put("success", false);
                result.put("error", "USER role not found in database");
                return result;
            }
            
            List<User> annotators = userRepository.findAllByRolesContaining(userRoleOpt.get());
            if (annotators.isEmpty()) {
                result.put("success", false);
                result.put("error", "No users with USER role found");
                return result;
            }
            
            User testUser = annotators.get(0);
            
            // Get a few text pairs
            List<CoupleText> couples = coupleTextService.findAllCoupleTextsByDatasetId(id);
            if (couples.isEmpty()) {
                result.put("success", false);
                result.put("error", "No text pairs found in dataset");
                return result;
            }
            
            // Limit to 5 pairs maximum
            List<CoupleText> testCouples = couples.subList(0, Math.min(5, couples.size()));
            
            // Create test task
            Task testTask = new Task();
            testTask.setDateLimite(new Date(System.currentTimeMillis() + 86400000)); // Tomorrow
            testTask.setUser(testUser);
            testTask.setDataset(dataset);
            testTask.setCouples(new ArrayList<>(testCouples));
            
            // Save using pure JPA
            entityManager.persist(testTask);
            entityManager.flush();
            
            result.put("success", true);
            result.put("taskId", testTask.getId());
            result.put("userId", testUser.getId());
            result.put("username", testUser.getUsername());
            result.put("coupleCount", testCouples.size());
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }

    // Add an endpoint to directly check dataset existence in the database
    @GetMapping("/datasets/verify-dataset/{id}")
    @ResponseBody
    public Map<String, Object> verifyDatasetExists(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Check if dataset exists in the repository
            boolean existsInRepo = datasetRepository.existsById(id);
            result.put("existsInRepository", existsInRepo);
            
            // Try to fetch from repository
            Optional<Dataset> datasetOpt = datasetRepository.findById(id);
            result.put("foundByRepository", datasetOpt.isPresent());
            
            if (datasetOpt.isPresent()) {
                Dataset dataset = datasetOpt.get();
                result.put("name", dataset.getName());
                result.put("description", dataset.getDescription());
            }
            
            // Try direct SQL query to verify database state
            try {
                Query nativeQuery = entityManager.createNativeQuery(
                    "SELECT id, name FROM dataset WHERE id = :id");
                nativeQuery.setParameter("id", id);
                List<?> resultList = nativeQuery.getResultList();
                
                result.put("existsInDatabase", !resultList.isEmpty());
                result.put("databaseResults", resultList);
            } catch (Exception e) {
                result.put("sqlError", e.getMessage());
            }
            
            // List all dataset IDs in the database
            try {
                Query allDatasetsQuery = entityManager.createNativeQuery(
                    "SELECT id, name FROM dataset ORDER BY id");
                List<Object[]> allDatasets = allDatasetsQuery.getResultList();
                
                List<Map<String, Object>> datasetsList = new ArrayList<>();
                for (Object[] row : allDatasets) {
                    Map<String, Object> datasetInfo = new HashMap<>();
                    datasetInfo.put("id", row[0]);
                    datasetInfo.put("name", row[1]);
                    datasetsList.add(datasetInfo);
                }
                
                result.put("allDatasets", datasetsList);
            } catch (Exception e) {
                result.put("listError", e.getMessage());
            }
            
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
}