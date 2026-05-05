package dev.dylanott.mcp.example.tools;

import dev.dylanott.mcp.annotation.MCPParam;
import dev.dylanott.mcp.annotation.MCPTool;
import org.springframework.stereotype.Component;

@Component
public class MathTool {

    @MCPTool(name = "math.add", description = "Add two integers and return the sum")
    public long add(@MCPParam(description = "First addend") long a,
                    @MCPParam(description = "Second addend") long b) {
        return a + b;
    }

    @MCPTool(name = "math.fibonacci", description = "Return the nth Fibonacci number (n >= 0)")
    public long fibonacci(@MCPParam(description = "Index n, must be 0 or greater") int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be >= 0");
        }
        long a = 0;
        long b = 1;
        for (int i = 0; i < n; i++) {
            long next = a + b;
            a = b;
            b = next;
        }
        return a;
    }
}
