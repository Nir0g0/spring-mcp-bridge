package dev.dylanott.mcp.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dylanott.mcp.protocol.JsonRpcRequest;
import dev.dylanott.mcp.protocol.JsonRpcResponse;
import dev.dylanott.mcp.server.AuthContext;
import dev.dylanott.mcp.server.McpDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public class StdioTransport {

    private static final Logger log = LoggerFactory.getLogger(StdioTransport.class);

    private final McpDispatcher dispatcher;
    private final ObjectMapper mapper;

    public StdioTransport(McpDispatcher dispatcher, ObjectMapper mapper) {
        this.dispatcher = dispatcher;
        this.mapper = mapper;
    }

    public void serve(InputStream in, OutputStream out) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        PrintWriter writer = new PrintWriter(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8), true);
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            JsonRpcRequest request;
            try {
                request = mapper.readValue(line, JsonRpcRequest.class);
            } catch (Exception e) {
                log.warn("Stdio: malformed JSON-RPC frame: {}", e.getMessage());
                continue;
            }
            JsonRpcResponse response = dispatcher.dispatch(request, AuthContext.ANONYMOUS);
            if (response != null) {
                writer.println(mapper.writeValueAsString(response));
            }
        }
    }
}
