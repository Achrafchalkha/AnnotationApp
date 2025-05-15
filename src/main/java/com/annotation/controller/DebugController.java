package com.annotation.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.annotation.model.Dataset;
import com.annotation.model.Task;
import com.annotation.repository.DatasetRepository;
import com.annotation.repository.TaskRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/debug")
public class DebugController {

    @PersistenceContext
    private EntityManager entityManager;

    private final DatasetRepository datasetRepository;
    private final TaskRepository taskRepository;

    public DebugController(DatasetRepository datasetRepository, TaskRepository taskRepository) {
        this.datasetRepository = datasetRepository;
        this.taskRepository = taskRepository;
    }

    @GetMapping("/db-schema")
    @ResponseBody
    public Map<String, Object> getDatabaseSchema() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // List all tables
            Query tablesQuery = entityManager.createNativeQuery(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()");
            List<String> tables = tablesQuery.getResultList();
            result.put("tables", tables);
            
            // Get details for each table
            Map<String, List<Map<String, Object>>> tableDetails = new HashMap<>();
            for (String tableName : tables) {
                // Get columns
                Query columnsQuery = entityManager.createNativeQuery(
                    "SELECT column_name, data_type, is_nullable, column_key " +
                    "FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = :tableName " +
                    "ORDER BY ordinal_position");
                columnsQuery.setParameter("tableName", tableName);
                
                List<Object[]> columns = columnsQuery.getResultList();
                List<Map<String, Object>> columnList = new ArrayList<>();
                for (Object[] column : columns) {
                    Map<String, Object> columnDetails = new HashMap<>();
                    columnDetails.put("name", column[0]);
                    columnDetails.put("type", column[1]);
                    columnDetails.put("isNullable", column[2]);
                    columnDetails.put("key", column[3]);
                    columnList.add(columnDetails);
                }
                
                // Get foreign keys
                Query foreignKeysQuery = entityManager.createNativeQuery(
                    "SELECT constraint_name, column_name, referenced_table_name, referenced_column_name " +
                    "FROM information_schema.key_column_usage " +
                    "WHERE table_schema = DATABASE() AND table_name = :tableName " +
                    "AND referenced_table_name IS NOT NULL");
                foreignKeysQuery.setParameter("tableName", tableName);
                
                List<Object[]> foreignKeys = foreignKeysQuery.getResultList();
                List<Map<String, Object>> fkList = new ArrayList<>();
                for (Object[] fk : foreignKeys) {
                    Map<String, Object> fkDetails = new HashMap<>();
                    fkDetails.put("name", fk[0]);
                    fkDetails.put("column", fk[1]);
                    fkDetails.put("referencedTable", fk[2]);
                    fkDetails.put("referencedColumn", fk[3]);
                    fkList.add(fkDetails);
                }
                
                Map<String, List<Map<String, Object>>> tableStructure = new HashMap<>();
                tableStructure.put("columns", columnList);
                tableStructure.put("foreignKeys", fkList);
                
                tableDetails.put(tableName, new ArrayList<>(Arrays.asList(
                    Map.of("columns", columnList),
                    Map.of("foreignKeys", fkList)
                )));
            }
            result.put("tableDetails", tableDetails);
            result.put("success", true);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
    
    @GetMapping("/fix-tasks/{datasetId}")
    @ResponseBody
    public Map<String, Object> fixTasks(@PathVariable Long datasetId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get the dataset
            Optional<Dataset> datasetOpt = datasetRepository.findById(datasetId);
            if (!datasetOpt.isPresent()) {
                result.put("success", false);
                result.put("error", "Dataset not found: " + datasetId);
                return result;
            }
            
            Dataset dataset = datasetOpt.get();
            result.put("datasetId", dataset.getId());
            result.put("datasetName", dataset.getName());
            
            // Check existing tasks
            List<Task> tasks = taskRepository.findByDatasetId(datasetId);
            result.put("existingTaskCount", tasks.size());
            
            // Find dangling task_couple entries
            Query danglingEntries = entityManager.createNativeQuery(
                "SELECT tc.task_id, tc.couple_id FROM task_couple tc " +
                "LEFT JOIN tasks t ON tc.task_id = t.id " +
                "WHERE t.id IS NULL");
            List<Object[]> danglingResults = danglingEntries.getResultList();
            result.put("danglingTaskCoupleEntries", danglingResults.size());
            
            // Find tasks with null dataset_id
            Query nullDatasetQuery = entityManager.createNativeQuery(
                "SELECT id FROM tasks WHERE dataset_id IS NULL");
            List<Object> nullDatasetTasks = nullDatasetQuery.getResultList();
            result.put("tasksWithNullDataset", nullDatasetTasks.size());
            
            // Find tasks with invalid dataset_id
            Query invalidDatasetQuery = entityManager.createNativeQuery(
                "SELECT t.id FROM tasks t LEFT JOIN datasets d ON t.dataset_id = d.id WHERE t.dataset_id IS NOT NULL AND d.id IS NULL");
            List<Object> invalidDatasetTasks = invalidDatasetQuery.getResultList();
            result.put("tasksWithInvalidDataset", invalidDatasetTasks.size());
            
            // Try to fix any invalid dataset references
            if (!invalidDatasetTasks.isEmpty()) {
                for (Object taskId : invalidDatasetTasks) {
                    Query updateQuery = entityManager.createNativeQuery(
                        "UPDATE tasks SET dataset_id = :datasetId WHERE id = :taskId");
                    updateQuery.setParameter("datasetId", datasetId);
                    updateQuery.setParameter("taskId", taskId);
                    int updated = updateQuery.executeUpdate();
                    result.put("fixedTask_" + taskId, updated > 0);
                }
                result.put("fixAttempted", true);
            }
            
            // Clean up any dangling entries
            if (!danglingResults.isEmpty()) {
                Query deleteQuery = entityManager.createNativeQuery(
                    "DELETE FROM task_couple WHERE task_id IN (" +
                    "SELECT tc.task_id FROM task_couple tc " +
                    "LEFT JOIN tasks t ON tc.task_id = t.id " +
                    "WHERE t.id IS NULL)");
                int deleted = deleteQuery.executeUpdate();
                result.put("danglingEntriesDeleted", deleted);
            }
            
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }

    @GetMapping("/sql-diagnostics")
    @ResponseBody
    public Map<String, Object> runSqlDiagnostics(@RequestParam(required = false) Long datasetId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Check schema information
            Query schemaQuery = entityManager.createNativeQuery(
                "SELECT table_schema, table_name FROM information_schema.tables " +
                "WHERE table_name IN ('tasks', 'dataset')");
            List<Object[]> schemaResults = schemaQuery.getResultList();
            
            List<Map<String, Object>> schemaInfo = new ArrayList<>();
            for (Object[] row : schemaResults) {
                Map<String, Object> tableInfo = new HashMap<>();
                tableInfo.put("schema", row[0]);
                tableInfo.put("table", row[1]);
                schemaInfo.add(tableInfo);
            }
            result.put("schemaInfo", schemaInfo);
            
            // Check all datasets
            Query datasetsQuery = entityManager.createNativeQuery(
                "SELECT id, name, description FROM dataset ORDER BY id");
            List<Object[]> datasetsResults = datasetsQuery.getResultList();
            
            List<Map<String, Object>> datasets = new ArrayList<>();
            for (Object[] row : datasetsResults) {
                Map<String, Object> dataset = new HashMap<>();
                dataset.put("id", row[0]);
                dataset.put("name", row[1]);
                dataset.put("description", row[2]);
                datasets.add(dataset);
            }
            result.put("datasets", datasets);
            
            // If specific dataset ID provided, check it
            if (datasetId != null) {
                Query specificDatasetQuery = entityManager.createNativeQuery(
                    "SELECT id, name, description FROM dataset WHERE id = :id");
                specificDatasetQuery.setParameter("id", datasetId);
                List<Object[]> specificResults = specificDatasetQuery.getResultList();
                
                if (!specificResults.isEmpty()) {
                    Object[] row = specificResults.get(0);
                    Map<String, Object> dataset = new HashMap<>();
                    dataset.put("id", row[0]);
                    dataset.put("name", row[1]);
                    dataset.put("description", row[2]);
                    result.put("specificDataset", dataset);
                    result.put("datasetExists", true);
                } else {
                    result.put("datasetExists", false);
                }
                
                // Check if this dataset is referenced in tasks
                Query taskReferencesQuery = entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM tasks WHERE dataset_id = :datasetId");
                taskReferencesQuery.setParameter("datasetId", datasetId);
                Long taskCount = ((Number) taskReferencesQuery.getSingleResult()).longValue();
                result.put("referencedInTasks", taskCount);
            }
            
            // Check for any uncommitted transactions (simplified)
            try {
                // Force a commit of any pending transactions
                entityManager.createNativeQuery("COMMIT").executeUpdate();
                result.put("committedTransactions", true);
            } catch (Exception e) {
                result.put("commitError", e.getMessage());
            }
            
            // Basic foreign key check
            Query foreignKeyQuery = entityManager.createNativeQuery(
                "SELECT constraint_name, table_name, column_name, referenced_table_name, referenced_column_name " +
                "FROM information_schema.key_column_usage " +
                "WHERE referenced_table_name = 'dataset' AND table_name = 'tasks'");
            List<Object[]> foreignKeys = foreignKeyQuery.getResultList();
            
            List<Map<String, Object>> fkInfo = new ArrayList<>();
            for (Object[] row : foreignKeys) {
                Map<String, Object> fk = new HashMap<>();
                fk.put("constraintName", row[0]);
                fk.put("tableName", row[1]);
                fk.put("columnName", row[2]);
                fk.put("referencedTable", row[3]);
                fk.put("referencedColumn", row[4]);
                fkInfo.add(fk);
            }
            result.put("foreignKeyInfo", fkInfo);
            
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }

    @GetMapping("/change-dataset-id")
    @ResponseBody
    public Map<String, Object> changeDatasetId(
            @RequestParam Long currentId,
            @RequestParam Long newId) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Verify old and new IDs
            Query checkOldQuery = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM dataset WHERE id = :id");
            checkOldQuery.setParameter("id", currentId);
            long oldExists = ((Number) checkOldQuery.getSingleResult()).longValue();
            
            Query checkNewQuery = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM dataset WHERE id = :id");
            checkNewQuery.setParameter("id", newId);
            long newExists = ((Number) checkNewQuery.getSingleResult()).longValue();
            
            result.put("oldIdExists", oldExists > 0);
            result.put("newIdExists", newExists > 0);
            
            if (oldExists == 0) {
                result.put("success", false);
                result.put("error", "Current dataset ID " + currentId + " doesn't exist");
                return result;
            }
            
            if (newExists > 0) {
                result.put("success", false);
                result.put("error", "New dataset ID " + newId + " already exists");
                return result;
            }
            
            // Update the dataset ID
            Query updateQuery = entityManager.createNativeQuery(
                "UPDATE dataset SET id = :newId WHERE id = :currentId");
            updateQuery.setParameter("newId", newId);
            updateQuery.setParameter("currentId", currentId);
            int updated = updateQuery.executeUpdate();
            
            // Update any references in tasks
            Query updateTasksQuery = entityManager.createNativeQuery(
                "UPDATE tasks SET dataset_id = :newId WHERE dataset_id = :currentId");
            updateTasksQuery.setParameter("newId", newId);
            updateTasksQuery.setParameter("currentId", currentId);
            int tasksUpdated = updateTasksQuery.executeUpdate();
            
            // Update any references in couple_text
            Query updateCouplesQuery = entityManager.createNativeQuery(
                "UPDATE couple_text SET dataset_id = :newId WHERE dataset_id = :currentId");
            updateCouplesQuery.setParameter("newId", newId);
            updateCouplesQuery.setParameter("currentId", currentId);
            int couplesUpdated = updateCouplesQuery.executeUpdate();
            
            // Update any references in class_possible
            Query updateClassesQuery = entityManager.createNativeQuery(
                "UPDATE class_possible SET dataset_id = :newId WHERE dataset_id = :currentId");
            updateClassesQuery.setParameter("newId", newId);
            updateClassesQuery.setParameter("currentId", currentId);
            int classesUpdated = updateClassesQuery.executeUpdate();
            
            // Force commit
            entityManager.createNativeQuery("COMMIT").executeUpdate();
            
            result.put("success", true);
            result.put("datasetsUpdated", updated);
            result.put("tasksUpdated", tasksUpdated);
            result.put("couplesUpdated", couplesUpdated);
            result.put("classesUpdated", classesUpdated);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }

    @GetMapping("/fix-task-dataset-ids")
    @ResponseBody
    public Map<String, Object> fixTaskDatasetIds() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. Find tasks with invalid dataset references
            Query findBadTasksQuery = entityManager.createNativeQuery(
                "SELECT t.id, t.dataset_id FROM tasks t " + 
                "LEFT JOIN dataset d ON t.dataset_id = d.id " +
                "WHERE d.id IS NULL");
            List<Object[]> badTasks = findBadTasksQuery.getResultList();
            
            result.put("badTasksCount", badTasks.size());
            
            if (badTasks.isEmpty()) {
                result.put("message", "No tasks with invalid dataset references found");
                result.put("success", true);
                return result;
            }
            
            // 2. Find a valid dataset to use as replacement
            Query findValidDatasetQuery = entityManager.createNativeQuery(
                "SELECT id FROM dataset ORDER BY id LIMIT 1");
            List<?> validDatasets = findValidDatasetQuery.getResultList();
            
            if (validDatasets.isEmpty()) {
                result.put("message", "No valid datasets found to use as replacement");
                result.put("success", false);
                return result;
            }
            
            Long validDatasetId = ((Number) validDatasets.get(0)).longValue();
            result.put("validDatasetId", validDatasetId);
            
            // 3. Update the bad tasks with the valid dataset ID
            List<Long> badTaskIds = new ArrayList<>();
            for (Object[] row : badTasks) {
                badTaskIds.add(((Number) row[0]).longValue());
            }
            
            String taskIdList = badTaskIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
            
            // Update the tasks
            Query updateTasksQuery = entityManager.createNativeQuery(
                "UPDATE tasks SET dataset_id = :validId WHERE id IN (" + taskIdList + ")");
            updateTasksQuery.setParameter("validId", validDatasetId);
            int updatedCount = updateTasksQuery.executeUpdate();
            
            // Force commit
            entityManager.createNativeQuery("COMMIT").executeUpdate();
            
            result.put("tasksFixed", updatedCount);
            result.put("message", "Fixed " + updatedCount + " tasks with invalid dataset references");
            result.put("success", true);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }

    @GetMapping("/create-test-dataset")
    @ResponseBody
    public Map<String, Object> createTestDataset(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Use default values if not provided
            String datasetName = name != null ? name : "Test Dataset";
            String datasetDesc = description != null ? description : "Dataset created for testing";
            
            // Insert directly using native SQL
            Query insertQuery = entityManager.createNativeQuery(
                "INSERT INTO dataset (name, description) VALUES (:name, :description)");
            insertQuery.setParameter("name", datasetName);
            insertQuery.setParameter("description", datasetDesc);
            
            int inserted = insertQuery.executeUpdate();
            
            // Get the ID of the newly created dataset
            Long newId = null;
            try {
                Query idQuery = entityManager.createNativeQuery(
                    "SELECT LAST_INSERT_ID()");
                newId = ((Number) idQuery.getSingleResult()).longValue();
            } catch (Exception e) {
                result.put("idLookupError", e.getMessage());
            }
            
            // Force commit
            try {
                entityManager.createNativeQuery("COMMIT").executeUpdate();
            } catch (Exception e) {
                result.put("commitError", e.getMessage());
            }
            
            // Get all datasets
            Query datasetsQuery = entityManager.createNativeQuery(
                "SELECT id, name, description FROM dataset ORDER BY id");
            List<Object[]> datasets = datasetsQuery.getResultList();
            
            List<Map<String, Object>> datasetsList = new ArrayList<>();
            for (Object[] row : datasets) {
                Map<String, Object> dataset = new HashMap<>();
                dataset.put("id", row[0]);
                dataset.put("name", row[1]);
                dataset.put("description", row[2]);
                datasetsList.add(dataset);
            }
            
            result.put("inserted", inserted);
            result.put("newDatasetId", newId);
            result.put("allDatasets", datasetsList);
            result.put("success", true);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }

    @GetMapping("/create-test-pairs")
    @ResponseBody
    public Map<String, Object> createTestPairs(
            @RequestParam Long datasetId,
            @RequestParam(defaultValue = "5") int count) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // First verify the dataset exists
            Query checkQuery = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM datasets WHERE id = :id");
            checkQuery.setParameter("id", datasetId);
            long exists = ((Number) checkQuery.getSingleResult()).longValue();
            
            if (exists == 0) {
                result.put("success", false);
                result.put("error", "Dataset with ID " + datasetId + " doesn't exist");
                return result;
            }
            
            // Create sample text pairs
            int created = 0;
            for (int i = 1; i <= count; i++) {
                String text1 = "Sample text 1 for pair " + i;
                String text2 = "Sample text 2 for pair " + i;
                
                Query insertQuery = entityManager.createNativeQuery(
                    "INSERT INTO couple_text (text1, text2, dataset_id) VALUES (:text1, :text2, :datasetId)");
                insertQuery.setParameter("text1", text1);
                insertQuery.setParameter("text2", text2);
                insertQuery.setParameter("datasetId", datasetId);
                
                created += insertQuery.executeUpdate();
            }
            
            // Force commit
            try {
                entityManager.createNativeQuery("COMMIT").executeUpdate();
            } catch (Exception e) {
                result.put("commitError", e.getMessage());
            }
            
            // Count text pairs
            Query countQuery = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM couple_text WHERE dataset_id = :datasetId");
            countQuery.setParameter("datasetId", datasetId);
            long totalPairs = ((Number) countQuery.getSingleResult()).longValue();
            
            result.put("createdPairs", created);
            result.put("totalPairs", totalPairs);
            result.put("success", true);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }

    @GetMapping("/all-tables")
    @ResponseBody
    public Map<String, Object> listAllTables() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get all tables in the database
            Query tablesQuery = entityManager.createNativeQuery(
                "SHOW TABLES");
            List<String> tables = tablesQuery.getResultList();
            result.put("allTables", tables);
            
            // Check specifically for 'dataset' and 'datasets' tables
            boolean datasetExists = tables.contains("dataset");
            boolean datasetsExists = tables.contains("datasets");
            
            result.put("datasetExists", datasetExists);
            result.put("datasetsExists", datasetsExists);
            
            // Get data from 'dataset' table if it exists
            if (datasetExists) {
                try {
                    Query datasetQuery = entityManager.createNativeQuery(
                        "SELECT id, name, description FROM dataset ORDER BY id");
                    List<Object[]> datasets = datasetQuery.getResultList();
                    
                    List<Map<String, Object>> datasetsList = new ArrayList<>();
                    for (Object[] row : datasets) {
                        Map<String, Object> dataset = new HashMap<>();
                        dataset.put("id", row[0]);
                        dataset.put("name", row[1]);
                        dataset.put("description", row[2]);
                        datasetsList.add(dataset);
                    }
                    result.put("datasetTableContents", datasetsList);
                    result.put("datasetTableCount", datasets.size());
                } catch (Exception e) {
                    result.put("datasetTableError", e.getMessage());
                }
            }
            
            // Get data from 'datasets' table if it exists
            if (datasetsExists) {
                try {
                    Query datasetsQuery = entityManager.createNativeQuery(
                        "SELECT id, name, description FROM datasets ORDER BY id");
                    List<Object[]> datasets = datasetsQuery.getResultList();
                    
                    List<Map<String, Object>> datasetsList = new ArrayList<>();
                    for (Object[] row : datasets) {
                        Map<String, Object> dataset = new HashMap<>();
                        dataset.put("id", row[0]);
                        dataset.put("name", row[1]);
                        dataset.put("description", row[2]);
                        datasetsList.add(dataset);
                    }
                    result.put("datasetsTableContents", datasetsList);
                    result.put("datasetsTableCount", datasets.size());
                } catch (Exception e) {
                    result.put("datasetsTableError", e.getMessage());
                }
            }
            
            // Check which table is referenced by foreign keys
            Query foreignKeyQuery = entityManager.createNativeQuery(
                "SELECT constraint_name, table_name, column_name, " +
                "referenced_table_name, referenced_column_name " +
                "FROM information_schema.key_column_usage " +
                "WHERE referenced_table_name IN ('dataset', 'datasets') " +
                "AND table_name = 'tasks'");
            List<Object[]> foreignKeys = foreignKeyQuery.getResultList();
            
            List<Map<String, Object>> fkInfo = new ArrayList<>();
            for (Object[] row : foreignKeys) {
                Map<String, Object> fk = new HashMap<>();
                fk.put("constraintName", row[0]);
                fk.put("tableName", row[1]);
                fk.put("columnName", row[2]);
                fk.put("referencedTable", row[3]);
                fk.put("referencedColumn", row[4]);
                fkInfo.add(fk);
            }
            result.put("foreignKeyInfo", fkInfo);
            
            // Find all columns in tasks table
            Query tasksColumnsQuery = entityManager.createNativeQuery(
                "SHOW COLUMNS FROM tasks");
            List<Object[]> tasksColumns = tasksColumnsQuery.getResultList();
            
            List<Map<String, Object>> columnsInfo = new ArrayList<>();
            for (Object[] row : tasksColumns) {
                Map<String, Object> column = new HashMap<>();
                column.put("field", row[0]);
                column.put("type", row[1]);
                column.put("null", row[2]);
                column.put("key", row[3]);
                column.put("default", row[4]);
                column.put("extra", row[5]);
                columnsInfo.add(column);
            }
            result.put("tasksColumns", columnsInfo);
            
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }

    @GetMapping("/fix-dataset-reference")
    @ResponseBody
    public Map<String, Object> fixDatasetReference() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // First check if we have both tables
            Query tablesQuery = entityManager.createNativeQuery(
                "SHOW TABLES LIKE 'dataset%'");
            List<String> matchingTables = tablesQuery.getResultList();
            result.put("matchingTables", matchingTables);
            
            boolean hasSingularTable = matchingTables.contains("dataset");
            boolean hasPluralTable = matchingTables.contains("datasets");
            
            result.put("hasSingularTable", hasSingularTable);
            result.put("hasPluralTable", hasPluralTable);
            
            if (!hasSingularTable && !hasPluralTable) {
                result.put("success", false);
                result.put("error", "Neither dataset nor datasets table exists");
                return result;
            }
            
            // Find which table has data
            long singularCount = 0;
            long pluralCount = 0;
            
            if (hasSingularTable) {
                Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM dataset");
                singularCount = ((Number) countQuery.getSingleResult()).longValue();
                result.put("singularTableCount", singularCount);
            }
            
            if (hasPluralTable) {
                Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM datasets");
                pluralCount = ((Number) countQuery.getSingleResult()).longValue();
                result.put("pluralTableCount", pluralCount);
            }
            
            // Check foreign key references
            Query fkQuery = entityManager.createNativeQuery(
                "SELECT constraint_name, referenced_table_name FROM information_schema.key_column_usage " +
                "WHERE table_name = 'tasks' AND column_name = 'dataset_id'");
            List<Object[]> foreignKeys = fkQuery.getResultList();
            
            // Extract constraint info
            String constraintName = null;
            String referencedTable = null;
            
            if (!foreignKeys.isEmpty()) {
                Object[] fk = foreignKeys.get(0);
                constraintName = (String) fk[0];
                referencedTable = (String) fk[1];
                
                result.put("constraintName", constraintName);
                result.put("referencedTable", referencedTable);
            }
            
            // Determine which table should be used based on data
            String preferredTable = null;
            if (singularCount > 0 && pluralCount == 0) {
                preferredTable = "dataset";
            } else if (pluralCount > 0 && singularCount == 0) {
                preferredTable = "datasets";
            } else if (singularCount > 0 && pluralCount > 0) {
                // Both have data, prefer the one with more records
                preferredTable = (singularCount >= pluralCount) ? "dataset" : "datasets";
            } else {
                // Neither has data, default to the one referenced by FK
                preferredTable = referencedTable;
            }
            
            result.put("preferredTable", preferredTable);
            
            // If foreign key doesn't match preferred table, update it
            boolean needsFkUpdate = referencedTable != null && !referencedTable.equals(preferredTable);
            result.put("needsFkUpdate", needsFkUpdate);
            
            if (needsFkUpdate && constraintName != null) {
                // We need to update the foreign key to point to the correct table
                try {
                    // First drop the existing constraint
                    Query dropFkQuery = entityManager.createNativeQuery(
                        "ALTER TABLE tasks DROP FOREIGN KEY " + constraintName);
                    dropFkQuery.executeUpdate();
                    
                    // Then create a new one pointing to the preferred table
                    Query addFkQuery = entityManager.createNativeQuery(
                        "ALTER TABLE tasks ADD CONSTRAINT tasks_dataset_fk " +
                        "FOREIGN KEY (dataset_id) REFERENCES " + preferredTable + "(id)");
                    addFkQuery.executeUpdate();
                    
                    // Force commit
                    entityManager.createNativeQuery("COMMIT").executeUpdate();
                    
                    result.put("foreignKeyUpdated", true);
                    result.put("newConstraintName", "tasks_dataset_fk");
                } catch (Exception e) {
                    result.put("foreignKeyUpdateError", e.getMessage());
                }
            }
            
            // Copy data between tables if needed
            if (preferredTable.equals("dataset") && hasPluralTable && pluralCount > 0) {
                // Copy from datasets to dataset
                try {
                    Query copyQuery = entityManager.createNativeQuery(
                        "INSERT INTO dataset (id, name, description, file_path, file_type) " +
                        "SELECT id, name, description, file_path, file_type FROM datasets " +
                        "ON DUPLICATE KEY UPDATE name = VALUES(name), " +
                        "description = VALUES(description), " +
                        "file_path = VALUES(file_path), " +
                        "file_type = VALUES(file_type)");
                    int copied = copyQuery.executeUpdate();
                    result.put("copiedToDatset", copied);
                } catch (Exception e) {
                    result.put("copyError", e.getMessage());
                }
            } else if (preferredTable.equals("datasets") && hasSingularTable && singularCount > 0) {
                // Copy from dataset to datasets
                try {
                    Query copyQuery = entityManager.createNativeQuery(
                        "INSERT INTO datasets (id, name, description, file_path, file_type) " +
                        "SELECT id, name, description, file_path, file_type FROM dataset " +
                        "ON DUPLICATE KEY UPDATE name = VALUES(name), " +
                        "description = VALUES(description), " +
                        "file_path = VALUES(file_path), " +
                        "file_type = VALUES(file_type)");
                    int copied = copyQuery.executeUpdate();
                    result.put("copiedToDatasets", copied);
                } catch (Exception e) {
                    result.put("copyError", e.getMessage());
                }
            }
            
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
} 