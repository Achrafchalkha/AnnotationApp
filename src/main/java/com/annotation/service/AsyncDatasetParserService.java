package com.annotation.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.annotation.model.Dataset;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AsyncDatasetParserService {
    private static final Logger logger = LoggerFactory.getLogger(AsyncDatasetParserService.class);
    
    private final ExcelDatasetParser excelDatasetParser;
    
    // Track processing status for datasets
    private final ConcurrentHashMap<Long, ProcessingStatus> processingStatus = new ConcurrentHashMap<>();
    
    @Autowired
    public AsyncDatasetParserService(ExcelDatasetParser excelDatasetParser) {
        this.excelDatasetParser = excelDatasetParser;
    }

    /**
     * Start async processing of a dataset
     */
    @Async("taskExecutor")
    public void parseDatasetAsync(Dataset dataset) {
        if (dataset == null || dataset.getId() == null) {
            logger.error("Invalid dataset provided for async processing");
            return;
        }
        
        Long datasetId = dataset.getId();
        
        // Mark dataset as processing
        processingStatus.put(datasetId, ProcessingStatus.PROCESSING);
        logger.info("Starting async processing of dataset {}: {}", datasetId, dataset.getName());
        
        try {
            // Process the dataset
            int processedRows = excelDatasetParser.parseDataset(dataset);
            
            // Mark as completed
            processingStatus.put(datasetId, ProcessingStatus.COMPLETED);
            logger.info("Completed processing dataset {}: {} - processed {} text pairs", 
                    datasetId, dataset.getName(), processedRows);
        } catch (Exception e) {
            // Mark as failed
            processingStatus.put(datasetId, ProcessingStatus.FAILED);
            logger.error("Failed to process dataset {}: {}", datasetId, e.getMessage(), e);
        }
    }
    
    /**
     * Get the current processing status for a dataset
     */
    public ProcessingStatus getProcessingStatus(Long datasetId) {
        return processingStatus.getOrDefault(datasetId, ProcessingStatus.NOT_STARTED);
    }
    
    /**
     * Enum for tracking processing status
     */
    public enum ProcessingStatus {
        NOT_STARTED,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}