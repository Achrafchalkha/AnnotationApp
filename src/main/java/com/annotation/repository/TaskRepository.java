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
}
