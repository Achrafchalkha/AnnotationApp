package com.annotation.controller;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
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

@Controller
@RequestMapping("/admin")
public class DatasetController {

    private final DatasetServiceImpl datasetService;
    private final CoupleTextServiceImpl coupleTextService;
    private final AsyncDatasetParserService asyncDatasetParserService;
    private final DefaultUserServiceImpl userService;

    // Constructor-based injection for both services
    @Autowired
    public DatasetController(DatasetServiceImpl datasetService, CoupleTextServiceImpl coupleTextService, AsyncDatasetParserService asyncDatasetParserService,DefaultUserServiceImpl userService) {
        this.datasetService = datasetService;
        this.coupleTextService = coupleTextService;
        this.asyncDatasetParserService = asyncDatasetParserService;
        this.userService = userService;
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
        Page<CoupleText> coupleTextsPage = coupleTextService.getCoupleTextsByDatasetId(id, page, size);

        if (dataset == null) {
            model.addAttribute("errorMessage", "Dataset not found");
            return "redirect:/admin/datasets";
        }

        int totalPages = coupleTextsPage.getTotalPages();
        int currentPage = page;

        // Pagination window control (show up to 5 pages max, centered around current)
        int startPage = Math.max(0, currentPage - 2);
        int endPage = Math.min(totalPages - 1, currentPage + 2);

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
}