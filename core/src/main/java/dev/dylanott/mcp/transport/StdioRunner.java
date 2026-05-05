package dev.dylanott.mcp.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

public class StdioRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StdioRunner.class);

    private final StdioTransport transport;

    public StdioRunner(StdioTransport transport) {
        this.transport = transport;
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread t = new Thread(() -> {
            try {
                log.info("Spring MCP Bridge: stdio transport listening on stdin/stdout");
                transport.serve(System.in, System.out);
            } catch (Exception e) {
                log.error("Stdio transport crashed", e);
            }
        }, "mcp-stdio");
        t.setDaemon(true);
        t.start();
    }
}
