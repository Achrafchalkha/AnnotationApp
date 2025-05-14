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
}
