package dev.dylanott.mcp.server;

import dev.dylanott.mcp.protocol.Resource;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record RegisteredResource(
        Resource descriptor,
        Object bean,
        Method method,
        String query,
        String[] roles,
        Pattern uriPattern,
        List<String> paramNames
) {

    public RegisteredResource {
        roles = roles == null ? new String[0] : Arrays.copyOf(roles, roles.length);
    }

    public String uri() {
        return descriptor.uri();
    }

    public Matcher match(String requestedUri) {
        return uriPattern.matcher(requestedUri);
    }
}
