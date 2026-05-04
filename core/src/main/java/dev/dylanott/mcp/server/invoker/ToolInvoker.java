package dev.dylanott.mcp.server.invoker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dylanott.mcp.protocol.JsonRpcError;
import dev.dylanott.mcp.server.McpException;
import dev.dylanott.mcp.server.RegisteredTool;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class ToolInvoker {

    private final ObjectMapper mapper;

    public ToolInvoker(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Object invoke(RegisteredTool tool, JsonNode arguments) {
        Method method = tool.method();
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter p = parameters[i];
            JsonNode value = arguments == null ? null : arguments.get(p.getName());
            if (value == null || value.isNull()) {
                args[i] = defaultValue(p.getType());
                continue;
            }
            try {
                args[i] = mapper.treeToValue(value, p.getType());
            } catch (Exception e) {
                throw new McpException(JsonRpcError.INVALID_PARAMS,
                        "Cannot bind argument '" + p.getName() + "': " + e.getMessage());
            }
        }

        try {
            method.setAccessible(true);
            return method.invoke(tool.bean(), args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new McpException(JsonRpcError.INTERNAL_ERROR,
                    "Tool '" + tool.name() + "' threw " + cause.getClass().getSimpleName()
                            + ": " + cause.getMessage(), cause);
        } catch (IllegalAccessException e) {
            throw new McpException(JsonRpcError.INTERNAL_ERROR,
                    "Cannot invoke tool '" + tool.name() + "': " + e.getMessage());
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0d;
        if (type == float.class) return 0.0f;
        return 0;
    }
}
