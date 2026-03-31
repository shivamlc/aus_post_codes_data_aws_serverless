package com.sgaur_tech.aus_post_codes_data_aws_serverless.service;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.ParameterizedTypeReference;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GeoCodingService {

    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search"
            + "?postalcode={postcode}&country=Australia&format=json&limit=1";

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Returns [latitude, longitude] for an Australian postcode.
     * Uses Nominatim (free, no API key). Swap the URL for Google Maps
     * if you need higher reliability or throughput.
     */
    public double[] getLatLon(String postcode) {
        HttpHeaders headers = new HttpHeaders();
        // Nominatim requires a descriptive User-Agent
        headers.set("User-Agent", "postcode-distance-app/1.0 contact@example.com");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                NOMINATIM_URL,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {
                },
                postcode);

        List<Map<String, Object>> body = response.getBody();
        if (body == null || body.isEmpty()) {
            throw new RuntimeException(
                    "Geocoding returned no results for postcode: " + postcode);
        }

        Map<String, Object> first = body.get(0);
        double lat = Double.parseDouble(first.get("lat").toString());
        double lon = Double.parseDouble(first.get("lon").toString());

        log.info("Geocoded {} → lat={}, lon={}", postcode, lat, lon);
        return new double[] { lat, lon };
    }
}