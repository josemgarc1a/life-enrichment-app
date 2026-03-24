package com.lifeenrichment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

/**
 * Service wrapping AWS S3 operations needed by the Life Enrichment App.
 *
 * <p>Currently supports two operations:
 * <ul>
 *   <li>{@link #uploadFile} — streams a {@link MultipartFile} to S3 and returns its public URL.</li>
 *   <li>{@link #deleteFile} — removes an object by key; silently no-ops if the key does not exist.</li>
 * </ul>
 *
 * <p>The bucket name and AWS region are injected from application properties
 * ({@code aws.s3.bucket-name} and {@code aws.region}). The public URL is constructed
 * using the standard S3 virtual-hosted-style format:
 * {@code https://<bucket>.s3.<region>.amazonaws.com/<key>}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.region}")
    private String region;

    /**
     * Uploads a multipart file to S3 under the given key and returns its public URL.
     *
     * @param key  the S3 object key (e.g. {@code "residents/uuid.jpg"})
     * @param file the file to upload; its content type and size are forwarded to S3
     * @return the public HTTPS URL of the uploaded object
     * @throws RuntimeException if the file bytes cannot be read
     */
    public String uploadFile(String key, MultipartFile file) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

            String url = "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key;
            log.info("Uploaded file to S3: {}", url);
            return url;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read upload file bytes", e);
        }
    }

    /**
     * Deletes an S3 object by key. If the key does not exist, the method logs a warning
     * and returns normally — it does not throw an exception.
     *
     * @param key the S3 object key to delete (e.g. {@code "residents/uuid.jpg"})
     */
    public void deleteFile(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
            log.info("Deleted S3 object: {}", key);
        } catch (NoSuchKeyException e) {
            log.warn("S3 object not found, skipping delete: {}", key);
        }
    }
}
