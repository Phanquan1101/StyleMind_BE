package com.stylemind.commerce.controller.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.dto.product.ProductListResponse;
import com.stylemind.commerce.dto.product.ProductResponse;
import com.stylemind.commerce.dto.product.VariantBatchLookupRequest;
import com.stylemind.commerce.dto.product.VariantBatchLookupResponse;
import com.stylemind.commerce.dto.product.VariantImageResponse;
import com.stylemind.commerce.dto.product.VariantResponse;
import com.stylemind.commerce.model.product.ProductCommand;
import com.stylemind.commerce.model.product.ProductListCriteria;
import com.stylemind.commerce.model.product.VariantCommand;
import com.stylemind.commerce.service.product.ProductService;
import com.stylemind.commerce.service.product.VariantImageStorageService;
import com.stylemind.commerce.validation.product.ProductRequestValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final VariantImageStorageService imageStorage;
    private final ProductRequestValidator validator;

    @GetMapping(path = "/api/v1/products", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List active products",
            description = "Supports keyword, category_id, min_price_vnd, max_price_vnd, demographic_segment, page and size filters.")
    public ProductListResponse listProducts(HttpServletRequest request) {
        ProductListCriteria criteria = validator.listCriteria(request);
        return productService.listPublicProducts(criteria);
    }

    @GetMapping(path = "/api/v1/products/{productId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get active product detail")
    public ProductResponse getProduct(@PathVariable String productId) {
        return productService.getPublicProduct(validator.pathId(productId));
    }

    @PostMapping(path = "/api/v1/variants/batch-lookup", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Batch lookup active variants with current prices")
    public VariantBatchLookupResponse batchLookup(@RequestBody VariantBatchLookupRequest request) {
        return productService.activeVariantLookup(validator.variantIds(request));
    }

    @PostMapping(path = "/api/v1/admin/products", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create product (ADMIN only)")
    public ResponseEntity<ProductResponse> createProduct(@RequestBody JsonNode body) {
        ProductCommand command = validator.productCommand(body);
        ProductResponse created = productService.createProduct(command);
        return ResponseEntity.created(URI.create("/api/v1/products/" + created.productId())).body(created);
    }

    @GetMapping(path = "/api/v1/admin/products/{productId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get product detail including inactive variants (ADMIN only)")
    public ProductResponse getAdminProduct(@PathVariable String productId) {
        return productService.getAdminProduct(validator.pathId(productId));
    }

    @PutMapping(path = "/api/v1/admin/products/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace product (ADMIN only)")
    public ProductResponse replaceProduct(@PathVariable String productId, @RequestBody JsonNode body) {
        return productService.replaceProduct(validator.pathId(productId), validator.productCommand(body));
    }

    @DeleteMapping("/api/v1/admin/products/{productId}")
    @Operation(summary = "Delete product (ADMIN only)")
    public ResponseEntity<Void> deleteProduct(@PathVariable String productId) {
        productService.deleteProduct(validator.pathId(productId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/api/v1/admin/products/{productId}/variants", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create product variant (ADMIN only)")
    public ResponseEntity<VariantResponse> createVariant(@PathVariable String productId, @RequestBody JsonNode body) {
        VariantCommand command = validator.variantCommand(body);
        VariantResponse created = productService.createVariant(validator.pathId(productId), command);
        return ResponseEntity.created(URI.create("/api/v1/admin/variants/" + created.variantId())).body(created);
    }

    @PutMapping(path = "/api/v1/admin/variants/{variantId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace product variant (ADMIN only)")
    public VariantResponse replaceVariant(@PathVariable String variantId, @RequestBody JsonNode body) {
        return productService.replaceVariant(validator.pathId(variantId), validator.variantCommand(body));
    }

    @DeleteMapping("/api/v1/admin/variants/{variantId}")
    @Operation(summary = "Delete product variant (ADMIN only)")
    public ResponseEntity<Void> deleteVariant(@PathVariable String variantId) {
        productService.deleteVariant(validator.pathId(variantId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/api/v1/admin/variants/{variantId}/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Upload variant image to local Commerce storage (ADMIN only)",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Image metadata created"),
                    @ApiResponse(responseCode = "400", description = "Invalid image file"),
                    @ApiResponse(responseCode = "404", description = "Variant not found")
            })
    public ResponseEntity<VariantImageResponse> uploadImage(
            @PathVariable String variantId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "alt_text", required = false) String altText) {
        validator.imageFile(file);
        VariantImageResponse created = productService.uploadImage(validator.pathId(variantId), file,
                validator.altText(altText));
        return ResponseEntity.created(URI.create(created.imageUrl())).body(created);
    }

    @PutMapping(path = "/api/v1/admin/variants/{variantId}/images/reorder",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reorder variant images and choose primary image (ADMIN only)")
    public List<VariantImageResponse> reorderImages(
            @PathVariable String variantId,
            @RequestBody com.stylemind.commerce.dto.product.ImageReorderRequest request) {
        return productService.reorderImages(validator.pathId(variantId), validator.imageOrder(request),
                validator.optionalPrimaryImageId(request));
    }

    @DeleteMapping("/api/v1/admin/variant-images/{imageId}")
    @Operation(summary = "Delete variant image (ADMIN only)")
    public ResponseEntity<Void> deleteImage(@PathVariable String imageId) {
        productService.deleteImage(validator.pathId(imageId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/variant-images/{imageId}/content")
    @Operation(summary = "Read locally stored variant image content")
    public ResponseEntity<Resource> imageContent(@PathVariable String imageId) {
        UUID id = validator.pathId(imageId);
        Resource resource = imageStorage.load(id).orElseThrow(() ->
                new com.stylemind.commerce.errors.ApiOperationException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "max-age=300")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
