package dev.dylanott.mcp.example.tools;

import dev.dylanott.mcp.annotation.MCPParam;
import dev.dylanott.mcp.annotation.MCPTool;
import org.springframework.stereotype.Component;

@Component
public class EchoTool {

    @MCPTool(name = "echo", description = "Return the input string verbatim")
    public String echo(@MCPParam(description = "Text to echo back") String message) {
        return message;
    }
}
