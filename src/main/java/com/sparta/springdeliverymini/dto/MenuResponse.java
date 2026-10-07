package com.sparta.springdeliverymini.dto;

import com.sparta.springdeliverymini.entity.Menu;

import java.time.LocalDateTime;

public record MenuResponse(
        Long id,
        String name,
        int price,
        String description,
        LocalDateTime updatedAt) {

    public static MenuResponse from(Menu menu) {
        return new MenuResponse(
                menu.getId(),
                menu.getName(),
                menu.getPrice(),
                menu.getDescription(),
                menu.getUpdatedAt()
        );
    }
}
