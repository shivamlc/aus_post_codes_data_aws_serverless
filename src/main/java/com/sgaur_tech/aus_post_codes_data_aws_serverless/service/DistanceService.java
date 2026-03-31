package com.sgaur_tech.aus_post_codes_data_aws_serverless.service;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DistanceService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Haversine formula — accurate great-circle distance in km.
     * Error is < 0.3%, which is more than sufficient for postcode distances.
     */
    public double calculateDistanceKm(
            double lat1, double lon1,
            double lat2, double lon2) {

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}