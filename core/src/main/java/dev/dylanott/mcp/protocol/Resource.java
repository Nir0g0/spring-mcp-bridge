package dev.dylanott.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Resource(
        String uri,
        String name,
        String description,
        @JsonProperty("mimeType") String mimeType
) {
}
