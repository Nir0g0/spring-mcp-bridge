# Spring MCP Bridge

[![CI](https://github.com/Nir0g0/spring-mcp-bridge/actions/workflows/ci.yml/badge.svg)](https://github.com/Nir0g0/spring-mcp-bridge/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)

A Spring Boot library that turns any Spring application into an MCP (Model Context Protocol) server. Annotate a `@Service` method with `@MCPTool` and an LLM agent can call it. Annotate it with `@MCPResource` and the result becomes an MCP resource. Stdio and HTTP/SSE transports both ship in the box. JWT auth too.

I built this because most MCP server work today happens in Python. That is fine for greenfield projects, but the largest enterprise codebases (banks, automakers, telcos, hospitals) run on Java. When those teams want to expose internal tools to LLM agents, "rewrite a thin Python wrapper around our Spring services" is the wrong shape. This is the right shape: in-JVM, in-Spring, behind the same Spring Security filters everything else lives behind.

The repo contains the library (`core/`) and a working reference application (`example/`) that you can clone, run with `docker compose up`, and point an MCP client at.

## Quickstart

```bash
cp .env.example .env
docker compose up --build
```

- `http://localhost:8080/mcp`: JSON-RPC endpoint (POST)
- `http://localhost:8080/mcp/stream`: SSE channel (GET)

The example ships with three tools (`echo`, `math.add`, `math.fibonacci`, `time.now`) and two resources (`db://customers/{region}`, `db://customers/{id}/invoices`) backed by a Postgres-seeded customer/invoice schema. JWT is on by default; the next section shows how to mint a token.

## Calling a tool

The wire protocol is JSON-RPC 2.0 over HTTP. With JWT enabled (the default):

```bash
TOKEN=$(python -c '
import jwt, time
print(jwt.encode(
  {"iss": "spring-mcp-bridge-example", "sub": "demo",
   "roles": ["analyst"], "exp": int(time.time())+300},
  "development-secret-change-me-please-32+bytes", algorithm="HS256"))
')

curl -s http://localhost:8080/mcp \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | jq
```

Result:

```json
{
  "jsonrpc": "2.0", "id": 1,
  "result": {
    "tools": [
      {"name": "echo",     "description": "Return the input string verbatim", "inputSchema": {...}},
      {"name": "math.add", "description": "Add two integers and return the sum", "inputSchema": {...}},
      ...
    ]
  }
}
```

`tools/call` and `resources/read` follow the same pattern. There is nothing Spring-specific on the wire; any MCP client (the official Inspector, Claude Desktop, your own bot) can talk to it.

## Adding your own tool

Drop the dependency in any Spring Boot 3.3+ project:

```xml
<dependency>
    <groupId>dev.dylanott</groupId>
    <artifactId>spring-mcp-bridge-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Annotate a bean method:

```java
@Component
public class WeatherTool {

    private final WeatherClient client;

    public WeatherTool(WeatherClient client) { this.client = client; }

    @MCPTool(name = "weather.lookup",
             description = "Get current conditions for a city",
             roles = {"reader"})
    public WeatherReport lookup(@MCPParam(description = "City name") String city) {
        return client.fetch(city);
    }
}
```

That is the whole integration. The autoconfiguration picks up the bean at startup, generates a JSON Schema from the parameter list, registers the tool, and wires it into the dispatcher. Calls reach `WeatherTool.lookup` reflectively, return values get marshalled back to MCP `Content` with Jackson.

## Adding a SQL-backed resource

```java
@Component
public class CustomerResources {

    @MCPResource(
        uri = "db://customers/{region}",
        description = "Active customers in a region",
        query = "SELECT id, name, signup_date FROM customer "
              + "WHERE region = :region ORDER BY signup_date DESC",
        roles = {"analyst"})
    public void customersInRegion() {}  // body intentionally empty
}
```

The bridge sees `query=...` and routes the resource through `DatabaseResourceProvider`, which uses your application's `DataSource` and `NamedParameterJdbcTemplate`. URI placeholders (`{region}`) become named parameters bound to the SQL. Rows come back as JSON.

If you would rather write the query in Java, drop the `query` attribute and put your own logic in the method body. Anything you return gets serialised.

## Architecture

```
                          your Spring Boot application
              ┌────────────────────────────────────────────────┐
              │                                                │
 stdio client ─▶│ StdioTransport ──┐                          │
              │                   ├──▶ McpDispatcher ──┐      │
 HTTP client ──▶│ McpHttpController┘                    │      │
              │       ▲                                 │      │
              │       │                                 ▼      │
              │  McpAuthWebFilter             ┌─────────────────┐
              │   (JWT, roles)                │  ToolRegistry   │
              │                               │ ResourceRegistry│
              │                               └────────┬────────┘
              │                                        │
              │                          @MCPTool / @MCPResource
              │                                        │
              │                                        ▼
              │                              ┌──────────────────┐
              │                              │  user beans      │
              │                              │  + JdbcTemplate  │
              │                              └──────────────────┘
              └────────────────────────────────────────────────┘
```

The dispatcher handles `initialize`, `tools/list`, `tools/call`, `resources/list`, `resources/read`, and `ping`. Stdio mode is a single thread reading line-delimited JSON; SSE mode is a Spring WebFlux controller. Both share one dispatcher, one registry, one set of beans.

## What is in the box

| Piece | Where |
|---|---|
| JSON-RPC 2.0 envelopes + MCP method routing | `core/src/main/java/dev/dylanott/mcp/protocol/`, `server/McpDispatcher.java` |
| `@MCPTool` annotation + scanner | `annotation/MCPTool.java`, `server/McpBeanScanner.java` |
| JSON Schema generation from method parameters | `schema/JsonSchemaGenerator.java` |
| `@MCPResource` with SQL or Java backing | `annotation/MCPResource.java`, `db/DatabaseResourceProvider.java` |
| Stdio transport | `transport/StdioTransport.java`, `StdioRunner.java` |
| HTTP + SSE transport (Spring WebFlux) | `transport/McpHttpController.java` |
| JWT auth filter | `security/JwtAuthenticator.java`, `McpAuthWebFilter.java` |
| Spring Boot auto-configuration | `autoconfigure/McpAutoConfiguration.java` |
| Reference application (Postgres + JWT + 4 tools + 2 resources) | `example/` |

## Configuration

Properties live under `spring.mcp.*`:

```yaml
spring:
  mcp:
    transport: sse              # sse | stdio | both | none
    server-name: my-service
    server-version: 1.4.2
    security:
      enabled: true
      jwt:
        secret: ${JWT_SECRET}   # at least 32 bytes for HS256
        issuer: my-idp
        roles-claim: roles      # JWT claim that holds the role list
```

Set `transport: none` to use the bridge as a pure library and drive the dispatcher yourself.

## Running tests

```bash
mvn -B verify
```

Goes green on a fresh checkout. The Postgres integration test is annotated `@Testcontainers(disabledWithoutDocker = true)` so it is skipped automatically when Docker is not available, which is useful in CI runners that do not have it. With Docker, four extra integration cases run against a real `postgres:16-alpine`.

Test count at last run: 35 in `core/`, 5 in `example/` (plus 4 conditionally skipped without Docker). Coverage hits the dispatcher, both transports, both annotations, JWT (valid / expired / wrong issuer / missing role / short secret), the SQL resource path against H2, and an end-to-end SSE call against the reference app.

## What this does not do

- It is not an MCP **client**. There are good ones already.
- Tool calls return a single result. If you need streaming partial output, model it as a paginated resource.
- Auth is JWT only. mTLS or API-key auth is a fifty-line `WebFilter` away. I just have not needed it.

## Why I made the design choices I did

- **Maven, not Gradle.** The target audience (enterprise Java shops, especially in China and central Europe) is overwhelmingly on Maven. Defaulting to Gradle would push them away.
- **WebFlux for the HTTP layer, servlet-friendly elsewhere.** SSE on the servlet stack is awkward; on WebFlux it is two lines. The rest of the library does not care which stack you use.
- **JJWT instead of Spring Cloud OAuth.** I wanted a small dependency footprint. JJWT is two transitive jars; bringing in the full OAuth stack would be three or four times that.
- **Reflective dispatch over a code generator.** A code generator would be faster, but the runtime overhead of a reflective call on a tool invocation is rounding error compared to whatever the LLM and its network round trip cost.

## Local dev

```bash
mvn -B install -DskipTests        # build both modules
mvn -B -pl example spring-boot:run
```

The example reads `spring.datasource.url` from environment variables (see `.env.example`). Without a real Postgres handy, set `spring.sql.init.mode=never` and override the datasource to H2 with `MODE=PostgreSQL`.

## License

MIT.
