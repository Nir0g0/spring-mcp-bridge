package dev.dylanott.mcp.example;

import dev.dylanott.mcp.server.ResourceRegistry;
import dev.dylanott.mcp.server.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ExampleApplicationContextTest {

    @Autowired
    private ToolRegistry tools;

    @Autowired
    private ResourceRegistry resources;

    @Test
    void registersExampleToolsAndResources() {
        assertThat(tools.describe()).extracting("name")
                .contains("echo", "math.add", "math.fibonacci", "time.now");

        assertThat(resources.describe()).extracting("uri")
                .contains("db://customers/{region}", "db://customers/{id}/invoices");
    }
}
