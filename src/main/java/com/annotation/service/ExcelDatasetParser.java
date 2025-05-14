package com.annotation.service;

import com.annotation.model.CoupleText;
import com.annotation.model.Dataset;
import com.annotation.repository.CoupleTextRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Component
public class ExcelDatasetParser {
    private static final Logger logger = LoggerFactory.getLogger(ExcelDatasetParser.class);
    private static final int BATCH_SIZE = 50;
    private static final int DEFAULT_MAX_ROWS = 1000; // Default value, can be overridden
    
    private final CoupleTextRepository coupleTextRepository;
    
    public ExcelDatasetParser(CoupleTextRepository coupleTextRepository) {
        this.coupleTextRepository = coupleTextRepository;
    }
    
    /**
     * Parse a dataset file and extract text pairs
     * @param dataset The dataset object containing file path information
     * @param maxRows Maximum number of rows to process (0 for all rows)
     * @return The number of text pairs successfully processed
     */
    @Transactional
    public int parseDataset(Dataset dataset, int maxRows) {
        int processedRows = 0;
        String filePath = dataset.getFilePath();
        
        System.out.println("=========== START DATASET PARSING ===========");
        System.out.println("Dataset ID: " + dataset.getId() + ", Name: " + dataset.getName());
        System.out.println("File path: " + filePath);
        
        if (filePath == null || filePath.isEmpty()) {
            System.out.println("ERROR: Dataset has no associated file path");
            logger.error("Dataset has no associated file path");
            throw new IllegalArgumentException("Dataset has no file path");
        }
        
        // Resolve file path
        Path resolvedPath = resolveFilePath(filePath);
        if (resolvedPath == null) {
            System.out.println("ERROR: Could not resolve file path: " + filePath);
            logger.error("Could not resolve file path: {}", filePath);
            throw new IllegalArgumentException("File not found: " + filePath);
        }
        
        System.out.println("Resolved file path: " + resolvedPath);
        
        // Check file extension to determine processing method
        String fileName = resolvedPath.getFileName().toString().toLowerCase();
        boolean isExcel = fileName.endsWith(".xlsx") || fileName.endsWith(".xls");
        boolean isCsv = fileName.endsWith(".csv") || fileName.endsWith(".tsv") || fileName.endsWith(".txt");
        
        try {
            if (isExcel) {
                System.out.println("Processing as Excel file");
                processedRows = processExcelFile(resolvedPath, dataset, maxRows);
            } else if (isCsv) {
                System.out.println("Processing as CSV/TSV file");
                processedRows = processCsvFile(resolvedPath, dataset, maxRows);
            } else {
                System.out.println("Unknown file type, trying as Excel file");
                processedRows = processExcelFile(resolvedPath, dataset, maxRows);
            }
            
            System.out.println("=========== FINISH DATASET PARSING ===========");
            System.out.println("Processed a total of " + processedRows + " text pairs for dataset " + dataset.getId());
            
            return processedRows;
            
        } catch (IOException e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            logger.error("Error reading file: {}", e.getMessage(), e);
            throw new RuntimeException("Error reading file", e);
        } catch (Exception e) {
            System.out.println("UNEXPECTED ERROR: " + e.getMessage());
            e.printStackTrace();
            logger.error("Unexpected error: {}", e.getMessage(), e);
            throw new RuntimeException("Unexpected error processing dataset", e);
        }
    }
    
    /**
     * Process an Excel file
     */
    private int processExcelFile(Path filePath, Dataset dataset, int maxRows) throws IOException {
        try (InputStream fileInputStream = new FileInputStream(filePath.toFile());
             Workbook workbook = WorkbookFactory.create(fileInputStream)) {
            
            System.out.println("Successfully opened Excel workbook");
            
            // Process each sheet in the workbook
            int numberOfSheets = workbook.getNumberOfSheets();
            System.out.println("Found " + numberOfSheets + " sheets in workbook");
            int totalProcessedRows = 0;
            
            for (int sheetIndex = 0; sheetIndex < numberOfSheets; sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                System.out.println("Processing sheet " + sheetIndex + ": " + sheet.getSheetName());
                
                // Skip empty sheets
                if (sheet.getPhysicalNumberOfRows() <= 1) {
                    System.out.println("Sheet " + sheetIndex + " is empty or only has headers, skipping");
                    continue;
                }
                
                // Process rows in this sheet
                int sheetRows = processSheet(sheet, dataset, maxRows > 0 ? maxRows : DEFAULT_MAX_ROWS);
                totalProcessedRows += sheetRows;
                System.out.println("Processed " + sheetRows + " rows from sheet " + sheetIndex);
                
                // If we've processed enough rows, stop
                if (maxRows > 0 && totalProcessedRows >= maxRows) {
                    break;
                }
            }
            
            return totalProcessedRows;
        }
    }
    
    /**
     * Process a CSV/TSV file
     */
    private int processCsvFile(Path filePath, Dataset dataset, int maxRows) throws IOException {
        List<CoupleText> textPairsBatch = new ArrayList<>(BATCH_SIZE);
        int processedRows = 0;
        int errorCount = 0;
        
        // Determine the delimiter based on file extension or content
        String delimiter = "\t"; // Default to tab for TSV
        String fileName = filePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".csv")) {
            delimiter = ","; // Use comma for CSV
        }
        
        System.out.println("Using delimiter: '" + delimiter + "'");
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            String line;
            int lineNum = 0;
            boolean hasHeader = false;
            int text1ColIndex = 1; // Default positions (0-based index)
            int text2ColIndex = 2;
            
            // Read the first line to check for headers
            line = reader.readLine();
            if (line != null) {
                lineNum++;
                String[] headers = line.split(delimiter, -1);
                
                // Check if this line contains headers
                for (int i = 0; i < headers.length; i++) {
                    String header = headers[i].trim().toLowerCase();
                    System.out.println("  Column " + i + ": '" + header + "'");
                    
                    if (header.contains("sentence1") || header.contains("text1")) {
                        text1ColIndex = i;
                        hasHeader = true;
                        System.out.println("Found text1 column at index " + i);
                    } else if (header.contains("sentence2") || header.contains("text2")) {
                        text2ColIndex = i;
                        hasHeader = true;
                        System.out.println("Found text2 column at index " + i);
                    } else if (header.equals("gold_label") || header.equals("label") || 
                               header.equals("class") || header.equals("promptid") || 
                               header.equals("pairid") || header.equals("genre")) {
                        hasHeader = true;
                    }
                }
                
                System.out.println("File " + (hasHeader ? "has" : "does not have") + " headers");
                
                // If the first line isn't a header, process it
                if (!hasHeader) {
                    processTextPair(line, delimiter, 0, text1ColIndex, text2ColIndex, dataset, textPairsBatch);
                    processedRows++;
                }
            }
            
            // Process remaining lines
            while ((line = reader.readLine()) != null && (maxRows <= 0 || processedRows < maxRows)) {
                lineNum++;
                try {
                    processTextPair(line, delimiter, lineNum, text1ColIndex, text2ColIndex, dataset, textPairsBatch);
                    processedRows++;
                    
                    // Log progress periodically
                    if (processedRows % 100 == 0) {
                        System.out.println("Processed " + processedRows + " lines so far");
                    }
                    
                    // Save in batches for better performance
                    if (textPairsBatch.size() >= BATCH_SIZE) {
                        System.out.println("Saving batch of " + textPairsBatch.size() + " text pairs");
                        coupleTextRepository.saveAll(textPairsBatch);
                        textPairsBatch.clear();
                    }
                } catch (Exception e) {
                    errorCount++;
                    System.out.println("Error processing line " + lineNum + ": " + e.getMessage());
                    if (errorCount < 5) {
                        e.printStackTrace();
                    }
                }
            }
            
            // Save any remaining text pairs
            if (!textPairsBatch.isEmpty()) {
                System.out.println("Saving final batch of " + textPairsBatch.size() + " text pairs");
                coupleTextRepository.saveAll(textPairsBatch);
            }
            
            System.out.println("Processed " + processedRows + " lines successfully, encountered " + 
                         errorCount + " errors");
            return processedRows;
        }
    }
    
    /**
     * Process a line from a CSV/TSV file into a text pair
     */
    private void processTextPair(String line, String delimiter, int lineNum, 
                                int text1ColIndex, int text2ColIndex, 
                                Dataset dataset, List<CoupleText> textPairsBatch) {
        if (line == null || line.trim().isEmpty()) {
            System.out.println("Empty line at " + lineNum + ", skipping");
            return;
        }
        
        String[] columns = line.split(delimiter, -1);
        
        // Check if we have enough columns
        if (columns.length <= Math.max(text1ColIndex, text2ColIndex)) {
            System.out.println("Line " + lineNum + " has only " + columns.length + 
                         " columns, need at least " + (Math.max(text1ColIndex, text2ColIndex) + 1));
            return;
        }
        
        String text1 = columns[text1ColIndex].trim();
        String text2 = columns[text2ColIndex].trim();
        
        // Skip if either text is empty
        if (text1.isEmpty() || text2.isEmpty()) {
            System.out.println("Line " + lineNum + " has empty text in column " + 
                     (text1.isEmpty() ? text1ColIndex : text2ColIndex));
            return;
        }
        
        // Create and add the text pair
        CoupleText textPair = new CoupleText();
        textPair.setText1(text1);
        textPair.setText2(text2);
        textPair.setDataset(dataset);
        
        textPairsBatch.add(textPair);
    }
    
    /**
     * Process a single sheet of the Excel workbook
     */
    private int processSheet(Sheet sheet, Dataset dataset, int maxRows) {
        int processedRows = 0;
        int totalRows = sheet.getLastRowNum();
        
        System.out.println("Sheet has " + (totalRows + 1) + " rows (including potential header)");
        
        // Determine if we have headers
        Row firstRow = sheet.getRow(0);
        boolean hasHeader = hasHeader(firstRow);
        System.out.println("Sheet " + (hasHeader ? "has" : "does not have") + " headers");
        
        // Start from the first row (after header if it exists)
        int startRow = hasHeader ? 1 : 0;
        System.out.println("Starting processing from row " + startRow);
        
        // Store column indices for sentence1 and sentence2 if we have headers
        int text1ColIndex = -1;
        int text2ColIndex = -1;
        
        if (hasHeader) {
            // Try to find the text columns by name
            for (int i = 0; i < firstRow.getLastCellNum(); i++) {
                Cell cell = firstRow.getCell(i);
                if (cell != null && cell.getCellType() == CellType.STRING) {
                    String header = cell.getStringCellValue().trim().toLowerCase();
                    if (header.contains("sentence1") || header.contains("text1") || 
                        header.equals("sentence 1") || header.equals("text 1")) {
                        text1ColIndex = i;
                        System.out.println("Found text1 column at index " + i + " with header '" + header + "'");
                    } else if (header.contains("sentence2") || header.contains("text2") || 
                               header.equals("sentence 2") || header.equals("text 2")) {
                        text2ColIndex = i;
                        System.out.println("Found text2 column at index " + i + " with header '" + header + "'");
                    }
                }
            }
        }
        
        // If columns not found, use defaults
        if (text1ColIndex == -1) {
            text1ColIndex = 1; // Default position for sentence1
            System.out.println("Using default position " + text1ColIndex + " for text1");
        }
        if (text2ColIndex == -1) {
            text2ColIndex = 2; // Default position for sentence2
            System.out.println("Using default position " + text2ColIndex + " for text2");
        }
        
        List<CoupleText> textPairsBatch = new ArrayList<>(BATCH_SIZE);
        int errorCount = 0;
        int processedCount = 0;
        
        for (int rowIdx = startRow; rowIdx <= totalRows; rowIdx++) {
            // Check if we've reached the maximum number of rows
            if (maxRows > 0 && processedRows >= maxRows) {
                System.out.println("Reached maximum row limit of " + maxRows);
                break;
            }
            
            Row row = sheet.getRow(rowIdx);
            if (row == null) {
                System.out.println("Row " + rowIdx + " is null, skipping");
                continue;
            }
            
            try {
                // Override default column positions for this row
                if (hasHeader) {
                    row.setRowNum(rowIdx); // Ensure row number is set correctly
                }
                
                // Extract using the determined column indices
                Cell text1Cell = row.getCell(text1ColIndex);
                Cell text2Cell = row.getCell(text2ColIndex);
                
                if (text1Cell == null || text2Cell == null) {
                    System.out.println("Row " + rowIdx + ": Missing text cells at indices " + 
                                 text1ColIndex + " or " + text2ColIndex);
                    continue;
                }
                
                String text1 = getCellValueAsString(text1Cell);
                String text2 = getCellValueAsString(text2Cell);
                
                if (text1.isEmpty() || text2.isEmpty()) {
                    System.out.println("Row " + rowIdx + ": Empty text found");
                    continue;
                }
                
                // Create text pair
                CoupleText textPair = new CoupleText();
                textPair.setText1(text1);
                textPair.setText2(text2);
                textPair.setDataset(dataset);
                
                textPairsBatch.add(textPair);
                processedRows++;
                processedCount++;
                
                // Log progress periodically
                if (processedCount % 10 == 0) {
                    System.out.println("Processed " + processedCount + " rows so far");
                }
                
                // Save in batches for better performance
                if (textPairsBatch.size() >= BATCH_SIZE) {
                    System.out.println("Saving batch of " + textPairsBatch.size() + " text pairs");
                    coupleTextRepository.saveAll(textPairsBatch);
                    textPairsBatch.clear();
                }
            } catch (Exception e) {
                errorCount++;
                System.out.println("Error processing row " + rowIdx + ": " + e.getMessage());
                if (errorCount < 5) {
                    e.printStackTrace(); // Print stack trace for the first few errors
                }
                // Continue processing other rows
            }
        }
        
        // Save any remaining text pairs
        if (!textPairsBatch.isEmpty()) {
            System.out.println("Saving final batch of " + textPairsBatch.size() + " text pairs");
            coupleTextRepository.saveAll(textPairsBatch);
        }
        
        System.out.println("Processed " + processedRows + " rows successfully, encountered " + 
                     errorCount + " errors");
        return processedRows;
    }
    
    /**
     * Extract a text pair from a row
     */
    private CoupleText extractTextPair(Row row, Dataset dataset) {
        try {
            // First, determine which columns contain our text data
            // Try to find text data in columns with specific headers or by position
            
            // If columns are not found by name, try default positions
            int text1ColIndex = findColumnIndex(row, "sentence1", 1);
            int text2ColIndex = findColumnIndex(row, "sentence2", 2);
            
            System.out.println("Extracting from row " + row.getRowNum() + 
                      ", text1 column: " + text1ColIndex + 
                      ", text2 column: " + text2ColIndex);
            
            // Get text cells using the determined indices
            Cell text1Cell = row.getCell(text1ColIndex);
            Cell text2Cell = row.getCell(text2ColIndex);
            
            // Skip if either cell is missing
            if (text1Cell == null || text2Cell == null) {
                System.out.println("Skipping row " + row.getRowNum() + ": one or both text cells are null");
                return null;
            }
            
            String text1 = getCellValueAsString(text1Cell);
            String text2 = getCellValueAsString(text2Cell);
            
            System.out.println("Row " + row.getRowNum() + " - Text1: " + (text1.length() > 50 ? text1.substring(0, 50) + "..." : text1));
            System.out.println("Row " + row.getRowNum() + " - Text2: " + (text2.length() > 50 ? text2.substring(0, 50) + "..." : text2));
            
            // Skip if either text is empty after trimming
            if (text1.isEmpty() || text2.isEmpty()) {
                System.out.println("Skipping row " + row.getRowNum() + ": one or both texts are empty");
                return null;
            }
            
            // Create and return the text pair
            CoupleText textPair = new CoupleText();
            textPair.setText1(text1);
            textPair.setText2(text2);
            textPair.setDataset(dataset);
            
            return textPair;
        } catch (Exception e) {
            System.out.println("Error extracting text pair from row " + row.getRowNum() + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Find index of a column by name or return default index if not found
     */
    private int findColumnIndex(Row row, String columnName, int defaultIndex) {
        // If this is the first row, try to find the column by name
        if (row.getRowNum() == 0) {
            for (int i = 0; i < row.getLastCellNum(); i++) {
                Cell cell = row.getCell(i);
                if (cell != null && 
                    cell.getCellType() == CellType.STRING && 
                    cell.getStringCellValue().trim().equalsIgnoreCase(columnName)) {
                    System.out.println("Found column '" + columnName + "' at index " + i);
                    return i;
                }
            }
        }
        
        // For subsequent rows or if column not found, use default index
        return defaultIndex;
    }
    
    /**
     * Get cell value as string, regardless of cell type
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // Convert numeric to string without scientific notation
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue().trim();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception ex) {
                        return cell.getCellFormula().trim();
                    }
                }
            default:
                return "";
        }
    }
    
    /**
     * Determine if the first row is a header row (heuristic)
     */
    private boolean hasHeader(Row firstRow) {
        if (firstRow == null) {
            return false;
        }
        
        System.out.println("Checking if first row is a header row");
        
        // Check every cell in the first row
        for (int i = 0; i < firstRow.getLastCellNum(); i++) {
            Cell cell = firstRow.getCell(i);
            if (cell != null && cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue().trim().toLowerCase();
                System.out.println("  Column " + i + ": '" + value + "'");
                
                // Common header names for text pairs and related data
                String[] possibleHeaders = {
                    "text1", "text2", "text 1", "text 2", 
                    "sentence1", "sentence2", "sentence 1", "sentence 2",
                    "question", "answer", "input", "output",
                    "source", "target", "premise", "hypothesis",
                    "gold_label", "label", "class", "category",
                    "prompt", "promptid", "id", "pair", "pairid", "genre"
                };
                
                for (String header : possibleHeaders) {
                    if (value.contains(header)) {
                        System.out.println("Detected header: '" + value + "' matches pattern '" + header + "'");
                        return true;
                    }
                }
            }
        }
        
        System.out.println("No headers detected in first row");
        return false;
    }
    
    /**
     * Resolve file path with multiple fallback options
     */
    private Path resolveFilePath(String filePath) {
        // Try the path as-is
        Path path = Paths.get(filePath);
        if (Files.exists(path)) {
            return path;
        }
        
        // Try as a file object
        File file = new File(filePath);
        if (file.exists()) {
            return file.toPath();
        }
        
        // Try with user.dir
        Path relativePath = Paths.get(System.getProperty("user.dir"), filePath);
        if (Files.exists(relativePath)) {
            return relativePath;
        }
        
        // Try removing any 'file:/' prefix
        if (filePath.startsWith("file:/")) {
            String cleanPath = filePath.substring(6);
            Path cleanedPath = Paths.get(cleanPath);
            if (Files.exists(cleanedPath)) {
                return cleanedPath;
            }
        }
        
        logger.error("Could not resolve file path after multiple attempts: {}", filePath);
        return null;
    }
    
    /**
     * Convenience method with default max rows
     */
    @Transactional
    public int parseDataset(Dataset dataset) {
        return parseDataset(dataset, DEFAULT_MAX_ROWS);
    }
} 