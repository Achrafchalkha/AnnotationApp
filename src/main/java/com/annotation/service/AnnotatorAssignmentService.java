package com.annotation.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.annotation.model.CoupleText;
import com.annotation.model.Dataset;
import com.annotation.model.Task;
import com.annotation.model.User;
import com.annotation.repository.TaskRepository;

@Service
@Transactional
public class AnnotatorAssignmentService {

    private final CoupleTextServiceImpl coupleTextService;
    private final TaskRepository taskRepository;

    public AnnotatorAssignmentService(CoupleTextServiceImpl coupleTextService, TaskRepository taskRepository) {
        this.coupleTextService = coupleTextService;
        this.taskRepository = taskRepository;
    }

    public void assignTasksToAnnotators(Dataset dataset, List<User> annotators, Date deadline) {
        try {
            System.out.println("======= STARTING ANNOTATOR ASSIGNMENT =======");
            System.out.println("Dataset ID: " + dataset.getId() + ", Name: " + dataset.getName());
            System.out.println("Annotators count: " + annotators.size());
            System.out.println("Deadline: " + deadline);
            
            // Get all text pairs from the dataset
            Long datasetId = dataset.getId();
            List<CoupleText> allCouples = coupleTextService.findAllCoupleTextsByDatasetId(datasetId);
            
            System.out.println("Found " + allCouples.size() + " text pairs in dataset");
            
            if (allCouples.isEmpty()) {
                throw new IllegalArgumentException("No text pairs found in dataset with ID: " + datasetId);
            }
            
            // Step 1: First, clear any existing tasks for this dataset
            // This is a cleaner approach than the previous implementation
            List<Task> existingTasks = taskRepository.findByDatasetId(datasetId);
            if (!existingTasks.isEmpty()) {
                System.out.println("Removing " + existingTasks.size() + " existing tasks for dataset " + datasetId);
                taskRepository.deleteAll(existingTasks);
                System.out.println("Deleted existing tasks for dataset " + datasetId);
            } else {
                System.out.println("No existing tasks found for dataset");
            }
            
            // Step 2: Shuffle the text pairs to ensure random distribution
            List<CoupleText> shuffledCouples = new ArrayList<>(allCouples);
            Collections.shuffle(shuffledCouples);
            System.out.println("Text pairs shuffled successfully");
            
            // Step 3: Distribute text pairs using round-robin approach
            int annotatorCount = annotators.size();
            Map<User, List<CoupleText>> taskMap = new HashMap<>();
            
            // Initialize lists for each annotator
            for (User annotator : annotators) {
                taskMap.put(annotator, new ArrayList<>());
            }
            
            // Distribute the text pairs round-robin
            for (int i = 0; i < shuffledCouples.size(); i++) {
                User annotator = annotators.get(i % annotatorCount);
                CoupleText couple = shuffledCouples.get(i);
                taskMap.get(annotator).add(couple);
            }
            
            // Step 4: Create tasks and save them - using a simpler approach
            List<Task> newTasks = new ArrayList<>();
            
            for (User annotator : annotators) {
                List<CoupleText> couples = taskMap.get(annotator);
                if (couples.isEmpty()) {
                    continue;
                }
                
                System.out.println("Creating task for " + annotator.getUsername() + " with " + couples.size() + " text pairs");
                
                // Create a simple task
                Task task = new Task();
                task.setDateLimite(deadline);
                task.setUser(annotator);
                task.setDataset(dataset);
                task.setCouples(new ArrayList<>(couples)); // Create a new list to avoid reference issues
                
                Task savedTask = taskRepository.save(task);
                newTasks.add(savedTask);
                
                System.out.println("Created task with ID: " + savedTask.getId() + 
                                 " for user " + annotator.getUsername() + 
                                 " with " + couples.size() + " text pairs");
            }
            
            System.out.println("Created " + newTasks.size() + " tasks successfully");
            System.out.println("======= ANNOTATOR ASSIGNMENT COMPLETED =======");
        } catch (Exception e) {
            System.out.println("CRITICAL ERROR IN ANNOTATOR ASSIGNMENT: " + e.getMessage());
            e.printStackTrace();
            throw e; // Rethrow to propagate the error
        }
    }
    
    public void removeAnnotatorFromDataset(Long datasetId, Long annotatorId) {
        try {
            System.out.println("======= REMOVING ANNOTATOR =======");
            System.out.println("Dataset ID: " + datasetId + ", Annotator ID: " + annotatorId);
            
            List<Task> tasks = taskRepository.findByDatasetIdAndUserId(datasetId, annotatorId);
            
            if (tasks.isEmpty()) {
                System.out.println("No tasks found for annotator " + annotatorId + " on dataset " + datasetId);
                return;
            }
            
            System.out.println("Found " + tasks.size() + " tasks to remove");
            for (Task task : tasks) {
                System.out.println("Task ID: " + task.getId() + 
                                 ", User: " + (task.getUser() != null ? task.getUser().getUsername() : "null") + 
                                 ", Couples: " + task.getCouples().size());
            }
            
            // Simply delete the tasks
            taskRepository.deleteAll(tasks);
            System.out.println("Tasks removed successfully");
            System.out.println("======= ANNOTATOR REMOVAL COMPLETED =======");
        } catch (Exception e) {
            System.out.println("ERROR REMOVING ANNOTATOR: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
} 