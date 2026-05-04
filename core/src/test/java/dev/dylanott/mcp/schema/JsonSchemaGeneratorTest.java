package dev.dylanott.mcp.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dylanott.mcp.annotation.MCPParam;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemaGeneratorTest {

    private final JsonSchemaGenerator generator = new JsonSchemaGenerator(new ObjectMapper());

    @Test
    void generatesSchemaForTypicalMethod() throws Exception {
        Method m = Sample.class.getDeclaredMethod("call", String.class, int.class, boolean.class);
        ObjectNode schema = generator.generate(m);
        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.get("properties").get("text").get("type").asText()).isEqualTo("string");
        assertThat(schema.get("properties").get("count").get("type").asText()).isEqualTo("integer");
        assertThat(schema.get("properties").get("flag").get("type").asText()).isEqualTo("boolean");
        assertThat(schema.get("properties").get("text").get("description").asText())
                .isEqualTo("text body");
    }

    @Test
    void marksOptionalParamsAsNotRequired() throws Exception {
        Method m = Sample.class.getDeclaredMethod("optional", String.class);
        ObjectNode schema = generator.generate(m);
        assertThat(schema.has("required")).isFalse();
    }

    @Test
    void mapsListToArray() throws Exception {
        Method m = Sample.class.getDeclaredMethod("listy", List.class);
        ObjectNode schema = generator.generate(m);
        assertThat(schema.get("properties").get("items").get("type").asText()).isEqualTo("array");
    }

    static class Sample {
        @SuppressWarnings("unused")
        public void call(@MCPParam(description = "text body") String text,
                         @MCPParam int count,
                         @MCPParam boolean flag) {
        }

        @SuppressWarnings("unused")
        public void optional(@MCPParam(required = false) String text) {
        }

        @SuppressWarnings("unused")
        public void listy(List<String> items) {
        }
    }
}
