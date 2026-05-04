package dev.dylanott.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dylanott.mcp.protocol.JsonRpcError;
import dev.dylanott.mcp.protocol.Tool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRegistryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void registersAndLooksUpTool() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        ObjectNode schema = mapper.createObjectNode();
        Tool descriptor = new Tool("echo", "echo", schema);
        registry.register(new RegisteredTool(descriptor, this,
                getClass().getDeclaredMethod("dummy"), new String[0]));
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.require("echo").name()).isEqualTo("echo");
    }

    @Test
    void rejectsDuplicateName() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        ObjectNode schema = mapper.createObjectNode();
        Tool descriptor = new Tool("echo", "echo", schema);
        var method = getClass().getDeclaredMethod("dummy");
        registry.register(new RegisteredTool(descriptor, this, method, new String[0]));
        assertThatThrownBy(() -> registry.register(
                new RegisteredTool(descriptor, this, method, new String[0])))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requireUnknownThrowsMethodNotFound() {
        ToolRegistry registry = new ToolRegistry();
        assertThatThrownBy(() -> registry.require("missing"))
                .isInstanceOf(McpException.class)
                .extracting(e -> ((McpException) e).code())
                .isEqualTo(JsonRpcError.METHOD_NOT_FOUND);
    }

    @SuppressWarnings("unused")
    private void dummy() {
    }
}
