package dev.dylanott.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Content(String type, String text, String uri, String mimeType) {

    public static Content text(String text) {
        return new Content("text", text, null, null);
    }

    public static Content resource(String uri, String mimeType, String text) {
        return new Content("resource", text, uri, mimeType);
    }
}
