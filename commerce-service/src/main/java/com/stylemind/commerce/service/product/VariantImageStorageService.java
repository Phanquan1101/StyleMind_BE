package com.stylemind.commerce.service.product;

import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface VariantImageStorageService {

    String store(UUID imageId, MultipartFile file);

    Optional<Resource> load(UUID imageId);

    void delete(UUID imageId);
}
