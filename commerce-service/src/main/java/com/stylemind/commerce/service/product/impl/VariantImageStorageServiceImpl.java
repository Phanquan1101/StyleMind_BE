package com.stylemind.commerce.service.product.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.service.product.VariantImageStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VariantImageStorageServiceImpl implements VariantImageStorageService {

    private final Cloudinary cloudinary;
    private final String cloudinaryFolder;
    private final Path legacyLocalRoot;

    @Autowired
    public VariantImageStorageServiceImpl(
            @Value("${stylemind.commerce.image-storage.cloudinary-url:}") String cloudinaryUrl,
            @Value("${stylemind.commerce.image-storage.cloudinary-folder:stylemind/products}") String cloudinaryFolder,
            @Value("${stylemind.commerce.image-storage.local-directory:target/variant-images}") String localDirectory) {
        this(cloudinaryUrl == null || cloudinaryUrl.isBlank() ? null : configuredClient(cloudinaryUrl),
                cloudinaryFolder, localDirectory);
    }

    VariantImageStorageServiceImpl(Cloudinary cloudinary, String cloudinaryFolder, String localDirectory) {
        this.cloudinary = cloudinary;
        this.cloudinaryFolder = normalizedFolder(cloudinaryFolder);
        this.legacyLocalRoot = Path.of(localDirectory).toAbsolutePath().normalize();
    }

    @Override
    public String store(UUID imageId, MultipartFile file) {
        if (cloudinary == null) {
            throw uploadFailed();
        }
        try {
            String publicId = publicId(imageId);
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "public_id", publicId,
                    "resource_type", "image",
                    "type", "upload",
                    "overwrite", false,
                    "unique_filename", false));
            Object returnedPublicId = result.get("public_id");
            Object secureUrl = result.get("secure_url");
            if (!publicId.equals(returnedPublicId) || !(secureUrl instanceof String url)
                    || !url.startsWith("https://")) {
                deleteCloudinaryAsset(publicId);
                throw uploadFailed();
            }
            return url;
        } catch (ApiOperationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw uploadFailed();
        }
    }

    @Override
    public Optional<Resource> load(UUID imageId) {
        try {
            if (!Files.isDirectory(legacyLocalRoot)) {
                return Optional.empty();
            }
            try (java.util.stream.Stream<Path> paths = Files.list(legacyLocalRoot)) {
                Optional<Path> path = paths
                        .filter(candidate -> candidate.getFileName().toString().startsWith(imageId.toString()))
                        .findFirst();
                if (path.isEmpty()) {
                    return Optional.empty();
                }
                Resource resource = new UrlResource(path.get().toUri());
                return resource.exists() && resource.isReadable() ? Optional.of(resource) : Optional.empty();
            }
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(UUID imageId) {
        deleteCloudinaryAsset(publicId(imageId));
        deleteLegacyLocalAsset(imageId);
    }

    private void deleteCloudinaryAsset(String publicId) {
        if (cloudinary == null) {
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap(
                    "resource_type", "image",
                    "type", "upload",
                    "invalidate", true));
        } catch (IOException | RuntimeException ignored) {
            // Database metadata remains authoritative; remote cleanup can be retried operationally.
        }
    }

    private void deleteLegacyLocalAsset(UUID imageId) {
        try {
            if (!Files.isDirectory(legacyLocalRoot)) {
                return;
            }
            try (java.util.stream.Stream<Path> paths = Files.list(legacyLocalRoot)) {
                paths.filter(candidate -> candidate.getFileName().toString().startsWith(imageId.toString()))
                        .forEach(candidate -> {
                            try {
                                Files.deleteIfExists(candidate);
                            } catch (IOException ignored) {
                                // Metadata deletion is authoritative; local file cleanup can be retried operationally.
                            }
                        });
            }
        } catch (IOException ignored) {
            // Metadata deletion is authoritative; local file cleanup can be retried operationally.
        }
    }

    private String publicId(UUID imageId) {
        return cloudinaryFolder + "/" + imageId;
    }

    private static Cloudinary configuredClient(String cloudinaryUrl) {
        Cloudinary client = new Cloudinary(cloudinaryUrl);
        client.config.secure = true;
        return client;
    }

    private String normalizedFolder(String folder) {
        String value = folder == null ? "" : folder.strip().replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty() || !value.matches("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*")) {
            throw new IllegalArgumentException("Cloudinary image folder is invalid");
        }
        return value;
    }

    private ApiOperationException uploadFailed() {
        return new ApiOperationException(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_UPLOAD_FAILED",
                "Image upload could not be stored");
    }
}
