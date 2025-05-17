package com.annotation.repository;

import com.annotation.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByDatasetId(Long datasetId);
    List<Task> findByUserId(Long userId);
    List<Task> findByDatasetIdAndUserId(Long datasetId, Long userId);
    
    @Query("SELECT t FROM Task t JOIN FETCH t.user JOIN FETCH t.dataset WHERE t.dataset.id = :datasetId")
    List<Task> findByDatasetIdWithUserAndDataset(@Param("datasetId") Long datasetId);
    
    /**
     * Retrieves a task with all necessary relationships eagerly loaded to prevent LazyInitializationExceptions
     */
    @Query("SELECT DISTINCT t FROM Task t " +
           "JOIN FETCH t.user u " +
           "JOIN FETCH t.dataset d " +
           "LEFT JOIN FETCH t.couples c " +
           "LEFT JOIN FETCH d.classesPossibles " +
           "WHERE t.id = :taskId")
    Task findByIdWithCouples(@Param("taskId") Long taskId);
    
    /**
     * Retrieves a task with accurate pair count, avoiding Cartesian products
     */
    @Query(value = "SELECT t.* FROM tasks t " +
           "JOIN users u ON t.user_id = u.id " +
           "JOIN dataset d ON t.dataset_id = d.id " +
           "WHERE t.id = :taskId", nativeQuery = true)
    Task findByIdWithoutCouples(@Param("taskId") Long taskId);
    
    /**
     * Counts the exact number of text pairs associated with a task
     */
    @Query(value = "SELECT COUNT(*) FROM task_couple WHERE task_id = :taskId", nativeQuery = true)
    int countCouplesByTaskId(@Param("taskId") Long taskId);
    
    /**
     * Gets all text pairs for a specific task
     */
    @Query(value = "SELECT c.* FROM task_couple tc " +
           "JOIN couple_text c ON tc.couple_id = c.id " +
           "WHERE tc.task_id = :taskId", nativeQuery = true)
    List<Object[]> findCouplesByTaskId(@Param("taskId") Long taskId);
    
    @Query("SELECT DISTINCT t FROM Task t " +
           "LEFT JOIN FETCH t.user " +
           "LEFT JOIN FETCH t.dataset " +
           "LEFT JOIN FETCH t.couples " +
           "WHERE t.dataset.id = :datasetId")
    List<Task> findByDatasetIdWithAllRelations(@Param("datasetId") Long datasetId);
    
    @Query("SELECT DISTINCT t FROM Task t " +
           "LEFT JOIN FETCH t.user " +
           "LEFT JOIN FETCH t.dataset " +
           "LEFT JOIN FETCH t.couples " +
           "WHERE t.user.id = :userId")
    List<Task> findByUserIdWithAllRelations(@Param("userId") Long userId);
    
    long countByDatasetId(Long datasetId);
    long countByUserId(Long userId);

    @Query("SELECT DISTINCT t FROM Task t " +
           "LEFT JOIN FETCH t.user " +
           "LEFT JOIN FETCH t.dataset " +
           "LEFT JOIN FETCH t.couples " +
           "LEFT JOIN FETCH t.dataset.classesPossibles " +
           "ORDER BY t.id")
    List<Task> findAllWithRelationships();

    @Query(value = "SELECT t.* FROM tasks t", nativeQuery = true)
    List<Task> findAllTasksNative();

    @Query("SELECT DISTINCT t FROM Task t " +
           "LEFT JOIN FETCH t.user " +
           "LEFT JOIN FETCH t.dataset " +
           "LEFT JOIN FETCH t.couples " +
           "LEFT JOIN FETCH t.dataset.classesPossibles")
    List<Task> findAllWithoutOrder();

    /**
     * Counts the exact number of annotated text pairs associated with a task
     */
    @Query(value = "SELECT COUNT(*) FROM task_couple tc " +
           "JOIN couple_text c ON tc.couple_id = c.id " +
           "WHERE tc.task_id = :taskId " +
           "AND c.class_annotation IS NOT NULL " +
           "AND c.class_annotation != ''", nativeQuery = true)
    int countAnnotatedCouplesByTaskId(@Param("taskId") Long taskId);
}
