package dev.dylanott.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcError(int code, String message, Object data) {

    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    public static final int UNAUTHORIZED = -32001;
    public static final int FORBIDDEN = -32003;
    public static final int RESOURCE_NOT_FOUND = -32004;

    public static JsonRpcError of(int code, String message) {
        return new JsonRpcError(code, message, null);
    }
}
