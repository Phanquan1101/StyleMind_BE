package com.stylemind.stylist.client.commerce;

import com.stylemind.stylist.client.commerce.CommerceCatalogContracts.ProductResponse;
import com.stylemind.stylist.client.commerce.CommerceCatalogContracts.ProductListResponse;
import com.stylemind.stylist.client.commerce.CommerceCatalogContracts.VariantBatchLookupRequest;
import com.stylemind.stylist.client.commerce.CommerceCatalogContracts.VariantBatchLookupResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "stylemindCommerceCatalog",
        url = "${stylemind.clients.commerce.base-url}",
        configuration = CommerceFeignConfiguration.class)
public interface CommerceCatalogFeignClient {

    @GetMapping(path = "/api/v1/products/{productId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ProductResponse getProduct(@PathVariable("productId") UUID productId);

    @GetMapping(path = "/api/v1/products", produces = MediaType.APPLICATION_JSON_VALUE)
    ProductListResponse listProducts(@RequestParam("page") int page, @RequestParam("size") int size,
                                     @RequestParam("demographic_segment") String demographicSegment);

    @PostMapping(path = "/api/v1/variants/batch-lookup", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    VariantBatchLookupResponse lookupActiveVariants(@RequestBody VariantBatchLookupRequest request);
}
