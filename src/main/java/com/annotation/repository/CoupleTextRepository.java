package com.annotation.repository;

import com.annotation.model.CoupleText;
import com.annotation.model.Dataset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

@Repository
public interface CoupleTextRepository extends JpaRepository<CoupleText, Long> {
    List<CoupleText> findByDataset(Dataset dataset);
    Page<CoupleText> findByDataset(Dataset dataset, Pageable pageable);
    long countByDatasetId(Long datasetId);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM CoupleText c WHERE c.dataset.id = :datasetId")
    void deleteByDatasetId(@Param("datasetId") Long datasetId);

    /**
     * Find all annotated text pairs for a specific task without duplicates
     */
    @Query(value = "SELECT DISTINCT c.* FROM task_couple tc " +
           "JOIN couple_text c ON tc.couple_id = c.id " +
           "WHERE tc.task_id = :taskId " +
           "AND c.class_annotation IS NOT NULL " +
           "AND c.class_annotation != '' " +
           "LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<CoupleText> findAnnotatedPairsByTaskId(@Param("taskId") Long taskId, 
                                               @Param("limit") int limit, 
                                               @Param("offset") int offset);
}
