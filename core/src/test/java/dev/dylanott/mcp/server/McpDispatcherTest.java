package dev.dylanott.mcp.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dylanott.mcp.annotation.MCPParam;
import dev.dylanott.mcp.annotation.MCPTool;
import dev.dylanott.mcp.protocol.JsonRpcError;
import dev.dylanott.mcp.protocol.JsonRpcRequest;
import dev.dylanott.mcp.protocol.JsonRpcResponse;
import dev.dylanott.mcp.protocol.McpMethod;
import dev.dylanott.mcp.schema.JsonSchemaGenerator;
import dev.dylanott.mcp.server.invoker.ResourceInvoker;
import dev.dylanott.mcp.server.invoker.ToolInvoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class McpDispatcherTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private ToolRegistry tools;
    private ResourceRegistry resources;
    private McpDispatcher dispatcher;
    private FakeBean bean;

    @BeforeEach
    void setUp() throws Exception {
        tools = new ToolRegistry();
        resources = new ResourceRegistry();
        bean = new FakeBean();

        JsonSchemaGenerator generator = new JsonSchemaGenerator(mapper);
        Method add = FakeBean.class.getDeclaredMethod("add", int.class, int.class);
        Method secret = FakeBean.class.getDeclaredMethod("secret");
        tools.register(new RegisteredTool(
                new dev.dylanott.mcp.protocol.Tool("add", "add two ints", generator.generate(add)),
                bean, add, new String[0]));
        tools.register(new RegisteredTool(
                new dev.dylanott.mcp.protocol.Tool("secret", "needs role", generator.generate(secret)),
                bean, secret, new String[]{"admin"}));

        dispatcher = new McpDispatcher(mapper, tools, resources,
                new ToolInvoker(mapper),
                new ResourceInvoker(),
                "test-server", "0.0.1");
    }

    @Test
    void initializeReturnsServerInfoAndProtocol() {
        JsonRpcRequest req = new JsonRpcRequest("2.0", 1, McpMethod.INITIALIZE, null);
        JsonRpcResponse res = dispatcher.dispatch(req, AuthContext.ANONYMOUS);
        JsonNode tree = mapper.valueToTree(res.result());
        assertThat(tree.get("protocolVersion").asText()).isEqualTo(McpMethod.PROTOCOL_VERSION);
        assertThat(tree.get("serverInfo").get("name").asText()).isEqualTo("test-server");
    }

    @Test
    void toolsListReturnsRegisteredTools() {
        JsonRpcRequest req = new JsonRpcRequest("2.0", 1, McpMethod.TOOLS_LIST, null);
        JsonRpcResponse res = dispatcher.dispatch(req, AuthContext.ANONYMOUS);
        JsonNode tree = mapper.valueToTree(res.result());
        JsonNode toolNames = tree.get("tools");
        assertThat(toolNames).hasSize(2);
        assertThat(toolNames.get(0).get("name").asText()).isEqualTo("add");
    }

    @Test
    void toolsCallInvokesBean() {
        ObjectNode params = mapper.createObjectNode();
        params.put("name", "add");
        ObjectNode args = params.putObject("arguments");
        args.put("a", 2);
        args.put("b", 3);

        JsonRpcRequest req = new JsonRpcRequest("2.0", 7, McpMethod.TOOLS_CALL, params);
        JsonRpcResponse res = dispatcher.dispatch(req, AuthContext.ANONYMOUS);
        JsonNode tree = mapper.valueToTree(res.result());
        JsonNode content = tree.get("content");
        assertThat(content.get(0).get("type").asText()).isEqualTo("text");
        assertThat(content.get(0).get("text").asText()).isEqualTo("5");
    }

    @Test
    void toolsCallRejectsCallerWithoutRole() {
        ObjectNode params = mapper.createObjectNode();
        params.put("name", "secret");
        params.putObject("arguments");

        JsonRpcRequest req = new JsonRpcRequest("2.0", 1, McpMethod.TOOLS_CALL, params);
        JsonRpcResponse res = dispatcher.dispatch(req, AuthContext.of("user", Set.of("reader")));
        assertThat(res.error().code()).isEqualTo(JsonRpcError.FORBIDDEN);
    }

    @Test
    void toolsCallAcceptsCallerWithRole() {
        ObjectNode params = mapper.createObjectNode();
        params.put("name", "secret");
        params.putObject("arguments");

        JsonRpcRequest req = new JsonRpcRequest("2.0", 1, McpMethod.TOOLS_CALL, params);
        JsonRpcResponse res = dispatcher.dispatch(req, AuthContext.of("admin", Set.of("admin")));
        assertThat(res.error()).isNull();
    }

    @Test
    void unknownMethodReturnsMethodNotFound() {
        JsonRpcRequest req = new JsonRpcRequest("2.0", 1, "totally/made/up", null);
        JsonRpcResponse res = dispatcher.dispatch(req, AuthContext.ANONYMOUS);
        assertThat(res.error().code()).isEqualTo(JsonRpcError.METHOD_NOT_FOUND);
    }

    @Test
    void notificationProducesNoResponse() {
        JsonRpcRequest req = new JsonRpcRequest("2.0", null, McpMethod.INITIALIZED_NOTIFICATION, null);
        assertThat(dispatcher.dispatch(req, AuthContext.ANONYMOUS)).isNull();
    }

    @SuppressWarnings("unused")
    static class FakeBean {
        @MCPTool(name = "add")
        public int add(@MCPParam int a, @MCPParam int b) {
            return a + b;
        }

        @MCPTool(name = "secret", roles = {"admin"})
        public String secret() {
            return "ok";
        }
    }
}
