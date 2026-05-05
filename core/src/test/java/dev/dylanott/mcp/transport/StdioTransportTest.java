package dev.dylanott.mcp.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dylanott.mcp.annotation.MCPParam;
import dev.dylanott.mcp.annotation.MCPTool;
import dev.dylanott.mcp.schema.JsonSchemaGenerator;
import dev.dylanott.mcp.server.McpBeanScanner;
import dev.dylanott.mcp.server.McpDispatcher;
import dev.dylanott.mcp.server.ResourceRegistry;
import dev.dylanott.mcp.server.ToolRegistry;
import dev.dylanott.mcp.server.invoker.ResourceInvoker;
import dev.dylanott.mcp.server.invoker.ToolInvoker;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StdioTransportTest {

    @Test
    void respondsToToolsCallOverStdio() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolRegistry tools = new ToolRegistry();
        ResourceRegistry resources = new ResourceRegistry();
        new McpBeanScanner(tools, resources, new JsonSchemaGenerator(mapper))
                .scan(new EchoBean());

        McpDispatcher dispatcher = new McpDispatcher(mapper, tools, resources,
                new ToolInvoker(mapper), new ResourceInvoker(Optional.empty()),
                "stdio-test", "0.0.1");

        StdioTransport transport = new StdioTransport(dispatcher, mapper);

        String input = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hi"}}}
                """;
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transport.serve(in, out);

        String[] lines = out.toString(StandardCharsets.UTF_8).split("\\r?\\n");
        assertThat(lines).hasSize(2);

        JsonNode init = mapper.readTree(lines[0]);
        assertThat(init.get("id").asInt()).isEqualTo(1);
        assertThat(init.get("result").get("serverInfo").get("name").asText())
                .isEqualTo("stdio-test");

        JsonNode call = mapper.readTree(lines[1]);
        assertThat(call.get("id").asInt()).isEqualTo(2);
        assertThat(call.get("result").get("content").get(0).get("text").asText()).isEqualTo("hi");
    }

    @SuppressWarnings("unused")
    static class EchoBean {
        @MCPTool(name = "echo")
        public String echo(@MCPParam String message) {
            return message;
        }
    }
}
