package dev.dylanott.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcRequest(
        String jsonrpc,
        Object id,
        String method,
        JsonNode params
) {

    public JsonRpcRequest {
        if (jsonrpc == null) {
            jsonrpc = "2.0";
        }
    }

    public boolean isNotification() {
        return id == null;
    }
}
