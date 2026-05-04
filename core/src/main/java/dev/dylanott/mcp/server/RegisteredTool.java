package dev.dylanott.mcp.server;

import dev.dylanott.mcp.protocol.Tool;

import java.lang.reflect.Method;
import java.util.Arrays;

public record RegisteredTool(Tool descriptor, Object bean, Method method, String[] roles) {

    public RegisteredTool {
        roles = roles == null ? new String[0] : Arrays.copyOf(roles, roles.length);
    }

    public String name() {
        return descriptor.name();
    }
}
