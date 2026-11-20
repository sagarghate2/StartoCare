package com.startocare.bookingservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startocare.bookingservice.dto.response.CompanionLocationResponse;
import com.startocare.bookingservice.exception.EstimateUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GeoapifyRoutingClientTest {

    @Test
    void includesGeoapifyErrorReasonWithoutExposingApiKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/v1/geocode/search")))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"results":[{"lat":17.4,"lon":78.5}]}
                                """));
        server.expect(requestTo(containsString("/v1/routing")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"message":"API key is not authorized to use this service"}
                                """));
        GeoapifyRoutingClient client =
                new GeoapifyRoutingClient(builder, new ObjectMapper(), "private-test-key");

        assertThatThrownBy(() -> client.getTwoWheelerDistanceMeters(
                new CompanionLocationResponse(UUID.randomUUID(), "Companion base", 17.4, 78.5),
                GeoapifyRoutingClient.RoutePoint.companion(),
                GeoapifyRoutingClient.RoutePoint.address("Hospital, Hyderabad")
        ))
                .isInstanceOf(EstimateUnavailableException.class)
                .hasMessageContaining("HTTP 403")
                .hasMessageContaining("API key is not authorized to use this service")
                .hasMessageNotContaining("private-test-key");

        server.verify();
    }

    @Test
    void returnsDistanceFromSuccessfulRouteResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/v1/routing")))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"type":"FeatureCollection","features":[{"type":"Feature","properties":{"distance":1234}}]}
                                """));
        GeoapifyRoutingClient client =
                new GeoapifyRoutingClient(builder, new ObjectMapper(), "private-test-key");

        long distance = client.getTwoWheelerDistanceMeters(
                new CompanionLocationResponse(UUID.randomUUID(), "Companion base", 17.4, 78.5),
                GeoapifyRoutingClient.RoutePoint.companion(),
                GeoapifyRoutingClient.RoutePoint.companion()
        );

        assertThat(distance).isEqualTo(1234L);
        server.verify();
    }
}
