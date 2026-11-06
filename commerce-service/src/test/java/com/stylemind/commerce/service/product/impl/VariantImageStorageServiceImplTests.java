package com.stylemind.commerce.service.product.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.stylemind.commerce.errors.ApiOperationException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

class VariantImageStorageServiceImplTests {

    @TempDir
    Path legacyDirectory;

    @Test
    void uploadsWithDeterministicPublicIdAndReturnsSecureUrl() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Uploader uploader = mock(Uploader.class);
        UUID imageId = UUID.randomUUID();
        String publicId = "stylemind/products/" + imageId;
        String secureUrl = "https://res.cloudinary.com/test/image/upload/v1/" + publicId + ".png";
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenReturn(Map.of("public_id", publicId, "secure_url", secureUrl));
        VariantImageStorageServiceImpl service = service(cloudinary);

        String storedUrl = service.store(imageId,
                new MockMultipartFile("file", "product.png", "image/png", new byte[] {1, 2, 3}));

        assertThat(storedUrl).isEqualTo(secureUrl);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> options = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(any(byte[].class), options.capture());
        assertThat(options.getValue()).containsEntry("public_id", publicId)
                .containsEntry("resource_type", "image")
                .containsEntry("overwrite", false)
                .containsEntry("unique_filename", false);
    }

    @Test
    void mapsProviderFailureToSanitizedServiceUnavailable() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new IOException("provider detail"));

        assertThatThrownBy(() -> service(cloudinary).store(UUID.randomUUID(),
                new MockMultipartFile("file", "product.png", "image/png", new byte[] {1})))
                .isInstanceOfSatisfying(ApiOperationException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.code()).isEqualTo("IMAGE_UPLOAD_FAILED");
                    assertThat(exception.detail()).doesNotContain("provider detail");
                });
    }

    @Test
    void missingCloudinaryConfigurationFailsClosed() {
        VariantImageStorageServiceImpl service = new VariantImageStorageServiceImpl(
                "", "stylemind/products", legacyDirectory.toString());

        assertThatThrownBy(() -> service.store(UUID.randomUUID(),
                new MockMultipartFile("file", "product.png", "image/png", new byte[] {1})))
                .isInstanceOfSatisfying(ApiOperationException.class,
                        exception -> assertThat(exception.code()).isEqualTo("IMAGE_UPLOAD_FAILED"));
    }

    @Test
    void deleteUsesTheSameCloudinaryPublicId() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        UUID imageId = UUID.randomUUID();

        service(cloudinary).delete(imageId);

        verify(uploader).destroy(org.mockito.ArgumentMatchers.eq("stylemind/products/" + imageId), anyMap());
    }

    private VariantImageStorageServiceImpl service(Cloudinary cloudinary) {
        return new VariantImageStorageServiceImpl(cloudinary, "stylemind/products", legacyDirectory.toString());
    }
}
