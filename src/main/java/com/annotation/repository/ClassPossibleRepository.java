package com.annotation.repository;

import com.annotation.model.ClassPossible;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClassPossibleRepository extends JpaRepository<ClassPossible, Long> {
    List<ClassPossible> findByDatasetId(Long datasetId);
}
