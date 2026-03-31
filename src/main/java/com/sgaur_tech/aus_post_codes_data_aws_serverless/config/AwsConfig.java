package com.sgaur_tech.aus_post_codes_data_aws_serverless.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AwsConfig {

    @Bean
    public S3Client s3Client() {
        // UrlConnectionHttpClient is recommended for Lambda
        // (lighter than Apache HTTP client)
        return S3Client.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }
}