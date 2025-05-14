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
import com.annotation.service.AnnotatorAssignmentService;

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
    private final AnnotatorAssignmentService annotatorAssignmentService;

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
        AnnotatorAssignmentService annotatorAssignmentService) {
        this.datasetService = datasetService;
        this.coupleTextService = coupleTextService;
        this.asyncDatasetParserService = asyncDatasetParserService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.taskRepository = taskRepository;
        this.annotatorAssignmentService = annotatorAssignmentService;
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
        // Use direct SQL query to ensure we get all the data we need in one go
        List<Task> tasks = null;
        try {
            tasks = taskRepository.findByDatasetIdWithUserAndDataset(id);
            System.out.println("Found " + tasks.size() + " tasks for dataset ID: " + id);
            
            // Add debug information for each task
            for (Task task : tasks) {
                User user = task.getUser();
                if (user != null) {
                    System.out.println("Task ID: " + task.getId() + 
                        ", User: " + user.getFirstname() + " " + user.getLastname() + 
                        " (ID: " + user.getId() + ")");
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
    
    // New endpoint to show annotator assignment modal
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
        
        // Get all users with USER role
        Optional<Role> userRoleOpt = roleRepository.findByRole("USER");
        List<User> annotators = new ArrayList<>();
        if (userRoleOpt.isPresent()) {
            Role userRole = userRoleOpt.get();
            annotators = userRepository.findAllByRolesContaining(userRole);
            
            // Debug information
            System.out.println("Found " + annotators.size() + " annotators with USER role");
            for (User annotator : annotators) {
                System.out.println("Annotator: " + annotator.getFirstname() + " " + annotator.getLastname() + " (ID: " + annotator.getId() + ")");
            }
        } else {
            System.out.println("USER role not found in the database");
        }
        
        model.addAttribute("dataset", dataset);
        model.addAttribute("annotators", annotators);
        
        // Use the full template now that we've fixed the issues
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
    
    // New endpoint to process annotator assignment
    @PostMapping("/datasets/assign-annotators/{id}")
    public String assignAnnotatorsToDataset(
            @PathVariable Long id,
            @RequestParam("annotatorIds") List<Long> annotatorIds,
            @RequestParam("deadline") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date deadline,
            RedirectAttributes redirectAttributes) {
        
        if (annotatorIds.size() < 3) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select at least 3 annotators");
            return "redirect:/admin/datasets/assign-annotators/" + id;
        }
        
        Dataset dataset = datasetService.findDatasetById(id);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Dataset not found");
            return "redirect:/admin/datasets";
        }
        
        try {
            // Get all text couples for this dataset
            List<CoupleText> allCouples = coupleTextService.findAllCoupleTextsByDatasetId(id);
            if (allCouples.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "No text pairs found in this dataset");
                return "redirect:/admin/datasets/details/" + id;
            }
            
            // Get selected annotators
            List<User> selectedAnnotators = userRepository.findAllById(annotatorIds);
            
            // Use the dedicated service to assign tasks to annotators
            annotatorAssignmentService.assignTasksToAnnotators(dataset, selectedAnnotators, deadline);
            
            redirectAttributes.addFlashAttribute("success", 
                "Successfully assigned " + allCouples.size() + " text pairs to " + selectedAnnotators.size() + " annotators");
            
        } catch (Exception e) {
            System.out.println("Error assigning annotators: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", "Error assigning annotators: " + e.getMessage());
        }
        
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
            annotatorAssignmentService.removeAnnotatorFromDataset(datasetId, userId);
            
            redirectAttributes.addFlashAttribute("success", "Annotator removed from dataset successfully");
        } catch (Exception e) {
            System.out.println("Error removing annotator: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", "Error removing annotator: " + e.getMessage());
        }
        
        return "redirect:/admin/datasets/details/" + datasetId;
    }
}