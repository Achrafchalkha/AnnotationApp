package com.annotation.service;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.annotation.model.CoupleText;
import com.annotation.model.Dataset;
import com.annotation.model.Task;
import com.annotation.model.User;
import com.annotation.repository.TaskRepository;
import com.annotation.repository.DatasetRepository;
import com.annotation.repository.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

@Service
@Transactional
public class TaskAssignmentService {

    private static final Logger logger = Logger.getLogger(TaskAssignmentService.class.getName());
    
    private final CoupleTextServiceImpl coupleTextService;
    private final TaskRepository taskRepository;
    private final DatasetRepository datasetRepository;
    private final UserRepository userRepository;
    
    @PersistenceContext
    private EntityManager entityManager;

    public TaskAssignmentService(
            CoupleTextServiceImpl coupleTextService, 
            TaskRepository taskRepository,
            DatasetRepository datasetRepository,
            UserRepository userRepository) {
        this.coupleTextService = coupleTextService;
        this.taskRepository = taskRepository;
        this.datasetRepository = datasetRepository;
        this.userRepository = userRepository;
    }

    /**
     * Assigns text pairs to annotators for a specific dataset
     * Using an improved distribution algorithm
     * 
     * @param annotators List of users to assign tasks to
     * @param dataset The dataset containing text pairs
     * @param deadline The deadline for completion
     * @throws IllegalArgumentException if dataset has no text pairs
     * @throws RuntimeException if an error occurs during task creation
     */
    @Transactional
    public void assignTasksToAnnotators(List<User> annotators, Dataset dataset, Date deadline) {
        logger.info("Starting task assignment for dataset ID: " + dataset.getId());
        
        if (annotators == null || annotators.isEmpty()) {
            logger.severe("No annotators provided for task assignment");
            throw new IllegalArgumentException("Cannot assign tasks: No annotators provided");
        }
        
        if (dataset == null) {
            logger.severe("Dataset is null");
            throw new IllegalArgumentException("Cannot assign tasks: Dataset is null");
        }
        
        if (deadline == null) {
            logger.severe("Deadline is null");
            throw new IllegalArgumentException("Cannot assign tasks: Deadline is null");
        }
        
        try {
            // First verify that the dataset exists in the database
            if (!datasetRepository.existsById(dataset.getId())) {
                logger.severe("Dataset with ID " + dataset.getId() + " does not exist in database");
                throw new IllegalArgumentException("Dataset with ID " + dataset.getId() + " does not exist in database");
            }
            
            // IMPORTANT: Get managed entities to avoid foreign key issues
            Dataset managedDataset = datasetRepository.findById(dataset.getId())
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + dataset.getId()));
                
            // Explicitly refresh the dataset entity from the database
            entityManager.refresh(managedDataset);
            logger.info("Retrieved managed dataset: " + managedDataset.getName() + " (ID: " + managedDataset.getId() + ")");
            
            List<User> managedAnnotators = new ArrayList<>();
            for (User annotator : annotators) {
                User managedUser = userRepository.findById(annotator.getId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + annotator.getId()));
                managedAnnotators.add(managedUser);
            }
            
            // Step 1: Retrieve all text pairs from the dataset
            Long datasetId = managedDataset.getId();
            List<CoupleText> allCouples = coupleTextService.findAllCoupleTextsByDatasetId(datasetId);
            logger.info("Found " + allCouples.size() + " text pairs in dataset " + datasetId);
            
            if (allCouples.isEmpty()) {
                logger.severe("No text pairs found in dataset with ID: " + datasetId);
                throw new IllegalArgumentException("Cannot assign tasks: No text pairs found in dataset with ID: " + datasetId);
            }
            
            // Step 2: Remove any existing task assignments for this dataset
            List<Task> existingTasks = taskRepository.findByDatasetId(datasetId);
            if (!existingTasks.isEmpty()) {
                logger.info("Removing " + existingTasks.size() + " existing tasks for dataset " + datasetId);
                taskRepository.deleteAll(existingTasks);
                entityManager.flush(); // Ensure deletions are committed
                logger.info("Existing tasks deleted successfully");
                
                // Refresh the dataset entity again after deletion to ensure it's up-to-date
                entityManager.refresh(managedDataset);
            }
            
            // Step 3: Shuffle text pairs for random distribution
            List<CoupleText> shuffledCouples = new ArrayList<>(allCouples);
            Collections.shuffle(shuffledCouples);
            logger.info("Text pairs shuffled for random distribution");
            
            // Step 4: Distribute text pairs evenly among annotators
            Map<User, List<CoupleText>> assignmentMap = distributeTextPairsEvenly(managedAnnotators, shuffledCouples);
            
            // Step 5: Create and save task entities
            createTasksWithTextPairs(assignmentMap, managedDataset, deadline);
            
            // Verification
            entityManager.flush(); // Ensure all changes are committed
            List<Task> createdTasks = taskRepository.findByDatasetId(datasetId);
            logger.info("Task assignment completed. Created " + createdTasks.size() + " tasks.");
            
            if (createdTasks.isEmpty()) {
                logger.severe("Failed to create tasks. No tasks found after assignment.");
                throw new RuntimeException("Task creation failed. No tasks were created.");
            }
            
            // Print detailed task information for debugging
            for (Task task : createdTasks) {
                logger.info("Task [ID: " + task.getId() + 
                          ", User: " + (task.getUser() != null ? task.getUser().getUsername() : "null") + 
                          ", Couples: " + (task.getCouples() != null ? task.getCouples().size() : 0) + "]");
            }
            
        } catch (Exception e) {
            logger.severe("Error in task assignment: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to assign tasks: " + e.getMessage(), e);
        }
    }
    
    /**
     * Distributes text pairs evenly among annotators
     * Each annotator receives approximately the same number of pairs
     */
    private Map<User, List<CoupleText>> distributeTextPairsEvenly(List<User> annotators, List<CoupleText> shuffledCouples) {
        Map<User, List<CoupleText>> assignmentMap = new HashMap<>();
        int annotatorCount = annotators.size();
        int totalCouples = shuffledCouples.size();
        
        logger.info("Distributing " + totalCouples + " text pairs among " + annotatorCount + " annotators");
        
        // Calculate base number of pairs per annotator
        int basePairsPerAnnotator = totalCouples / annotatorCount;
        int remainingPairs = totalCouples % annotatorCount;
        
        logger.info("Base pairs per annotator: " + basePairsPerAnnotator + ", Remaining pairs: " + remainingPairs);
        
        // Initialize lists for each annotator
        for (User annotator : annotators) {
            assignmentMap.put(annotator, new ArrayList<>());
        }
        
        // Distribute pairs evenly
        int currentIndex = 0;
        for (int i = 0; i < annotatorCount; i++) {
            User annotator = annotators.get(i);
            int pairsForThisAnnotator = basePairsPerAnnotator;
            
            // Distribute remaining pairs one by one to the first 'remainingPairs' annotators
            if (i < remainingPairs) {
                pairsForThisAnnotator++;
            }
            
            logger.info("Assigning " + pairsForThisAnnotator + " pairs to annotator: " + annotator.getUsername());
            
            // Add couples to this annotator
            for (int j = 0; j < pairsForThisAnnotator && currentIndex < totalCouples; j++) {
                assignmentMap.get(annotator).add(shuffledCouples.get(currentIndex++));
            }
        }
        
        // Log distribution stats
        for (User annotator : annotators) {
            List<CoupleText> pairs = assignmentMap.get(annotator);
            logger.info("Annotator " + annotator.getUsername() + " (ID: " + annotator.getId() + "): " + 
                      pairs.size() + " text pairs");
        }
        
        return assignmentMap;
    }
    
    /**
     * Creates task entities with proper text pair assignments
     * and saves them to the database
     */
    private void createTasksWithTextPairs(Map<User, List<CoupleText>> assignmentMap, Dataset dataset, Date deadline) {
        logger.info("Creating tasks with text pair assignments...");
        
        try {
            // Make sure we're working with a managed dataset entity
            Dataset managedDataset = entityManager.find(Dataset.class, dataset.getId());
            if (managedDataset == null) {
                logger.severe("Could not find dataset with ID: " + dataset.getId());
                throw new RuntimeException("Dataset with ID " + dataset.getId() + " not found in database");
            }
            
            // Refresh the dataset entity to ensure we have the latest state
            entityManager.refresh(managedDataset);
            logger.info("Using managed dataset: ID=" + managedDataset.getId() + ", Name=" + managedDataset.getName());
            
            // Count total text pairs to be assigned
            int totalPairs = 0;
            for (List<CoupleText> couples : assignmentMap.values()) {
                totalPairs += couples.size();
            }
            logger.info("About to create tasks for " + assignmentMap.size() + " annotators with " + totalPairs + " total text pairs");
            
            if (assignmentMap.isEmpty()) {
                logger.severe("Assignment map is empty - no tasks to create");
                return;
            }
            
            if (totalPairs == 0) {
                logger.severe("No text pairs to assign - cannot create empty tasks");
                return;
            }
            
            List<Task> tasksToSave = new ArrayList<>();
            
            for (Map.Entry<User, List<CoupleText>> entry : assignmentMap.entrySet()) {
                User annotator = entry.getKey();
                List<CoupleText> couples = entry.getValue();
                
                if (annotator == null) {
                    logger.warning("Skipping null annotator during task creation");
                    continue;
                }
                
                if (couples == null || couples.isEmpty()) {
                    logger.warning("No text pairs assigned to annotator: " + annotator.getUsername());
                    continue;
                }
                
                logger.info("Creating task for " + annotator.getUsername() + " with " + couples.size() + " text pairs");
                
                // Check that each couple has proper database IDs
                boolean allCouplesValid = true;
                for (CoupleText couple : couples) {
                    if (couple.getId() == null) {
                        logger.warning("Text pair has null ID - may cause persistence issues");
                        allCouplesValid = false;
                        break;
                    }
                }
                
                if (!allCouplesValid) {
                    logger.warning("Skipping task creation due to invalid text pairs");
                    continue;
                }
                
                // Create the task with proper references
                Task task = new Task();
                task.setDateLimite(deadline);
                task.setUser(annotator); // This should be a managed entity
                
                // Establish proper bidirectional relationship
                ensureBidirectionalRelationship(task, managedDataset);
                
                // Make sure couples collection is initialized
                if (task.getCouples() == null) {
                    task.setCouples(new ArrayList<>());
                }
                
                // Add couples one by one to avoid collection issues
                for (CoupleText couple : couples) {
                    task.addCouple(couple);
                }
                
                logger.info("Task prepared with " + task.getCouples().size() + " text pairs");
                tasksToSave.add(task);
            }
            
            // Save all tasks
            if (!tasksToSave.isEmpty()) {
                logger.info("Saving " + tasksToSave.size() + " tasks to database");
                
                // Save each task individually and log the result
                for (int i = 0; i < tasksToSave.size(); i++) {
                    Task task = tasksToSave.get(i);
                    try {
                        entityManager.persist(task);
                        logger.info("Task #" + (i+1) + " persisted successfully with ID: " + task.getId());
                    } catch (Exception e) {
                        logger.severe("Error persisting task #" + (i+1) + ": " + e.getMessage());
                        throw e; // Rethrow to trigger transaction rollback
                    }
                }
                
                // Flush changes to ensure they're committed
                logger.info("Flushing all changes to database");
                entityManager.flush();
                logger.info("Successfully saved " + tasksToSave.size() + " tasks to database");
                
                // Double-check that tasks were actually saved
                try {
                    Long datasetId = managedDataset.getId();
                    Query query = entityManager.createQuery("SELECT COUNT(t) FROM Task t WHERE t.dataset.id = :datasetId");
                    query.setParameter("datasetId", datasetId);
                    Long taskCount = (Long) query.getSingleResult();
                    logger.info("Verification: Found " + taskCount + " tasks in database for dataset " + datasetId);
                } catch (Exception e) {
                    logger.warning("Could not verify task count: " + e.getMessage());
                }
            } else {
                logger.severe("No tasks to save! This indicates a problem with task creation.");
            }
        } catch (Exception e) {
            logger.severe("Error in task creation: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to create tasks: " + e.getMessage(), e);
        }
    }

    /**
     * Ensures proper bidirectional relationship between Task and Dataset
     * This is important for JPA to correctly manage the relationships
     */
    private void ensureBidirectionalRelationship(Task task, Dataset dataset) {
        if (task == null || dataset == null) {
            logger.warning("Cannot establish bidirectional relationship: task or dataset is null");
            return;
        }
        
        // Set Task → Dataset relationship
        task.setDataset(dataset);
        
        // Set Dataset → Task relationship (using helper method in Dataset)
        if (dataset.getTasks() == null) {
            dataset.setTasks(new ArrayList<>());
        }
        
        // Only add if not already present to avoid duplicates
        if (!dataset.getTasks().contains(task)) {
            dataset.addTask(task);
            logger.info("Established bidirectional relationship between task and dataset");
        }
    }

    /**
     * Removes an annotator from a dataset by deleting their tasks
     */
    @Transactional
    public void removeAnnotatorFromDataset(Long datasetId, Long annotatorId) {
        logger.info("Removing annotator " + annotatorId + " from dataset " + datasetId);
        
        try {
            List<Task> tasks = taskRepository.findByDatasetIdAndUserId(datasetId, annotatorId);
            
            if (tasks.isEmpty()) {
                logger.warning("No tasks found for annotator " + annotatorId + " on dataset " + datasetId);
                return;
            }
            
            logger.info("Found " + tasks.size() + " tasks to remove");
            taskRepository.deleteAll(tasks);
            entityManager.flush();
            logger.info("Tasks successfully removed");
            
        } catch (Exception e) {
            logger.severe("Error removing annotator: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to remove annotator: " + e.getMessage(), e);
        }
    }
    
    /**
     * Fixes any broken dataset references in tasks
     * This helps resolve foreign key constraint issues
     * 
     * @param datasetId The dataset ID to use for fixing tasks
     * @return Number of tasks fixed
     */
    @Transactional
    public int fixBrokenDatasetReferences(Long datasetId) {
        logger.info("Attempting to fix broken dataset references for dataset ID: " + datasetId);
        
        try {
            // Get the dataset
            Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetId));
            
            // Find tasks with null or invalid dataset ID using native query
            Query query = entityManager.createNativeQuery(
                "SELECT t.id FROM tasks t " +
                "LEFT JOIN dataset d ON t.dataset_id = d.id " +
                "WHERE d.id IS NULL");
            List<Number> taskIds = query.getResultList();
            
            if (taskIds.isEmpty()) {
                logger.info("No tasks with broken dataset references found");
                return 0;
            }
            
            logger.info("Found " + taskIds.size() + " tasks with broken dataset references");
            
            // Fix each task
            int fixedCount = 0;
            for (Number taskId : taskIds) {
                // Use native query to update directly to avoid any JPA caching issues
                Query updateQuery = entityManager.createNativeQuery(
                    "UPDATE tasks SET dataset_id = :datasetId WHERE id = :taskId");
                updateQuery.setParameter("datasetId", datasetId);
                updateQuery.setParameter("taskId", taskId.longValue());
                
                int updated = updateQuery.executeUpdate();
                if (updated > 0) {
                    fixedCount++;
                    logger.info("Fixed task ID: " + taskId);
                }
            }
            
            entityManager.flush();
            logger.info("Fixed " + fixedCount + " tasks with broken dataset references");
            
            return fixedCount;
            
        } catch (Exception e) {
            logger.severe("Error fixing broken dataset references: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to fix broken dataset references: " + e.getMessage(), e);
        }
    }
} 