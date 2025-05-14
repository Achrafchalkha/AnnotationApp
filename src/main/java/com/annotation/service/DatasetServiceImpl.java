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
            }

            // Generate a unique filename to avoid collisions
            String originalFilename = file.getOriginalFilename();
            String uniqueFilename = UUID.randomUUID() + "_" + originalFilename;

            // Create the complete file path
            Path targetLocation = Paths.get(UPLOAD_DIR, uniqueFilename).toAbsolutePath();

            // Actually save the file to disk
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

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
    public void SaveDataset(Dataset dataset) {
        datasetRepository.save(dataset);
    }

    @Override
    public void deleteDataset(Long id) {
        datasetRepository.deleteById(id);
    }

    @Override
    public long countDatasets() {
        return datasetRepository.count();
    }



}