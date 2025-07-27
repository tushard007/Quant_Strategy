package org.factor_investing.quant_strategy.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Slf4j
@Component
public class JsonUtility {

    private static final String BASE_PATH = "src/main/resources/json/";
    private final ObjectMapper objectMapper;
    private final ObjectWriter objectWriter;
    private final ObjectMapper createObjectMapper = createObjectMapper();


    public JsonUtility() {
        this.objectMapper = new ObjectMapper();
        this.objectWriter = objectMapper.writerWithDefaultPrettyPrinter();
    }

    public static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
    /**
     * Write any object to a JSON file
     * @param data Object to be written
     * @param prefix Prefix for the filename
     * @param <T> Type of the object
     * @return File path where JSON was written
     */
    public <T> String writeToJson(T data, String prefix) {
        try {
            // Create directory if it doesn't exist
            File directory = new File(BASE_PATH);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // Generate filename with priceDate
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String filePath = BASE_PATH + prefix + "_" + timestamp + ".json";

            // Write JSON to file
            objectWriter.writeValue(new File(filePath), data);
            log.info("JSON file created successfully at: {}", filePath);
            return filePath;

        } catch (IOException e) {
            log.error("Failed to write JSON file for prefix {}: {}", prefix, e.getMessage());
            throw new RuntimeException("Error writing JSON file", e);
        }
    }

    /**
     * Read JSON file and convert to specified type
     * @param filePath Path to the JSON file
     * @param typeReference TypeReference of the target type
     * @param <T> Type to convert the JSON to
     * @return Object of type T
     */
    public <T> T readFromJson(String filePath, TypeReference<T> typeReference) {
        try {
            return createObjectMapper.readValue(new File(filePath), typeReference);
        } catch (IOException e) {
            log.error("Failed to read JSON file from {}: {}", filePath, e.getMessage());
            throw new RuntimeException("Error reading JSON file", e);
        }
    }
}