package dev.dylanott.mcp.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonRpcRoundTripTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesRequestWithStringId() throws Exception {
        String json = """
                {"jsonrpc":"2.0","id":"abc","method":"tools/list","params":{}}
                """;
        JsonRpcRequest req = mapper.readValue(json, JsonRpcRequest.class);
        assertThat(req.method()).isEqualTo("tools/list");
        assertThat(req.id()).isEqualTo("abc");
        assertThat(req.isNotification()).isFalse();
    }

    @Test
    void parsesRequestWithNumericId() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"ping\"}";
        JsonRpcRequest req = mapper.readValue(json, JsonRpcRequest.class);
        assertThat(((Number) req.id()).intValue()).isEqualTo(42);
    }

    @Test
    void notificationHasNullId() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
        JsonRpcRequest req = mapper.readValue(json, JsonRpcRequest.class);
        assertThat(req.isNotification()).isTrue();
    }

    @Test
    void serialisesSuccessResponse() throws Exception {
        ObjectNode tools = mapper.createObjectNode();
        tools.putArray("tools");
        JsonRpcResponse response = JsonRpcResponse.ok(1, Map.of("tools", List.of()));
        JsonNode tree = mapper.valueToTree(response);
        assertThat(tree.get("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(tree.get("id").asInt()).isEqualTo(1);
        assertThat(tree.has("error")).isFalse();
    }

    @Test
    void serialisesErrorResponse() throws Exception {
        JsonRpcResponse response = JsonRpcResponse.fail(1,
                JsonRpcError.of(JsonRpcError.METHOD_NOT_FOUND, "no such method"));
        JsonNode tree = mapper.valueToTree(response);
        assertThat(tree.has("result")).isFalse();
        assertThat(tree.get("error").get("code").asInt()).isEqualTo(JsonRpcError.METHOD_NOT_FOUND);
        assertThat(tree.get("error").get("message").asText()).isEqualTo("no such method");
    }
}
