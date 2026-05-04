# Spring MCP Bridge Spec

## Problem

The MCP (Model Context Protocol) ecosystem is overwhelmingly Python-first. The official Python SDK is mature, FastMCP is on the Thoughtworks Tech Radar, and most demos and reference servers are Python. Meanwhile the largest enterprise codebases (banks, telcos, automakers, hospitals) run on Java. They have decades of business logic in Spring Boot services backed by Oracle, Postgres, and JEE legacy systems. When those companies want to expose their internal tools to LLM agents, the answer today is "rewrite a thin Python wrapper that calls our Java services over HTTP." That is the wrong shape. It doubles the surface area, splits ownership, and skips Java's type system and its existing security stack.

I built this at Audi in Python because that is what existed at the time. A Java-native MCP bridge is what I would have built if it had been an option.

## Solution

Spring MCP Bridge is a Spring Boot library that turns any Spring application into an MCP server. Annotate a method with `@MCPTool` and it becomes a tool an LLM agent can call. Annotate a SQL query with `@MCPResource` and its rows become an MCP resource. Add the starter to your `pom.xml`, set a couple of properties, and the application speaks the full MCP protocol over stdio (for local agents) and SSE (for HTTP clients), with JWT auth bolted on through Spring Security.

The shipped reference application is a plain Spring Boot app. It is the documentation.

## User stories

- As a Java backend engineer, I want to expose a Spring `@Service` method as an MCP tool by adding one annotation, so I do not have to write a Python sidecar.
- As a security engineer, I want every MCP request to be validated against a JWT issued by my existing identity provider, so MCP tool access lives inside the same auth model as my REST endpoints.
- As a DBA, I want to expose a SELECT query as a read-only MCP resource without writing a Java method, so analysts can pull controlled data through an LLM agent.
- As an SRE, I want the MCP server to run inside the same JVM as my service, with the same observability stack (Micrometer, Spring Boot Actuator), so I do not have a second runtime to monitor.
- As a developer, I want a sample app with a Postgres-backed resource and three example tools that runs with `docker compose up`, so I can copy and modify it.

## Architecture

```
                           Spring Boot Application
                  ┌────────────────────────────────────────┐
                  │                                        │
  MCP client ─stdio▶│  StdioTransport ─┐                   │
  (Claude, etc.)  │                    ├▶ McpDispatcher    │
                  │                    │       │           │
  MCP client ─SSE──▶│  SseController ──┘       │           │
  (HTTP)          │       ▲                    │           │
                  │       │                    ▼           │
                  │  JwtAuthFilter   ┌─────────────────┐   │
                  │       │          │  ToolRegistry   │   │
                  │       │          │ ResourceRegistry│   │
                  │       │          └────────┬────────┘   │
                  │       │                   │            │
                  │       │           reflective dispatch  │
                  │       │                   ▼            │
                  │       │     ┌─────────────────────┐    │
                  │       │     │  @MCPTool methods   │    │
                  │       │     │  @MCPResource SQL   │    │
                  │       │     │  on user beans      │    │
                  │       │     └──────────┬──────────┘    │
                  │       │                │               │
                  │       │                ▼               │
                  │       │            JDBC ──▶ Postgres / Oracle
                  └────────────────────────────────────────┘
```

**Components:**
- `protocol/`: JSON-RPC 2.0 envelopes plus MCP-specific message types (initialize, tools/list, tools/call, resources/list, resources/read).
- `server/McpDispatcher`: central handler. Maps incoming method names to registry lookups, calls the matching tool or resource, wraps the result.
- `server/ToolRegistry`, `server/ResourceRegistry`: discovered at startup by a `BeanPostProcessor` scanning for `@MCPTool` / `@MCPResource`.
- `transport/StdioTransport`: line-delimited JSON over `System.in` / `System.out`, started via `ApplicationRunner` when `spring.mcp.transport=stdio` (or both).
- `transport/SseController`: Spring WebFlux controller exposing `POST /mcp` (request) and `GET /mcp/stream` (SSE event source).
- `schema/JsonSchemaGenerator`: turns a method's parameter list into a JSON Schema object so `tools/list` returns useful schemas.
- `db/DatabaseResourceProvider`: runs the SQL behind `@MCPResource`, returns rows as MCP resource content.
- `security/JwtAuthFilter`: Spring Security filter validating the bearer token; enforces `roles` claim against `@MCPTool(roles = ...)`.
- `autoconfigure/McpAutoConfiguration`: wires everything together. Discoverable via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

## Tech stack

- **Java 21**: virtual threads, records, pattern matching. Spring Boot 3.3 baseline.
- **Spring Boot 3.3 + Spring WebFlux**: WebFlux for the SSE side; the rest of the app can still be servlet-based.
- **Spring Security**: existing primitive for filter chains and JWT validation. No reason to roll my own.
- **Jackson**: shipped with Spring Boot; used for JSON-RPC marshalling.
- **JJWT (`io.jsonwebtoken:jjwt`)**: HS256 / RS256 JWT validation without dragging in the rest of Spring Cloud OAuth.
- **HikariCP + JDBC**: Spring Boot defaults; explicit `JdbcTemplate` for resource queries (no JPA, since that is overkill).
- **JUnit 5 + Testcontainers + WebTestClient**: contract-style tests against a real Postgres for the resource path.
- **Maven**: chosen over Gradle because the target audience (enterprise Java shops) is overwhelmingly on Maven.

## Data model

There is no persistent state owned by the bridge itself. Tools are stateless method calls; resources are SQL queries declared in user code. The example app uses two tables to make the resource demo concrete:

```sql
CREATE TABLE customer (
  id          BIGSERIAL PRIMARY KEY,
  name        TEXT NOT NULL,
  region      TEXT NOT NULL,
  signup_date DATE NOT NULL
);

CREATE TABLE invoice (
  id          BIGSERIAL PRIMARY KEY,
  customer_id BIGINT NOT NULL REFERENCES customer(id),
  amount_cents BIGINT NOT NULL,
  issued_at   TIMESTAMPTZ NOT NULL
);
```

A method-level annotation declares the resource:

```java
@MCPResource(
  uri = "db://customers/{region}",
  description = "Customers in a region",
  query = "SELECT id, name, signup_date FROM customer WHERE region = :region")
public List<Map<String,Object>> customersByRegion(String region) { /* ... */ }
```

## API / interface

The library surface is annotations and one configuration prefix:

- `@MCPTool(name, description, roles)` on any Spring bean method.
- `@MCPResource(uri, description, query)` on any Spring bean method.
- `@MCPParam(description)` on parameters, surfaced in the tool schema.
- `spring.mcp.transport` = `stdio` | `sse` | `both` (default `sse`).
- `spring.mcp.security.enabled` = `true` | `false` (default `true`).
- `spring.mcp.security.jwt.secret` / `...issuer` / `...public-key`: at least one required when security is on.

The wire interface is the MCP spec, so an LLM agent does not see Spring at all.

## Out of scope

- Implementing an MCP **client** (this is server-side only; client implementations exist already).
- Long-running tool calls with streaming partial output. Tools return one result. If you need streaming, expose it as a resource with pagination.
- Auth schemes other than JWT. mTLS and API-key auth are easy to add by writing another `WebFilter`; the library ships with JWT only.
- A web UI. Operators can use any MCP inspector.

## Success criteria

- A Spring Boot developer can annotate a method, run `mvn spring-boot:run`, and have it appear in an MCP client like the official MCP Inspector, without writing any protocol code.
- Tests pass against a real Postgres via Testcontainers, not a mock.
- The example app starts in under 10 seconds and serves a `tools/list` request returning at least three tools and one resource.
- `mvn verify` is green from a clean checkout with no manual setup beyond Docker.

## Milestones

1. **Scaffold.** Parent POM, `core/` and `example/` modules, `mvnw` wrapper, base `.gitignore`, `.env.example`, empty `docker-compose.yml`.
2. **Protocol types.** JSON-RPC envelopes, MCP method enum, `Tool`, `Resource`, `Content` records. Round-trip tests.
3. **Dispatcher + registries.** `ToolRegistry`, `ResourceRegistry`, `McpDispatcher` handling `initialize`, `tools/list`, `tools/call`, `resources/list`, `resources/read`. Unit tests.
4. **Stdio transport.** `ApplicationRunner` reading line-delimited JSON, dispatching, writing replies. Loop test against in-memory streams.
5. **SSE transport.** Spring WebFlux controller with `POST /mcp` and `GET /mcp/stream`. Reactive end-to-end test with `WebTestClient`.
6. **`@MCPTool` annotation + scanner.** `BeanPostProcessor` collecting annotated methods, JSON Schema generator from parameter types, reflective invocation in dispatcher.
7. **`@MCPResource` annotation + DB provider.** Annotation, `DatabaseResourceProvider` using `JdbcTemplate`, named-parameter binding, JSON serialisation of rows.
8. **JWT auth.** `JwtAuthFilter`, `McpSecurityProperties`, role check at dispatch time. Tests for valid / expired / wrong-role tokens.
9. **Auto-configuration.** `McpAutoConfiguration`, `AutoConfiguration.imports`, conditional beans on properties.
10. **Reference app.** `example/` module: three `@MCPTool` beans (echo, math, http-fetch), one `@MCPResource` bean (customer queries), `application.yml`, sample `schema.sql`/`data.sql`.
11. **Testcontainers integration test.** Full app boot with a real Postgres, `tools/call` and `resources/read` over the SSE transport, JWT included.
12. **Docker + compose.** Multi-stage Dockerfile for the example, compose file with `postgres:16` and the example service.
13. **README.** What / why / quickstart / annotation cookbook / architecture / dev setup. Apply the natural-writing rules.
