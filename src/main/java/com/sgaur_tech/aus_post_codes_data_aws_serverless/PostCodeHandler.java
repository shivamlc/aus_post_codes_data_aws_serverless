package com.sgaur_tech.aus_post_codes_data_aws_serverless;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.sgaur_tech.aus_post_codes_data_aws_serverless.model.PostCodeDbEntity;
import com.sgaur_tech.aus_post_codes_data_aws_serverless.model.PostCodeRow;
import com.sgaur_tech.aus_post_codes_data_aws_serverless.service.DistanceService;
import com.sgaur_tech.aus_post_codes_data_aws_serverless.service.DynamoDbService;
import com.sgaur_tech.aus_post_codes_data_aws_serverless.service.GeoCodingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Component("postcodeHandler")
public class PostCodeHandler implements Function<S3Event, String> {

    private static final Logger log = LoggerFactory.getLogger(PostCodeHandler.class);

    // Geocode cache — avoids re-calling the API for repeated postcodes
    // (e.g. CENTRAL_POST_CODE 3000 appears on every VIC row)
    private final Map<String, double[]> geocodeCache = new ConcurrentHashMap<>();

    @Autowired
    private GeoCodingService geocodingService;
    @Autowired
    private DistanceService distanceService;
    @Autowired
    private DynamoDbService dynamoDbService;
    @Autowired
    private S3Client s3Client;

    @Override
    public String apply(S3Event s3Event) {
        for (S3EventNotificationRecord record : s3Event.getRecords()) {
            String bucket = record.getS3().getBucket().getName();
            String key = record.getS3().getObject().getKey();
            log.info("Processing s3://{}/{}", bucket, key);
            processFile(bucket, key);
        }
        return "OK";
    }

    private void processFile(String bucket, String key) {
        // Download CSV from S3
        ResponseBytes<GetObjectResponse> obj = s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(key).build());

        try (CSVReader reader = new CSVReader(new InputStreamReader(
                new ByteArrayInputStream(obj.asByteArray()), StandardCharsets.UTF_8))) {

            String[] header = reader.readNext(); // skip header row
            validateHeader(header);

            String[] row;
            int rowNum = 1;

            while ((row = reader.readNext()) != null) {
                rowNum++;
                if (row.length < 3) {
                    log.warn("Skipping malformed row {}: {}", rowNum, String.join(",", row));
                    continue;
                }

                PostCodeRow postcodeRow = new PostCodeRow(
                        row[0].trim(), // STATE
                        row[1].trim(), // POST_CODE
                        row[2].trim() // CENTRAL_POST_CODE
                );

                try {
                    PostCodeDbEntity result = resolve(postcodeRow);
                    dynamoDbService.save(result);
                } catch (Exception e) {
                    log.error("Failed row {}: {}", rowNum, e.getMessage());
                    // Continue processing remaining rows
                }

                // Nominatim rate limit: 1 request per second
                // The cache means we only sleep for genuinely new postcodes
                Thread.sleep(1100);
            }

        } catch (IOException | CsvValidationException | InterruptedException e) {
            throw new RuntimeException("Failed to process CSV: " + e.getMessage(), e);
        }
    }

    /**
     * Geocodes both the row's postcode and its central postcode,
     * then calculates the distance between them.
     */
    private PostCodeDbEntity resolve(PostCodeRow row) {
        double[] postLatLon = cachedGeocode(row.postCode());
        double[] centralLatLon = cachedGeocode(row.centralPostCode());

        double distanceKm = distanceService.calculateDistanceKm(
                postLatLon[0], postLatLon[1],
                centralLatLon[0], centralLatLon[1]);

        return new PostCodeDbEntity(
                row.state(),
                row.postCode(),
                row.centralPostCode(),
                postLatLon[0], postLatLon[1],
                centralLatLon[0], centralLatLon[1],
                distanceKm);
    }

    /**
     * Returns cached geocode result, or fetches and caches it.
     * This is the key optimisation — CENTRAL_POST_CODE (e.g. 3000)
     * appears on every row but is only geocoded once.
     */
    private double[] cachedGeocode(String postcode) {
        return geocodeCache.computeIfAbsent(postcode, geocodingService::getLatLon);
    }

    private void validateHeader(String[] header) {
        if (header == null || header.length < 3) {
            throw new RuntimeException("CSV header missing or too short");
        }
        // Case-insensitive check
        if (!header[0].trim().equalsIgnoreCase("STATE") ||
                !header[1].trim().equalsIgnoreCase("POST_CODE") ||
                !header[2].trim().equalsIgnoreCase("CENTRAL_POST_CODE")) {
            log.warn("Unexpected header: [{}, {}, {}] — proceeding anyway",
                    header[0], header[1], header[2]);
        }
    }
}