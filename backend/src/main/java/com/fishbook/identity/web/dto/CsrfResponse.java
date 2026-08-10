package com.fishbook.identity.web.dto;

public record CsrfResponse(String token, String headerName) {}
