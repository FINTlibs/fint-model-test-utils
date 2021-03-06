package no.fint.model.test.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.Sets;
import io.github.benas.randombeans.api.EnhancedRandom;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class JsonSnapshot {
    private static final String SNAPSHOT_FOLDER = "src/test/resources/snapshots";

    private String snapshotFolder;

    @Getter(AccessLevel.PACKAGE)
    private Class<?> modelClass;
    @Getter(AccessLevel.PACKAGE)
    private File snapshotFile;
    @Getter(AccessLevel.PACKAGE)
    private File relationNamesFile;

    private ObjectMapper objectMapper;

    public JsonSnapshot(Class<?> modelClass) {
        this(modelClass, SNAPSHOT_FOLDER);
    }

    public JsonSnapshot(Class<?> modelClass, String snapshotFolder) {
        this.snapshotFolder = snapshotFolder;
        this.modelClass = modelClass;

        String fileName = String.format("%s.json", modelClass.getName());
        snapshotFile = new File(String.format("%s/%s", snapshotFolder, fileName));

        String relationsFileName = String.format("%s-relation-names.json", modelClass.getName());
        relationNamesFile = new File(String.format("%s/%s", snapshotFolder, relationsFileName));

        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    private void createSnapshotFolder() {
        File folder = new File(snapshotFolder);
        if (!folder.isDirectory() && folder.mkdir()) {
            log.info("Snapshot folder created ({})", snapshotFolder);
        }
    }

    public boolean snapshotFolderExists() {
        return new File(snapshotFolder).isDirectory();
    }

    public boolean exists() {
        return snapshotFile.isFile();
    }

    public void cleanSnapshotFolder() {
        if (snapshotFolderExists()) {
            try {
                FileUtils.cleanDirectory(new File(snapshotFolder));
            } catch (IOException e) {
                throw new IllegalStateException("Exception when trying to clean snapshot folder", e);
            }
        }
    }

    public boolean create() {
        createSnapshotFolder();
        createSnapshotJson();
        createRelationsJson();
        return true;
    }

    private void createSnapshotJson() {
        try {
            Object random = EnhancedRandom.random(modelClass);
            objectMapper.writeValue(snapshotFile, random);
        } catch (IOException e) {
            String msg = String.format("Exception when trying to write snapshot json file (%s)", snapshotFile.getName());
            throw new IllegalStateException(msg, e);
        }
    }

    private void createRelationsJson() {
        try {
            String[] relationNames = getRelationNames();
            objectMapper.writeValue(relationNamesFile, relationNames);
        } catch (IOException e) {
            String msg = String.format("Exception when trying to write relation names json file (%s)", snapshotFile.getName());
            throw new IllegalStateException(msg, e);
        }
    }

    private String[] getRelationNames() {
        Class<?>[] declaredClasses = modelClass.getDeclaredClasses();
        if (declaredClasses.length == 1) {
            Field[] declaredFields = declaredClasses[0].getDeclaredFields();
            return Arrays.stream(declaredFields).map(Field::getName).filter(name -> !name.equals("$VALUES")).toArray(String[]::new);
        } else {
            return new String[]{};
        }
    }

    public boolean matchesSnapshot() {
        try {
            Object object = objectMapper.readValue(snapshotFile, modelClass);
            return matchesProperties(object);
        } catch (IOException e) {
            log.error("Test failed.\n - Class: {}\n - Exception when trying to read snapshot json file.\n - {}", modelClass.getName(), e.getMessage());
            return false;
        }
    }

    private boolean matchesProperties(Object object) {
        try {
            Map<String, String> describe = BeanUtils.describe(object);
            List<Map.Entry<String, String>> nullEntries = describe.entrySet().stream().filter(entry -> entry.getValue() == null).collect(Collectors.toList());
            if (nullEntries.size() > 0) {
                List<String> nullKeys = nullEntries.stream().map(Map.Entry::getKey).collect(Collectors.toList());
                log.error("Test failed.\n - Class: {}\n - Property value is null: {}.", modelClass.getName(), nullKeys);
                return false;
            } else {
                return true;
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            log.error("Test failed.\n - Class: {}\n - Exception when trying to read property values.\n - {}", modelClass.getName(), e.getMessage());
            return false;
        }
    }

    public boolean matchesRelationNames() {
        try {
            Set<String> modelClassRelationNames = new HashSet<>(Arrays.asList(getRelationNames()));
            Set<String> snapshotRelationNames = new HashSet<>(Arrays.asList(objectMapper.readValue(relationNamesFile, String[].class)));

            Sets.SetView<String> differencesFromModelClass = Sets.difference(modelClassRelationNames, snapshotRelationNames);
            Sets.SetView<String> differencesFromSnapshotfile = Sets.difference(snapshotRelationNames, modelClassRelationNames);

            if (differencesFromModelClass.size() > 0 || differencesFromSnapshotfile.size() > 0) {
                log.error("Test failed.\n - Class: {}\n - The relation names are different in snapshot file and model class:\n - Relation names in model and not in snapshot: {}\n - Relation names in snapshot and not in model: {}", modelClass.getName(), differencesFromModelClass.toString(), differencesFromSnapshotfile.toString());
                return false;
            } else {
                return true;
            }
        } catch (IOException e) {
            log.error("Exception when trying to read relation names json file ({}).\n{}", snapshotFile.getName(), e.getMessage());
            return false;
        }
    }

}
