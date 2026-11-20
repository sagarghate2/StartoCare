package com.startocare.notificationservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

@Configuration
public class AwsConfig {

    @Bean
    @ConditionalOnProperty(name = "aws.sns.enabled", havingValue = "true", matchIfMissing = true)
    public SnsClient snsClient(
            @Value("${aws.region:us-east-1}") String region,
            @Value("${aws.endpoint:}") String endpoint,
            @Value("${aws.access-key:test}") String accessKey,
            @Value("${aws.secret-key:test}") String secretKey
    ) {
        software.amazon.awssdk.services.sns.SnsClientBuilder builder = SnsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "aws.sqs.enabled", havingValue = "true", matchIfMissing = true)
    public SqsClient sqsClient(
            @Value("${aws.region:us-east-1}") String region,
            @Value("${aws.endpoint:}") String endpoint,
            @Value("${aws.access-key:test}") String accessKey,
            @Value("${aws.secret-key:test}") String secretKey
    ) {
        software.amazon.awssdk.services.sqs.SqsClientBuilder builder = SqsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
