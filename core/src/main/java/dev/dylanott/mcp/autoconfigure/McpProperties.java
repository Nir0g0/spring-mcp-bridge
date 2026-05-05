package dev.dylanott.mcp.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.mcp")
public class McpProperties {

    public enum Transport {SSE, STDIO, BOTH, NONE}

    private Transport transport = Transport.SSE;
    private String serverName = "spring-mcp-bridge";
    private String serverVersion = "0.1.0";

    public Transport getTransport() {
        return transport;
    }

    public void setTransport(Transport transport) {
        this.transport = transport;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    public boolean stdioEnabled() {
        return transport == Transport.STDIO || transport == Transport.BOTH;
    }

    public boolean sseEnabled() {
        return transport == Transport.SSE || transport == Transport.BOTH;
    }
}
