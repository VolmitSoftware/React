package art.arcane.react.api.web;

import java.util.Set;

public record TokenRecord(String id, String label, long issuedAt, Set<String> scopes, String role) {}
