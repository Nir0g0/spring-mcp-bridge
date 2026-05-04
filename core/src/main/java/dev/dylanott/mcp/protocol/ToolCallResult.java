package dev.dylanott.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolCallResult(List<Content> content, Boolean isError) {

    public static ToolCallResult ok(List<Content> content) {
        return new ToolCallResult(content, null);
    }

    public static ToolCallResult error(String message) {
        return new ToolCallResult(List.of(Content.text(message)), true);
    }
}
