package com.annotation.service;

import com.annotation.model.ClassPossible;
import com.annotation.model.CoupleText;
import com.annotation.model.Dataset;
import com.annotation.repository.ClassPossibleRepository;
import com.annotation.repository.CoupleTextRepository;
import com.annotation.repository.DatasetRepository;
import com.annotation.service.DatasetService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class DatasetServiceImpl implements DatasetService {

    // Changed to a constant path that exists in project structure
    private static final String UPLOAD_DIR = "uploads/datasets";

    @Autowired
    private final DatasetRepository datasetRepository;
    @Autowired
    private final ClassPossibleRepository classPossibleRepository;
    @Autowired
    private CoupleTextRepository coupleTextRepository;
    @Autowired
    private ExcelDatasetParser excelDatasetParser;

    public DatasetServiceImpl(DatasetRepository datasetRepository, ClassPossibleRepository classPossibleRepository) {
        this.datasetRepository = datasetRepository;
        this.classPossibleRepository = classPossibleRepository;
    }

    @Override
    public List<Dataset> findAllDatasets() {
        return datasetRepository.findAll();
    }
    @Override
    public Dataset findDatasetByName(String name) {
        return datasetRepository.findByName(name);
    }
    @Override
    public Dataset findDatasetById(Long id) {
        return datasetRepository.findById(id).orElse(null);
    }


    @Override
    public void ParseDataset(Dataset dataset) {
        // Delegate to the new dedicated parser implementation
        excelDatasetParser.parseDataset(dataset);
    }



    @Override
    @Transactional
    public Dataset createDataset(String name, String description, MultipartFile file, String classesRaw) throws IOException {
        Dataset dataset = new Dataset();
        dataset.setName(name);
        dataset.setDescription(description);

        if (file != null && !file.isEmpty()) {
            // Create upload directory if it doesn't exist
            File uploadDirFile = new File(UPLOAD_DIR);
            if (!uploadDirFile.exists()) {
                uploadDirFile.mkdirs();
                System.out.println("Created directory: " + uploadDirFile.getAbsolutePath());
            } else {
                System.out.println("Using existing directory: " + uploadDirFile.getAbsolutePath());
            }

            // Generate a unique filename to avoid collisions
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            
            String uniqueFilename = UUID.randomUUID() + "_" + 
                                  (originalFilename != null ? originalFilename : "dataset" + extension);
            
            // Create the complete file path
            Path targetLocation = Paths.get(UPLOAD_DIR, uniqueFilename).toAbsolutePath();
            
            System.out.println("Saving file to: " + targetLocation);
            System.out.println("File size: " + file.getSize() + " bytes");
            System.out.println("Content type: " + file.getContentType());

            // Actually save the file to disk with explicit byte handling for better reliability
            try (InputStream inputStream = file.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(targetLocation.toFile())) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytesWritten = 0;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesWritten += bytesRead;
                }
                
                outputStream.flush();
                System.out.println("File written successfully. Total bytes: " + totalBytesWritten);
                
                // Verify file was written correctly
                File writtenFile = targetLocation.toFile();
                if (writtenFile.exists() && writtenFile.length() > 0) {
                    System.out.println("Verified file exists and has size: " + writtenFile.length() + " bytes");
                } else {
                    System.out.println("WARNING: File verification failed. Exists: " + 
                                 writtenFile.exists() + ", Size: " + 
                                 (writtenFile.exists() ? writtenFile.length() : 0));
                }
            }

            // Store the absolute path in the dataset
            dataset.setFilePath(targetLocation.toString());
            dataset.setFileType(file.getContentType());
        }

        // Handle classes
        Set<ClassPossible> classSet = Arrays.stream(classesRaw.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(className -> {
                    ClassPossible cp = new ClassPossible();
                    cp.setTextClass(className);
                    cp.setDataset(dataset);
                    return cp;
                })
                .collect(Collectors.toSet());

        dataset.setClassesPossibles(classSet);

        // Save the dataset to get an ID assigned
        return datasetRepository.save(dataset);
    }


    @Override
    @Transactional
    public void SaveDataset(Dataset dataset) {
        try {
            if (dataset == null) {
                System.out.println("Cannot save null dataset");
                return;
            }
            
            System.out.println("Saving dataset: ID=" + dataset.getId() + ", Name=" + dataset.getName());
            
            // Check if dataset exists in database
            boolean isNew = dataset.getId() == null;
            Dataset saved = datasetRepository.save(dataset);
            
            System.out.println("Dataset " + (isNew ? "created" : "updated") + " with ID: " + saved.getId());
            
            // Verify dataset was saved
            Dataset verified = datasetRepository.findById(saved.getId()).orElse(null);
            if (verified != null) {
                System.out.println("Verified dataset exists in database with ID: " + verified.getId());
            } else {
                System.out.println("WARNING: Failed to verify dataset with ID: " + saved.getId());
            }
        } catch (Exception e) {
            System.out.println("Error saving dataset: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @Override
    @Transactional
    public void deleteDataset(Long id) {
        try {
            Dataset dataset = datasetRepository.findById(id).orElse(null);
            if (dataset == null) {
                System.out.println("Dataset not found with ID: " + id);
                return;
            }
            
            // Explicitly delete related couple texts first
            System.out.println("Deleting related text pairs for dataset ID: " + id);
            coupleTextRepository.deleteByDatasetId(id);
            
            // Now delete the dataset
            System.out.println("Deleting dataset with ID: " + id);
            datasetRepository.deleteById(id);
            
            // Delete the file if it exists
            if (dataset.getFilePath() != null && !dataset.getFilePath().isEmpty()) {
                try {
                    File file = new File(dataset.getFilePath());
                    if (file.exists()) {
                        boolean deleted = file.delete();
                        System.out.println("Dataset file " + (deleted ? "deleted" : "could not be deleted") + 
                                       ": " + dataset.getFilePath());
                    }
                } catch (Exception e) {
                    System.out.println("Error deleting dataset file: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("Error in deleteDataset: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @Override
    public long countDatasets() {
        return datasetRepository.count();
    }



}