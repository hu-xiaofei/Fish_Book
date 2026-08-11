package com.fishbook.catalog.domain;

public enum HabitatType {
    RIVER("江河"),
    LAKE("湖泊"),
    RESERVOIR("水库"),
    POND("池塘"),
    STREAM("溪流");

    private final String labelZh;

    HabitatType(String labelZh) {
        this.labelZh = labelZh;
    }

    public String labelZh() {
        return labelZh;
    }
}
