package art.arcane.react.api.web.dto;

public record ErrorEnvelope(Message error) {
    public record Message(String message) {}
}
