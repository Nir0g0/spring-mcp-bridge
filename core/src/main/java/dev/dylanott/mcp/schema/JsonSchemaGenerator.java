package dev.dylanott.mcp.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dylanott.mcp.annotation.MCPParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;

public final class JsonSchemaGenerator {

    private final ObjectMapper mapper;

    public JsonSchemaGenerator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectNode generate(Method method) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = mapper.createArrayNode();

        for (Parameter parameter : method.getParameters()) {
            String name = parameter.getName();
            ObjectNode property = properties.putObject(name);
            property.put("type", jsonType(parameter.getType()));

            MCPParam meta = parameter.getAnnotation(MCPParam.class);
            if (meta != null) {
                if (!meta.description().isEmpty()) {
                    property.put("description", meta.description());
                }
                if (meta.required()) {
                    required.add(name);
                }
            } else {
                required.add(name);
            }
        }

        if (!required.isEmpty()) {
            schema.set("required", required);
        }
        return schema;
    }

    private static String jsonType(Class<?> type) {
        if (type == String.class) {
            return "string";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class) {
            return "integer";
        }
        if (type == double.class || type == Double.class
                || type == float.class || type == Float.class) {
            return "number";
        }
        if (List.class.isAssignableFrom(type) || type.isArray()) {
            return "array";
        }
        if (Map.class.isAssignableFrom(type)) {
            return "object";
        }
        return "string";
    }
}
