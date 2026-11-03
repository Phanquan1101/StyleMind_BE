package com.stylemind.wardrobecommunity.storage.chat;

import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.model.chat.ChatAttachmentContent;
import com.stylemind.wardrobecommunity.model.chat.ChatImage;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

/** Private S3-compatible storage. Configuration is operator-owned; no account/bucket creation or local fallback. */
@Component
public class S3ChatImageStorage implements ChatImageStorage {
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private final boolean enabled;
    private final String bucket;
    private final String endpoint;
    private final String region;
    private volatile S3Client client;
    private DefaultCredentialsProvider credentials;

    public S3ChatImageStorage(@Value("${stylemind.chat.images.enabled:false}") boolean enabled,
            @Value("${stylemind.chat.images.bucket:}") String bucket,
            @Value("${stylemind.chat.images.endpoint:}") String endpoint,
            @Value("${stylemind.chat.images.region:}") String region) {
        this.enabled = enabled;
        this.bucket = bucket;
        this.endpoint = endpoint;
        this.region = region;
    }
    @Override public void requireAvailable() {
        if (!enabled || bucket.isBlank() || region.isBlank()) { throw unavailable(); }
    }
    private S3Client client() {
        requireAvailable();
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    try {
                        credentials = DefaultCredentialsProvider.builder().build();
                        var builder = S3Client.builder().region(Region.of(region)).credentialsProvider(credentials)
                                .httpClientBuilder(UrlConnectionHttpClient.builder()
                                        .connectionTimeout(Duration.ofSeconds(3)).socketTimeout(Duration.ofSeconds(10)))
                                .serviceConfiguration(S3Configuration.builder()
                                        .pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build())
                                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(30))
                                        .apiCallAttemptTimeout(Duration.ofSeconds(10)));
                        if (!endpoint.isBlank()) {
                            URI uri = URI.create(endpoint);
                            if (uri.getUserInfo() != null || uri.getHost() == null
                                    || !(uri.getScheme().equals("https") || uri.getScheme().equals("http"))
                                    || uri.getRawQuery() != null || uri.getRawFragment() != null) { throw unavailable(); }
                            if (uri.getScheme().equals("http") && !java.util.Set.of("localhost", "127.0.0.1", "host.docker.internal").contains(uri.getHost())) {
                                throw unavailable();
                            }
                            builder.endpointOverride(uri);
                        }
                        client = builder.build();
                    } catch (RuntimeException invalid) { throw unavailable(); }
                }
            }
        }
        return client;
    }
    @Override public void put(String key, ChatImage image) {
        try {
            client().putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(image.contentType()).build(),
                    RequestBody.fromBytes(image.bytes()));
        } catch (RuntimeException failed) { throw unavailable(); }
    }
    @Override public ChatAttachmentContent get(String key) {
        try (var stream = client().getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            Long length = stream.response().contentLength();
            if (length == null || length < 1 || length > MAX_BYTES) { throw unavailable(); }
            byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
            if (bytes.length != length || bytes.length > MAX_BYTES) { throw unavailable(); }
            return new ChatAttachmentContent(bytes, stream.response().contentType());
        } catch (Exception failed) { throw unavailable(); }
    }
    @Override public void delete(String key) {
        try { client().deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build()); }
        catch (RuntimeException failed) { throw unavailable(); }
    }
    @PreDestroy public void close() {
        if (client != null) { client.close(); }
        if (credentials != null) { credentials.close(); }
    }
    private static ApiOperationException unavailable() {
        return new ApiOperationException(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_IMAGE_STORAGE_UNAVAILABLE",
                "Private chat image storage is temporarily unavailable");
    }
}
