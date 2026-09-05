package com.bookly.business.dto;

import com.bookly.business.Business;
import java.util.UUID;

public record BusinessResponse(UUID id, String name, String slug, String timezone) {

    public static BusinessResponse from(Business business) {
        return new BusinessResponse(business.getId(), business.getName(),
                business.getSlug(), business.getTimezone());
    }
}
