package dev.dylanott.mcp.protocol;

public final class McpMethod {

    private McpMethod() {
    }

    public static final String INITIALIZE = "initialize";
    public static final String INITIALIZED_NOTIFICATION = "notifications/initialized";
    public static final String PING = "ping";

    public static final String TOOLS_LIST = "tools/list";
    public static final String TOOLS_CALL = "tools/call";

    public static final String RESOURCES_LIST = "resources/list";
    public static final String RESOURCES_READ = "resources/read";

    public static final String PROTOCOL_VERSION = "2024-11-05";
}
