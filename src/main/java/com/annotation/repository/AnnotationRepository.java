package com.annotation.repository;

import com.annotation.model.Annotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AnnotationRepository extends JpaRepository<Annotation, Long> {
    @Query("SELECT COUNT(a) FROM Annotation a WHERE a.coupleText.dataset.id = :datasetId")
    long countByDatasetId(@Param("datasetId") Long datasetId);
    
    @Query("SELECT COUNT(DISTINCT a.coupleText.id) FROM Annotation a WHERE a.coupleText.dataset.id = :datasetId")
    long countDistinctCoupleTextByDatasetId(@Param("datasetId") Long datasetId);
    
    @Query("SELECT COUNT(DISTINCT a.id) FROM Annotation a WHERE a.coupleText.dataset.id = :datasetId")
    long countTotalAnnotationsByDatasetId(@Param("datasetId") Long datasetId);
    
    @Query("SELECT COUNT(DISTINCT CONCAT(a.user.id, '_', a.coupleText.id)) FROM Annotation a WHERE a.coupleText.dataset.id = :datasetId")
    long countUniqueUserTextPairsByDatasetId(@Param("datasetId") Long datasetId);
}
