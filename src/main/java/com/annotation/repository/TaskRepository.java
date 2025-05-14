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
    
    @Query("SELECT t FROM Task t JOIN FETCH t.couples WHERE t.id = :taskId")
    Task findByIdWithCouples(@Param("taskId") Long taskId);
}
