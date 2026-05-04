package dev.dylanott.mcp.server;

import dev.dylanott.mcp.protocol.JsonRpcError;
import dev.dylanott.mcp.protocol.Tool;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolRegistry {

    private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();

    public synchronized void register(RegisteredTool tool) {
        if (tools.putIfAbsent(tool.name(), tool) != null) {
            throw new IllegalStateException("Duplicate MCP tool name: " + tool.name());
        }
    }

    public RegisteredTool require(String name) {
        RegisteredTool tool = tools.get(name);
        if (tool == null) {
            throw new McpException(JsonRpcError.METHOD_NOT_FOUND, "Unknown tool: " + name);
        }
        return tool;
    }

    public List<Tool> describe() {
        return tools.values().stream().map(RegisteredTool::descriptor).toList();
    }

    public Collection<RegisteredTool> all() {
        return tools.values();
    }

    public int size() {
        return tools.size();
    }
}
