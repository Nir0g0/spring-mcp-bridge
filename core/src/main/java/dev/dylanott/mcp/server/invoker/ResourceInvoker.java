package dev.dylanott.mcp.server.invoker;

import dev.dylanott.mcp.protocol.JsonRpcError;
import dev.dylanott.mcp.server.McpException;
import dev.dylanott.mcp.server.RegisteredResource;
import dev.dylanott.mcp.server.ResourceRegistry;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public class ResourceInvoker {

    public Object invoke(ResourceRegistry.Match match) {
        return invokeMethod(match.resource(), match.matcher());
    }

    private Object invokeMethod(RegisteredResource resource, Matcher matcher) {
        Method method = resource.method();
        Map<String, String> bindings = new LinkedHashMap<>();
        for (String name : resource.paramNames()) {
            try {
                bindings.put(name, matcher.group(name));
            } catch (IllegalArgumentException e) {
                bindings.put(name, null);
            }
        }
        Object[] args = new Object[method.getParameterCount()];
        var parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();
            Class<?> type = parameters[i].getType();
            if (Map.class.isAssignableFrom(type)) {
                args[i] = bindings;
            } else if (List.class.isAssignableFrom(type)) {
                args[i] = List.copyOf(bindings.values());
            } else if (type == String.class) {
                args[i] = bindings.get(paramName);
            } else {
                args[i] = coerce(bindings.get(paramName), type);
            }
        }
        try {
            method.setAccessible(true);
            return method.invoke(resource.bean(), args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new McpException(JsonRpcError.INTERNAL_ERROR,
                    "Resource '" + resource.uri() + "' threw " + cause.getClass().getSimpleName()
                            + ": " + cause.getMessage(), cause);
        } catch (IllegalAccessException e) {
            throw new McpException(JsonRpcError.INTERNAL_ERROR,
                    "Cannot invoke resource '" + resource.uri() + "': " + e.getMessage());
        }
    }

    private static Object coerce(String value, Class<?> type) {
        if (value == null) {
            return null;
        }
        if (type == String.class) return value;
        if (type == int.class || type == Integer.class) return Integer.parseInt(value);
        if (type == long.class || type == Long.class) return Long.parseLong(value);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
        if (type == double.class || type == Double.class) return Double.parseDouble(value);
        return value;
    }
}
