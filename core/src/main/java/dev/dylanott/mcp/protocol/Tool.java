package dev.dylanott.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Tool(
        String name,
        String description,
        @JsonProperty("inputSchema") ObjectNode inputSchema
) {
}
