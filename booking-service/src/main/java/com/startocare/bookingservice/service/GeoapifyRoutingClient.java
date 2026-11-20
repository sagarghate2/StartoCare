package com.startocare.bookingservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startocare.bookingservice.dto.response.CompanionLocationResponse;
import com.startocare.bookingservice.exception.EstimateUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GeoapifyRoutingClient {

    private static final String GEOAPIFY_URL = "https://api.geoapify.com";

    private final RestClient geocodingClient;
    private final RestClient routingClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public GeoapifyRoutingClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.geoapify.api-key:}") String apiKey
    ) {
        this.geocodingClient = restClientBuilder.clone().baseUrl(GEOAPIFY_URL).build();
        this.routingClient = restClientBuilder.clone().baseUrl(GEOAPIFY_URL).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    public long getTwoWheelerDistanceMeters(
            CompanionLocationResponse companion,
            RoutePoint origin,
            RoutePoint destination
    ) {
        if (apiKey.isBlank()) {
            throw new EstimateUnavailableException("Geoapify Geocoding and Routing API key is not configured");
        }
        try {
            Coordinates originCoordinates = coordinatesFor(companion, origin);
            Coordinates destinationCoordinates = coordinatesFor(companion, destination);
            JsonNode response = routingClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/routing")
                            .queryParam("waypoints", originCoordinates.toGeoapifyWaypoint()
                                    + "|" + destinationCoordinates.toGeoapifyWaypoint())
                            .queryParam("mode", "motorcycle")
                            .queryParam("format", "geojson")
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode distance = response == null
                    ? null
                    : response.path("features").path(0).path("properties").path("distance");
            if (distance == null || !distance.isNumber() || distance.doubleValue() < 0) {
                throw new EstimateUnavailableException("Geoapify did not return a motorcycle route for this travel leg");
            }
            return Math.round(distance.doubleValue());
        } catch (EstimateUnavailableException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw new EstimateUnavailableException(
                    "Geoapify returned HTTP " + exception.getStatusCode().value()
                            + ": " + providerErrorMessage(exception),
                    exception
            );
        } catch (RestClientException exception) {
            throw new EstimateUnavailableException(
                    "Could not connect to Geoapify. Check network access and the API key configuration",
                    exception
            );
        }
    }

    private Coordinates coordinatesFor(CompanionLocationResponse companion, RoutePoint point) {
        if (point.companionLocation()) {
            return new Coordinates(companion.latitude(), companion.longitude());
        }
        JsonNode response = geocodingClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/geocode/search")
                        .queryParam("text", point.address())
                        .queryParam("format", "json")
                        .queryParam("limit", 1)
                        .queryParam("apiKey", apiKey)
                        .build())
                .retrieve()
                .body(JsonNode.class);
        JsonNode result = response == null ? null : response.path("results").path(0);
        JsonNode latitude = result == null ? null : result.path("lat");
        JsonNode longitude = result == null ? null : result.path("lon");
        if (latitude == null || longitude == null
                || !latitude.isNumber() || !longitude.isNumber()
                || !Double.isFinite(latitude.doubleValue()) || !Double.isFinite(longitude.doubleValue())) {
            throw new EstimateUnavailableException("Geoapify could not find coordinates for address: " + point.address());
        }
        return new Coordinates(latitude.doubleValue(), longitude.doubleValue());
    }

    private String providerErrorMessage(RestClientResponseException exception) {
        try {
            JsonNode response = objectMapper.readTree(exception.getResponseBodyAsString());
            String message = response.path("message").asText();
            if (message.isBlank()) {
                message = response.path("error").path("message").asText();
            }
            if (!message.isBlank()) {
                message = message.replace(apiKey, "[redacted]");
                return message.length() <= 300 ? message : message.substring(0, 300);
            }
        } catch (JsonProcessingException ignored) {
            return "Geoapify did not return a readable error message";
        }
        return "Geoapify did not provide an error message";
    }

    public record RoutePoint(String address, boolean companionLocation) {
        public static RoutePoint address(String address) {
            return new RoutePoint(address, false);
        }

        public static RoutePoint companion() {
            return new RoutePoint(null, true);
        }
    }

    private record Coordinates(double latitude, double longitude) {
        private String toGeoapifyWaypoint() {
            return latitude + "," + longitude;
        }
    }
}
