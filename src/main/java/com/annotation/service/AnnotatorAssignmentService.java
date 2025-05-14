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
        // Get all text pairs from the dataset
        Long datasetId = dataset.getId();
        List<CoupleText> allCouples = coupleTextService.findAllCoupleTextsByDatasetId(datasetId);
        
        if (allCouples.isEmpty()) {
            throw new IllegalArgumentException("No text pairs found in dataset with ID: " + datasetId);
        }
        
        // Step 1: First, remove any existing task assignments for this dataset
        List<Task> existingTasks = taskRepository.findByDatasetId(datasetId);
        if (!existingTasks.isEmpty()) {
            System.out.println("Removing " + existingTasks.size() + " existing tasks for dataset " + datasetId);
            taskRepository.deleteAll(existingTasks);
        }
        
        // Step 2: Shuffle the text pairs to ensure random distribution
        List<CoupleText> shuffledCouples = new ArrayList<>(allCouples);
        Collections.shuffle(shuffledCouples);
        
        // Step 3: Create a task map to track assignments
        Map<User, List<CoupleText>> taskMap = new HashMap<>();
        
        // Initialize empty lists for each annotator
        for (User annotator : annotators) {
            taskMap.put(annotator, new ArrayList<>());
        }
        
        // Step 4: Distribute text pairs using round-robin approach
        int annotatorCount = annotators.size();
        for (int i = 0; i < shuffledCouples.size(); i++) {
            User annotator = annotators.get(i % annotatorCount);
            CoupleText couple = shuffledCouples.get(i);
            taskMap.get(annotator).add(couple);
        }
        
        // Step 5: Create and save task entities
        List<Task> tasks = new ArrayList<>();
        for (Map.Entry<User, List<CoupleText>> entry : taskMap.entrySet()) {
            User annotator = entry.getKey();
            List<CoupleText> couples = entry.getValue();
            
            if (!couples.isEmpty()) {
                Task task = new Task(deadline, annotator, dataset);
                task.getCouples().addAll(couples);
                tasks.add(task);
                
                System.out.println("Creating task for annotator " + annotator.getUsername() + 
                                  " with " + couples.size() + " text pairs");
            }
        }
        
        // Step 6: Save all tasks in a batch
        tasks = taskRepository.saveAll(tasks);
        System.out.println("Saved " + tasks.size() + " tasks with " + 
                           tasks.stream().mapToInt(t -> t.getCouples().size()).sum() + 
                           " total text pairs");
    }
    
    public void removeAnnotatorFromDataset(Long datasetId, Long annotatorId) {
        List<Task> tasks = taskRepository.findByDatasetIdAndUserId(datasetId, annotatorId);
        
        if (tasks.isEmpty()) {
            System.out.println("No tasks found for annotator " + annotatorId + " on dataset " + datasetId);
            return;
        }
        
        System.out.println("Removing " + tasks.size() + " tasks for annotator " + annotatorId);
        taskRepository.deleteAll(tasks);
    }
} 