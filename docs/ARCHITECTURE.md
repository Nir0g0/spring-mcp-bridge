# Architecture

This document covers the runtime structure of the bridge, the wire flow of an MCP request, and the rules each component sticks to. The README is the introduction; this is the reference.

## Module layout

```
spring-mcp-bridge/
├── core/                                 the library + Spring Boot starter
│   └── src/main/java/dev/dylanott/mcp/
│       ├── annotation/                   @MCPTool, @MCPResource, @MCPParam
│       ├── protocol/                     JSON-RPC 2.0 + MCP message types
│       ├── server/                       dispatcher, registries, scanner, invokers
│       ├── transport/                    stdio + WebFlux SSE transports
│       ├── schema/                       JSON Schema generator from method params
│       ├── db/                           SQL-driven resource provider
│       ├── security/                     JWT authenticator + reactive WebFilter
│       └── autoconfigure/                Spring Boot auto-configuration entry point
└── example/                              reference Spring Boot app using the library
```

The `core/` module is purely a library plus a starter. It has no `main` method; you wire it into your own Spring Boot app, or use the `example/` app as a starting point.

## Request lifecycle

An MCP `tools/call` over HTTP/SSE runs through the bridge like this:

```
POST /mcp
   |
   v
Reactor Netty (WebFlux runtime)
   |
   v
McpAuthWebFilter
   |   1. extract Authorization: Bearer <jwt>
   |   2. JwtAuthenticator.authenticate -> AuthContext (principal, roles)
   |   3. attach AuthContext to ServerWebExchange attributes
   |
   v
McpHttpController.handle
   |   bodyValue parsed as JsonRpcRequest by Jackson
   |
   v
McpDispatcher.dispatch(request, auth)
   |   switch on method name:
   |     - initialize       -> InitializeResult
   |     - tools/list       -> ToolRegistry.describe()
   |     - tools/call       -> see below
   |     - resources/list   -> ResourceRegistry.describe()
   |     - resources/read   -> see below
   |     - ping             -> {}
   |
   v   tools/call
ToolRegistry.require(name)
   |   throws METHOD_NOT_FOUND if absent
   |
   v
auth.hasAnyRole(tool.roles())
   |   throws FORBIDDEN if caller lacks any required role
   |
   v
ToolInvoker.invoke(tool, arguments)
   |   binds JsonNode arguments to method parameters via Jackson
   |   calls method.invoke(bean, args) reflectively
   |   wraps result in ToolCallResult.ok([Content])
   |
   v
JsonRpcResponse.ok(id, result)
   |
   v
Jackson serializes back to JSON
```

`resources/read` follows the same path with `ResourceRegistry.resolve(uri)` doing template matching, and `ResourceInvoker` either calling the annotated method or, when `query=` is set, delegating to `DatabaseResourceProvider`.

`stdio` works the same way except the transport reads line-delimited JSON from `System.in` and writes responses to `System.out`. There is no auth filter on stdio (the OS process boundary is the trust boundary).

## Bean lifecycle

The scanner runs as a `SmartInitializingSingleton`, not a `BeanPostProcessor`. The reason is brittle: when a `BeanPostProcessor` depends on regular beans (the registries, schema generator, ObjectMapper), Spring has to instantiate those dependencies before all post-processors are ready, which produces a long cascade of `BeanPostProcessorChecker` warnings on startup. `SmartInitializingSingleton.afterSingletonsInstantiated()` fires after every singleton is built, so the scan happens with no early-init drama.

The scan walks `BeanFactory.getBeanDefinitionNames()`, fetches each singleton bean, and calls `scan(bean)`, which uses `AnnotationUtils.findAnnotation` to look for `@MCPTool` / `@MCPResource` on every declared method. AOP-proxied beans (e.g. anything `@Transactional`) get unwrapped via `AopProxyUtils.ultimateTargetClass` so the annotation lookup hits the user's actual class, not the proxy.

## URI templates

`@MCPResource(uri = "db://customers/{region}/invoices/{type}")` compiles to a regex with named groups: `^db://customers/(?<region>[^/]+)/invoices/(?<type>[^/]+)$`. On `resources/read`, the registry walks all registered resources in registration order and picks the first regex match. Captured groups bind to method parameters by name (or to `:region`-style named SQL parameters when `query=` is set).

URIs are resolved in registration order, not most-specific-first. Two templates that overlap will match in registration order, so put the more specific one first if you have ambiguous templates.

## Security boundaries

JWT validation happens in `JwtAuthenticator`. It is HS256-only by design; switching to RS256 means subbing the key construction in the constructor and is a future-work hook. The authenticator requires:

- An `Authorization: Bearer <token>` header.
- A token signed with the configured secret (minimum 32 bytes for HS256, validated at startup).
- A token issued by the configured issuer (when `spring.mcp.security.jwt.issuer` is set).
- A token that has not expired.

The roles claim (`roles` by default, override with `spring.mcp.security.jwt.roles-claim`) accepts either a list (`["admin", "analyst"]`) or a comma-separated string (`"admin,analyst"`).

Role checks happen inside `McpDispatcher` against `RegisteredTool.roles()` and `RegisteredResource.roles()`. An empty roles array means "any authenticated caller". The dispatcher does not interpret an unauthenticated `AuthContext.ANONYMOUS`: tools without role requirements are callable, tools with role requirements get rejected with `FORBIDDEN`.

If you disable security (`spring.mcp.security.enabled=false`), `McpAuthWebFilter` is not registered and every request gets `AuthContext.ANONYMOUS`. Tools with `roles = {...}` will reject anonymous calls. This is intentional: disabling auth does not disable role checks, it just removes the JWT requirement.

## Error contract

Every error path in the dispatcher throws `McpException(code, message)`. The dispatcher catches it and returns `JsonRpcResponse.fail(id, JsonRpcError.of(code, message))`. The codes follow JSON-RPC convention plus a few MCP-specific extensions:

| Code     | Name              | When |
|----------|-------------------|------|
| -32600   | INVALID_REQUEST   | Malformed JSON-RPC envelope |
| -32601   | METHOD_NOT_FOUND  | Unknown method name or unknown tool |
| -32602   | INVALID_PARAMS    | Missing or unbindable parameters |
| -32603   | INTERNAL_ERROR    | Tool method threw, SQL failed, etc. |
| -32001   | UNAUTHORIZED      | Missing or invalid JWT |
| -32003   | FORBIDDEN         | Caller lacks the required role |
| -32004   | RESOURCE_NOT_FOUND| URI does not match any registered resource |

`UNAUTHORIZED` returns HTTP 401 because the auth filter intercepts before the controller runs. The other codes return HTTP 200 with an `error` object in the JSON-RPC payload, per spec.

## What this is not

- The bridge is not a request router across multiple MCP servers. One Spring app speaks for itself.
- The bridge is not stateful. Sessions, subscriptions, and progress notifications are not implemented. `tools/call` is request-response only.
- The bridge is not a sandbox. Tool methods run with the same JVM permissions as the rest of your Spring app. If you want isolation, run the bridge in a separate process with its own classloader policy.

These are deliberate omissions, not unfinished work. Adding any of them would change the integration model.
