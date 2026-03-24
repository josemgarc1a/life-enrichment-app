package com.lifeenrichment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock private S3Client s3Client;

    @InjectMocks private S3Service s3Service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(s3Service, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(s3Service, "region", "us-east-1");
    }

    // ── uploadFile ────────────────────────────────────────────────────────────

    @Test
    void uploadFile_returnsCorrectUrl() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        String url = s3Service.uploadFile("residents/test-key.jpg", file);

        assertThat(url).isEqualTo(
                "https://test-bucket.s3.us-east-1.amazonaws.com/residents/test-key.jpg");
    }

    @Test
    void uploadFile_callsPutObjectWithCorrectBucketAndKey() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", "bytes".getBytes());

        s3Service.uploadFile("residents/my-key.png", file);

        verify(s3Client).putObject(
                argThat((PutObjectRequest r) ->
                        r.bucket().equals("test-bucket") && r.key().equals("residents/my-key.png")),
                any(RequestBody.class));
    }

    // ── deleteFile ────────────────────────────────────────────────────────────

    @Test
    void deleteFile_callsDeleteObjectWithCorrectKey() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        s3Service.deleteFile("residents/old-key.jpg");

        verify(s3Client).deleteObject(
                argThat((DeleteObjectRequest r) ->
                        r.bucket().equals("test-bucket") && r.key().equals("residents/old-key.jpg")));
    }

    @Test
    void deleteFile_noOps_whenKeyDoesNotExist() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        // should not throw
        s3Service.deleteFile("residents/missing-key.jpg");

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }
}
