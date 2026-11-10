package com.toeic.backend.identity.application;

public interface GoogleTokenVerifier {
    Identity verify(String credential);
    record Identity(String subject, String email, String fullName, String nonce, boolean authoritativeEmail) {
        @Override public String toString() { return "GoogleIdentity[redacted]"; }
    }
}
