package com.fishbook.common.error;

import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        List<FieldErrorItem> fieldErrors,
        String requestId) {}
