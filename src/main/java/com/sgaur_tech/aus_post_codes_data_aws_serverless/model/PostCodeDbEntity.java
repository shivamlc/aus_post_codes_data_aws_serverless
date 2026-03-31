package com.sgaur_tech.aus_post_codes_data_aws_serverless.model;

/**
 * Fully resolved row ready to be written to DynamoDB.
 */
public record PostCodeDbEntity(
        String state,
        String postCode,
        String centralPostCode,
        double latitude,
        double longitude,
        double centralLatitude,
        double centralLongitude,
        double distanceKm) {
}