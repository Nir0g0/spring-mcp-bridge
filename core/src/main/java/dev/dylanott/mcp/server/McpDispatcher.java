package dev.dylanott.mcp.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dylanott.mcp.protocol.Content;
import dev.dylanott.mcp.protocol.InitializeResult;
import dev.dylanott.mcp.protocol.JsonRpcError;
import dev.dylanott.mcp.protocol.JsonRpcRequest;
import dev.dylanott.mcp.protocol.JsonRpcResponse;
import dev.dylanott.mcp.protocol.McpMethod;
import dev.dylanott.mcp.protocol.ToolCallResult;
import dev.dylanott.mcp.server.invoker.ResourceInvoker;
import dev.dylanott.mcp.server.invoker.ToolInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class McpDispatcher {

    private static final Logger log = LoggerFactory.getLogger(McpDispatcher.class);

    private final ObjectMapper mapper;
    private final ToolRegistry tools;
    private final ResourceRegistry resources;
    private final ToolInvoker toolInvoker;
    private final ResourceInvoker resourceInvoker;
    private final String serverName;
    private final String serverVersion;

    public McpDispatcher(ObjectMapper mapper,
                         ToolRegistry tools,
                         ResourceRegistry resources,
                         ToolInvoker toolInvoker,
                         ResourceInvoker resourceInvoker,
                         String serverName,
                         String serverVersion) {
        this.mapper = mapper;
        this.tools = tools;
        this.resources = resources;
        this.toolInvoker = toolInvoker;
        this.resourceInvoker = resourceInvoker;
        this.serverName = serverName;
        this.serverVersion = serverVersion;
    }

    public JsonRpcResponse dispatch(JsonRpcRequest request, AuthContext auth) {
        if (request.isNotification()) {
            return null;
        }
        try {
            return JsonRpcResponse.ok(request.id(), handle(request, auth));
        } catch (McpException e) {
            log.debug("MCP error for {}: {}", request.method(), e.getMessage());
            return JsonRpcResponse.fail(request.id(), e.toError());
        } catch (Exception e) {
            log.warn("Internal error dispatching {}", request.method(), e);
            return JsonRpcResponse.fail(request.id(),
                    JsonRpcError.of(JsonRpcError.INTERNAL_ERROR, e.getMessage()));
        }
    }

    private Object handle(JsonRpcRequest request, AuthContext auth) {
        String method = request.method();
        return switch (method) {
            case McpMethod.INITIALIZE -> initialize();
            case McpMethod.PING -> Map.of();
            case McpMethod.TOOLS_LIST -> Map.of("tools", tools.describe());
            case McpMethod.TOOLS_CALL -> callTool(request.params(), auth);
            case McpMethod.RESOURCES_LIST -> Map.of("resources", resources.describe());
            case McpMethod.RESOURCES_READ -> readResource(request.params(), auth);
            default -> throw new McpException(JsonRpcError.METHOD_NOT_FOUND,
                    "Unsupported method: " + method);
        };
    }

    private InitializeResult initialize() {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Map.of("listChanged", false));
        capabilities.put("resources", Map.of("subscribe", false, "listChanged", false));
        return new InitializeResult(
                McpMethod.PROTOCOL_VERSION,
                capabilities,
                new InitializeResult.ServerInfo(serverName, serverVersion));
    }

    private ToolCallResult callTool(JsonNode params, AuthContext auth) {
        if (params == null || !params.isObject()) {
            throw new McpException(JsonRpcError.INVALID_PARAMS, "tools/call requires params object");
        }
        String name = textField(params, "name");
        if (name == null) {
            throw new McpException(JsonRpcError.INVALID_PARAMS, "tools/call requires 'name'");
        }
        RegisteredTool tool = tools.require(name);
        if (!auth.hasAnyRole(tool.roles())) {
            throw new McpException(JsonRpcError.FORBIDDEN, "Caller lacks required role for " + name);
        }
        JsonNode arguments = params.has("arguments") ? params.get("arguments") : mapper.createObjectNode();
        Object result = toolInvoker.invoke(tool, arguments);
        if (result instanceof ToolCallResult tcr) {
            return tcr;
        }
        if (result instanceof List<?> list && list.stream().allMatch(o -> o instanceof Content)) {
            @SuppressWarnings("unchecked")
            List<Content> typed = (List<Content>) list;
            return ToolCallResult.ok(typed);
        }
        return ToolCallResult.ok(List.of(Content.text(stringify(result))));
    }

    private Map<String, Object> readResource(JsonNode params, AuthContext auth) {
        if (params == null || !params.isObject()) {
            throw new McpException(JsonRpcError.INVALID_PARAMS, "resources/read requires params object");
        }
        String uri = textField(params, "uri");
        if (uri == null) {
            throw new McpException(JsonRpcError.INVALID_PARAMS, "resources/read requires 'uri'");
        }
        ResourceRegistry.Match match = resources.resolve(uri);
        RegisteredResource resource = match.resource();
        if (!auth.hasAnyRole(resource.roles())) {
            throw new McpException(JsonRpcError.FORBIDDEN, "Caller lacks required role for " + uri);
        }
        Object body = resourceInvoker.invoke(match);

        ObjectNode contentNode = mapper.createObjectNode();
        contentNode.put("uri", uri);
        contentNode.put("mimeType", resource.descriptor().mimeType());
        contentNode.put("text", stringify(body));
        return Map.of("contents", List.of(contentNode));
    }

    private static String textField(JsonNode node, String name) {
        JsonNode v = node.get(name);
        return v == null || v.isNull() ? null : v.asText();
    }

    private String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
