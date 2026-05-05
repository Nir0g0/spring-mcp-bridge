package dev.dylanott.mcp.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dylanott.mcp.annotation.MCPParam;
import dev.dylanott.mcp.annotation.MCPTool;
import dev.dylanott.mcp.schema.JsonSchemaGenerator;
import dev.dylanott.mcp.server.McpBeanScanner;
import dev.dylanott.mcp.server.McpDispatcher;
import dev.dylanott.mcp.server.ResourceRegistry;
import dev.dylanott.mcp.server.ToolRegistry;
import dev.dylanott.mcp.server.invoker.ResourceInvoker;
import dev.dylanott.mcp.server.invoker.ToolInvoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Optional;

class McpHttpControllerTest {

    private WebTestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ToolRegistry tools = new ToolRegistry();
        ResourceRegistry resources = new ResourceRegistry();
        new McpBeanScanner(tools, resources, new JsonSchemaGenerator(mapper))
                .scan(new MathBean());

        McpDispatcher dispatcher = new McpDispatcher(mapper, tools, resources,
                new ToolInvoker(mapper), new ResourceInvoker(Optional.empty()),
                "http-test", "0.0.1");

        client = WebTestClient.bindToController(new McpHttpController(dispatcher)).build();
    }

    @Test
    void postReturnsToolsListResult() {
        ObjectNode body = mapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", 1);
        body.put("method", "tools/list");

        client.post().uri("/mcp")
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .value(node -> {
                    org.assertj.core.api.Assertions.assertThat(node.get("id").asInt()).isEqualTo(1);
                    org.assertj.core.api.Assertions.assertThat(
                            node.get("result").get("tools").get(0).get("name").asText())
                            .isEqualTo("multiply");
                });
    }

    @Test
    void postReturnsToolCallResult() {
        ObjectNode body = mapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", 99);
        body.put("method", "tools/call");
        ObjectNode params = body.putObject("params");
        params.put("name", "multiply");
        ObjectNode args = params.putObject("arguments");
        args.put("a", 6);
        args.put("b", 7);

        client.post().uri("/mcp")
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .value(node -> org.assertj.core.api.Assertions.assertThat(
                        node.get("result").get("content").get(0).get("text").asText())
                        .isEqualTo("42"));
    }

    @SuppressWarnings("unused")
    static class MathBean {
        @MCPTool(name = "multiply")
        public long multiply(@MCPParam long a, @MCPParam long b) {
            return a * b;
        }
    }
}
