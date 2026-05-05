package dev.dylanott.mcp.transport;

import dev.dylanott.mcp.protocol.JsonRpcRequest;
import dev.dylanott.mcp.protocol.JsonRpcResponse;
import dev.dylanott.mcp.server.AuthContext;
import dev.dylanott.mcp.server.McpDispatcher;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/mcp")
public class McpHttpController {

    private final McpDispatcher dispatcher;

    public McpHttpController(McpDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<JsonRpcResponse> handle(@RequestBody JsonRpcRequest request,
                                        ServerWebExchange exchange) {
        AuthContext auth = exchange.getAttributeOrDefault(
                AuthContext.class.getName(), AuthContext.ANONYMOUS);
        return Mono.fromCallable(() -> dispatcher.dispatch(request, auth));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream() {
        ServerSentEvent<String> ready = ServerSentEvent.<String>builder()
                .event("ready")
                .data("{}")
                .build();
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(i -> ServerSentEvent.<String>builder()
                        .event("ping")
                        .data("{\"i\":" + i + "}")
                        .build());
        return Flux.concat(Flux.just(ready), heartbeat);
    }
}
