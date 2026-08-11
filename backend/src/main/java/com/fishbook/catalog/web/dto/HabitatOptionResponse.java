package com.fishbook.catalog.web.dto;

import com.fishbook.catalog.application.HabitatOptionView;

public record HabitatOptionResponse(String code, String labelZh) {

    public static HabitatOptionResponse from(HabitatOptionView view) {
        return new HabitatOptionResponse(view.code(), view.labelZh());
    }
}
