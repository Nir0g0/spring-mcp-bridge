package dev.dylanott.mcp.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class McpEndpointTest {

    @Autowired
    private WebTestClient client;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void initializeReturnsServerInfo() {
        ObjectNode req = jsonRpc(1, "initialize");
        client.post().uri("/mcp").bodyValue(req).exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .value(n -> assertThat(n.get("result").get("serverInfo").get("name").asText())
                        .isEqualTo("spring-mcp-bridge-example"));
    }

    @Test
    void toolsListExposesExampleTools() {
        ObjectNode req = jsonRpc(2, "tools/list");
        client.post().uri("/mcp").bodyValue(req).exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .value(n -> {
                    JsonNode tools = n.get("result").get("tools");
                    assertThat(tools).isNotEmpty();
                    boolean hasFib = false;
                    for (JsonNode t : tools) {
                        if ("math.fibonacci".equals(t.get("name").asText())) {
                            hasFib = true;
                        }
                    }
                    assertThat(hasFib).as("math.fibonacci should be exposed").isTrue();
                });
    }

    @Test
    void mathAddInvokesTool() {
        ObjectNode req = jsonRpc(3, "tools/call");
        ObjectNode params = req.putObject("params");
        params.put("name", "math.add");
        ObjectNode args = params.putObject("arguments");
        args.put("a", 17);
        args.put("b", 25);

        client.post().uri("/mcp").bodyValue(req).exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .value(n -> assertThat(n.get("result").get("content").get(0).get("text").asText())
                        .isEqualTo("42"));
    }

    @Test
    void streamEmitsReadyEvent() {
        client.get().uri("/mcp/stream")
                .accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(org.springframework.http.MediaType.TEXT_EVENT_STREAM);
    }

    private ObjectNode jsonRpc(int id, String method) {
        ObjectNode root = mapper.createObjectNode();
        root.put("jsonrpc", "2.0");
        root.put("id", id);
        root.put("method", method);
        return root;
    }
}
