package dev.dylanott.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dylanott.mcp.annotation.MCPResource;
import dev.dylanott.mcp.annotation.MCPTool;
import dev.dylanott.mcp.schema.JsonSchemaGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpBeanScannerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void scansAnnotatedToolMethods() {
        ToolRegistry tools = new ToolRegistry();
        ResourceRegistry resources = new ResourceRegistry();
        McpBeanScanner scanner = new McpBeanScanner(tools, resources, new JsonSchemaGenerator(mapper));

        Object bean = new HelloBean();
        scanner.scan(bean);

        assertThat(tools.size()).isEqualTo(1);
        assertThat(tools.require("greet").name()).isEqualTo("greet");
        assertThat(resources.size()).isEqualTo(1);
        assertThat(resources.describe().get(0).uri()).isEqualTo("hello://greet/{name}");
    }

    @Test
    void usesMethodNameWhenAnnotationNameIsBlank() {
        ToolRegistry tools = new ToolRegistry();
        McpBeanScanner scanner = new McpBeanScanner(tools, new ResourceRegistry(),
                new JsonSchemaGenerator(mapper));

        scanner.scan(new UnnamedBean());
        assertThat(tools.require("doStuff")).isNotNull();
    }

    @SuppressWarnings("unused")
    static class HelloBean {
        @MCPTool(name = "greet", description = "say hi")
        public String greet(String name) {
            return "hello " + name;
        }

        @MCPResource(uri = "hello://greet/{name}", description = "greeting resource")
        public String greetResource(String name) {
            return "hello " + name;
        }
    }

    @SuppressWarnings("unused")
    static class UnnamedBean {
        @MCPTool
        public String doStuff() {
            return "ok";
        }
    }
}
