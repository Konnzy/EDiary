package com.ediary.backend.security;

public record CsrfTokenResponse(String token, String headerName) {
}
