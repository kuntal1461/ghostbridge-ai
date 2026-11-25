package com.ghostbridge.responsevo;

/** The CSRF header name and token a browser must send on every state-changing request. Held in memory only. */
public record AuthCsrfResponseVO(String headerName, String token) {
}
