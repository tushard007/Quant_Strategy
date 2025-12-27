package org.factor_investing.quant_strategy.service;

import com.opencsv.CSVWriter;
import org.factor_investing.quant_strategy.model.TopN_MomentumAssetType;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

@Service
public class CSVWriterService {
    public void writeNiftyIndexMomentumDataToCSV(Map<String, List<TopN_MomentumAssetType>> data) throws IOException {
        // Define the date format
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = sdf.format(new java.util.Date());

        // Define the file path
        String filePath = "src/main/resources/" + timestamp + ".csv";

        // Create the file
        File file = new File(filePath);


        try (CSVWriter writer = new CSVWriter(new FileWriter(filePath))) {
            // Write headers
            String[] headers = data.keySet().toArray(new String[0]);
            writer.writeNext(headers);

            // Find the maximum list size
            int maxSize = 40;

            // Write data rows
            for (int i = 0; i < maxSize; i++) {
                String[] row = new String[headers.length];
                for (int j = 0; j < headers.length; j++) {
                    List<TopN_MomentumAssetType> columnData = data.get(headers[j]);
                    row[j] = (i < columnData.size()) ? columnData.get(i).getStockName() : "";
                }
                writer.writeNext(row);
            }
        }
    }
}
