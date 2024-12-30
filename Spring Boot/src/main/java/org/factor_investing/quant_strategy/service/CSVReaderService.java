package org.factor_investing.quant_strategy.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CSVReaderService {
    @Autowired
    private ResourceLoader resourceLoader;

    public List<String[]> readRowWiseCSVFile(String filename) throws Exception {
        List<String[]> data = new ArrayList<>();
        try (CSVReader csvReader = new CSVReader(new BufferedReader(new InputStreamReader(new ClassPathResource(filename).getInputStream())))) {
            String[] nextLine;
            while ((nextLine = csvReader.readNext()) != null) {
                data.add(nextLine);
            }
        }
        return data;
    }

    public Map<String, List<String>> readColumnWiseCsv(String filename) throws Exception {
        Map<String, List<String>> columnData = new HashMap<>();

        try {
            ClassPathResource resource = new ClassPathResource(filename);
            BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()));
            try (CSVReader csvReader = new CSVReader(new BufferedReader(new InputStreamReader(new ClassPathResource(filename).getInputStream())))) {
                String[] headers = csvReader.readNext(); // Read the header row
                // Initialize lists for each column
                if (headers != null) {
                    for (String header : headers) {
                        columnData.put(header, new ArrayList<>());
                    }
                    String[] nextLine;
                    while ((nextLine = csvReader.readNext()) != null) {
                        // Add data to corresponding column
                        for (int i = 0; i < headers.length; i++) {
                            if (i < nextLine.length) {
                                if (!StringUtils.trimAllWhitespace(nextLine[i]).isEmpty()) {
                                    columnData.get(headers[i]).add(nextLine[i]);
                                }
                            }
                        }
                    }
                }
                return columnData;
            } catch (IOException | CsvValidationException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

