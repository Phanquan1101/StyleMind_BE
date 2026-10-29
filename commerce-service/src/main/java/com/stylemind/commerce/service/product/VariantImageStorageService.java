package com.stylemind.commerce.service.product;

import com.stylemind.commerce.errors.ApiOperationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VariantImageStorageService {

    private final Path root;

    public VariantImageStorageService(
            @Value("${stylemind.commerce.image-storage.local-directory:target/variant-images}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    public String store(UUID imageId, MultipartFile file) {
        try {
            Files.createDirectories(root);
            String extension = extension(file.getContentType());
            Path target = root.resolve(imageId + extension).normalize();
            if (!target.startsWith(root)) {
                throw new IllegalStateException("Resolved image path escaped the storage root");
            }
            file.transferTo(target);
            return "/api/v1/variant-images/" + imageId + "/content";
        } catch (IOException exception) {
            throw new ApiOperationException(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_UPLOAD_FAILED",
                    "Image upload could not be stored");
        }
    }

    public Optional<Resource> load(UUID imageId) {
        try {
            if (!Files.isDirectory(root)) {
                return Optional.empty();
            }
            try (java.util.stream.Stream<Path> paths = Files.list(root)) {
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

    public void delete(UUID imageId) {
        try {
            if (!Files.isDirectory(root)) {
                return;
            }
            try (java.util.stream.Stream<Path> paths = Files.list(root)) {
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

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "Request is missing or malformed");
        };
    }
}
