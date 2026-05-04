package dev.dylanott.mcp.server;

import dev.dylanott.mcp.protocol.JsonRpcError;
import dev.dylanott.mcp.protocol.Resource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;

public class ResourceRegistry {

    private final List<RegisteredResource> resources = new ArrayList<>();

    public synchronized void register(RegisteredResource resource) {
        for (RegisteredResource existing : resources) {
            if (existing.uri().equals(resource.uri())) {
                throw new IllegalStateException("Duplicate MCP resource uri: " + resource.uri());
            }
        }
        resources.add(resource);
    }

    public Match resolve(String uri) {
        for (RegisteredResource resource : resources) {
            Matcher m = resource.match(uri);
            if (m.matches()) {
                return new Match(resource, m);
            }
        }
        throw new McpException(JsonRpcError.RESOURCE_NOT_FOUND, "Unknown resource: " + uri);
    }

    public List<Resource> describe() {
        return resources.stream().map(RegisteredResource::descriptor).toList();
    }

    public Collection<RegisteredResource> all() {
        return resources;
    }

    public int size() {
        return resources.size();
    }

    public record Match(RegisteredResource resource, Matcher matcher) {
    }
}
