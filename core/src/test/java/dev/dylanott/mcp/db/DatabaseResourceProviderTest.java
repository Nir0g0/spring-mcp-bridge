package dev.dylanott.mcp.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dylanott.mcp.annotation.MCPResource;
import dev.dylanott.mcp.schema.JsonSchemaGenerator;
import dev.dylanott.mcp.server.McpBeanScanner;
import dev.dylanott.mcp.server.RegisteredResource;
import dev.dylanott.mcp.server.ResourceRegistry;
import dev.dylanott.mcp.server.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseResourceProviderTest {

    private EmbeddedDatabase db;
    private DatabaseResourceProvider provider;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        JdbcTemplate jdbc = new JdbcTemplate(db);
        jdbc.execute("CREATE TABLE customer (id INT PRIMARY KEY, name VARCHAR(100), region VARCHAR(20))");
        jdbc.update("INSERT INTO customer VALUES (1, 'Alpha GmbH', 'EU-DE')");
        jdbc.update("INSERT INTO customer VALUES (2, 'Beta Robotics', 'CN-SOUTH')");
        jdbc.update("INSERT INTO customer VALUES (3, 'Gamma AG', 'EU-DE')");

        provider = new DatabaseResourceProvider(new NamedParameterJdbcTemplate(db));
    }

    @Test
    void runsParameterisedQueryAndReturnsRows() {
        ResourceRegistry registry = new ResourceRegistry();
        ToolRegistry tools = new ToolRegistry();
        new McpBeanScanner(tools, registry, new JsonSchemaGenerator(new ObjectMapper()))
                .scan(new SqlBean());

        ResourceRegistry.Match match = registry.resolve("db://customers/EU-DE");
        Matcher m = match.matcher();
        m.matches();

        RegisteredResource resource = match.resource();
        List<Map<String, Object>> rows = provider.query(resource, m);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(r -> r.get("NAME"))
                .containsExactlyInAnyOrder("Alpha GmbH", "Gamma AG");
    }

    @SuppressWarnings("unused")
    static class SqlBean {
        @MCPResource(uri = "db://customers/{region}",
                query = "SELECT name FROM customer WHERE region = :region")
        public void customers() {
        }
    }
}
