package com.stylemind.commerce.dto.product;

import java.util.List;

public record ProductPage(
        long totalElements,
        List<Product> items) {
}
