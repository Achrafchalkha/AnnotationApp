package com.annotation.service;

import com.annotation.model.CoupleText;
import org.springframework.data.domain.Page;

import java.util.List;

public interface CoupleTextService {
    CoupleText findCoupleTextById(Long id);
    Page<CoupleText> getCoupleTexts(int page, int size);
    Page<CoupleText> getCoupleTextsByDatasetId(Long datasetId, int page, int size);
    List<CoupleText> findAllCoupleTextsByDatasetId(Long id);
    long countCoupleTextsByDatasetId(Long id);
}