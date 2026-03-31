package com.sgaur_tech.aus_post_codes_data_aws_serverless.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sgaur_tech.aus_post_codes_data_aws_serverless.model.PostCodeDbEntity;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class DynamoDbService {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbService.class);

    private final DynamoDbClient client;
    private final String tableName;

    public DynamoDbService(@Value("${dynamodb.table}") String tableName) {
        // Uses default credential chain (Lambda execution role)
        this.client = DynamoDbClient.create();
        this.tableName = tableName;
    }

    public void save(PostCodeDbEntity r) {
        Map<String, AttributeValue> item = new HashMap<>();

        // PK is STATE#POST_CODE — unique per postcode within its state grouping
        item.put("PK", str(r.state() + "#" + r.postCode()));
        item.put("STATE", str(r.state()));
        item.put("POST_CODE", str(r.postCode()));
        item.put("CENTRAL_POST_CODE", str(r.centralPostCode()));
        item.put("LATITUDE", num(r.latitude()));
        item.put("LONGITUDE", num(r.longitude()));
        item.put("CENTRAL_LATITUDE", num(r.centralLatitude()));
        item.put("CENTRAL_LONGITUDE", num(r.centralLongitude()));
        item.put("DISTANCE_KM", num(r.distanceKm()));

        client.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());

        log.info("Saved {}/{} → {:.4f} km from {}",
                r.state(), r.postCode(), r.distanceKm(), r.centralPostCode());
    }

    private AttributeValue str(String v) {
        return AttributeValue.fromS(v);
    }

    private AttributeValue num(double v) {
        return AttributeValue.fromN(String.format("%.6f", v));
    }
}