package dev.dylanott.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InitializeResult(
        String protocolVersion,
        Map<String, Object> capabilities,
        ServerInfo serverInfo
) {

    public record ServerInfo(String name, String version) {
    }
}
