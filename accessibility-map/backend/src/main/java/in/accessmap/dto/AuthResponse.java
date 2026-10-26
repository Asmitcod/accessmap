package in.accessmap.dto;

public record AuthResponse(String token, String email, long expiresInSeconds) {
}
