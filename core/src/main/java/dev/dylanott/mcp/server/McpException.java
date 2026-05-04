package dev.dylanott.mcp.server;

import dev.dylanott.mcp.protocol.JsonRpcError;

public class McpException extends RuntimeException {

    private final int code;

    public McpException(int code, String message) {
        super(message);
        this.code = code;
    }

    public McpException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int code() {
        return code;
    }

    public JsonRpcError toError() {
        return JsonRpcError.of(code, getMessage());
    }
}
