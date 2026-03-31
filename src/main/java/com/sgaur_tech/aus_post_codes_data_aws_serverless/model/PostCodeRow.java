package com.sgaur_tech.aus_post_codes_data_aws_serverless.model;

/**
 * Represents one row from the CSV.
 * STATE and CENTRAL_POST_CODE can vary per row —
 * no assumptions about VIC or 3000 are hardcoded.
 */
public record PostCodeRow(
        String state,
        String postCode,
        String centralPostCode) {
}