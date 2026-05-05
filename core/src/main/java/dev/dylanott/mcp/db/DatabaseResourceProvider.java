package dev.dylanott.mcp.db;

import dev.dylanott.mcp.protocol.JsonRpcError;
import dev.dylanott.mcp.server.McpException;
import dev.dylanott.mcp.server.RegisteredResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public class DatabaseResourceProvider {

    private final NamedParameterJdbcTemplate jdbc;

    public DatabaseResourceProvider(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> query(RegisteredResource resource, Matcher uriMatch) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        Map<String, String> bindings = new LinkedHashMap<>();
        for (String name : resource.paramNames()) {
            String value;
            try {
                value = uriMatch.group(name);
            } catch (IllegalArgumentException e) {
                value = null;
            }
            bindings.put(name, value);
            params.addValue(name, value);
        }
        try {
            return jdbc.queryForList(resource.query(), params);
        } catch (Exception e) {
            throw new McpException(JsonRpcError.INTERNAL_ERROR,
                    "SQL resource '" + resource.uri() + "' failed: " + e.getMessage(), e);
        }
    }
}
