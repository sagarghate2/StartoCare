package com.startocare.paymentservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.startocare.paymentservice.exception.PaymentGatewayException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Component
public class RazorpayGateway {

    private final RestClient restClient;
    private final String keyId;
    private final String keySecret;

    public RazorpayGateway(
            RestClient.Builder builder,
            @Value("${app.razorpay.key-id}") String keyId,
            @Value("${app.razorpay.key-secret}") String keySecret
    ) {
        this.restClient = builder.clone().baseUrl("https://api.razorpay.com").build();
        this.keyId = keyId;
        this.keySecret = keySecret;
    }

    public String keyId() {
        return keyId;
    }

    public String createOrder(
            long amountPaise,
            String currency,
            String receipt,
            String bookingId
    ) {
        try {
            JsonNode response = restClient.post()
                    .uri("/v1/orders")
                    .headers(headers -> headers.setBasicAuth(keyId, keySecret))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "amount", amountPaise,
                            "currency", currency,
                            "receipt", receipt,
                            "notes", Map.of("booking_id", bookingId)
                    ))
                    .retrieve()
                    .body(JsonNode.class);
            String orderId = response == null ? "" : response.path("id").asText();
            if (orderId.isBlank()) {
                throw new PaymentGatewayException("Razorpay response did not contain an order ID");
            }
            return orderId;
        } catch (RestClientResponseException exception) {
            throw gatewayError("create order", exception);
        } catch (RestClientException exception) {
            throw new PaymentGatewayException("Could not connect to Razorpay while creating an order", exception);
        }
    }

    public CapturedPayment getPayment(String paymentId) {
        try {
            JsonNode response = restClient.get()
                    .uri("/v1/payments/{paymentId}", paymentId)
                    .headers(headers -> headers.setBasicAuth(keyId, keySecret))
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new PaymentGatewayException("Razorpay returned an empty payment response");
            }
            return new CapturedPayment(
                    response.path("id").asText(),
                    response.path("order_id").asText(),
                    response.path("amount").asLong(-1),
                    response.path("currency").asText(),
                    response.path("status").asText()
            );
        } catch (RestClientResponseException exception) {
            throw gatewayError("retrieve payment", exception);
        } catch (RestClientException exception) {
            throw new PaymentGatewayException("Could not connect to Razorpay while retrieving payment", exception);
        }
    }

    private PaymentGatewayException gatewayError(String operation, RestClientResponseException exception) {
        return new PaymentGatewayException(
                "Razorpay returned HTTP " + exception.getStatusCode().value()
                        + " while attempting to " + operation,
                exception
        );
    }

    public record CapturedPayment(
            String paymentId,
            String orderId,
            long amountPaise,
            String currency,
            String status
    ) {
    }
}
