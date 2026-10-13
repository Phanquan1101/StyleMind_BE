package com.stylemind.commerce.model.product;

import java.util.List;

public record ProductPage(
        long totalElements,
        List<Product> items) {
}
