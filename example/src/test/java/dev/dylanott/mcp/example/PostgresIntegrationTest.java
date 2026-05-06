package dev.dylanott.mcp.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("mcpbridge")
            .withUsername("mcpbridge")
            .withPassword("mcpbridge");

    @DynamicPropertySource
    static void wireDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.mcp.security.enabled", () -> "true");
        registry.add("spring.mcp.security.jwt.secret",
                () -> "integration-test-secret-at-least-thirty-two-bytes-please");
        registry.add("spring.mcp.security.jwt.issuer", () -> "test-issuer");
    }

    @Value("${spring.mcp.security.jwt.secret}")
    private String jwtSecret;

    @Value("${spring.mcp.security.jwt.issuer}")
    private String jwtIssuer;

    @Autowired
    private WebTestClient client;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsRequestWithoutToken() {
        ObjectNode req = jsonRpc(1, "tools/list");
        client.post().uri("/mcp").bodyValue(req).exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void readsCustomersResourceWithAnalystToken() {
        ObjectNode req = jsonRpc(2, "resources/read");
        ObjectNode params = req.putObject("params");
        params.put("uri", "db://customers/CN-SOUTH");

        client.post().uri("/mcp")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithRoles("analyst"))
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .value(n -> {
                    JsonNode contents = n.get("result").get("contents");
                    assertThat(contents).isNotEmpty();
                    String body = contents.get(0).get("text").asText();
                    assertThat(body).contains("Shenzhen Beidou Robotics");
                    assertThat(body).contains("Foshan Tinywire Industrial");
                    assertThat(body).doesNotContain("Munich Carbon Composites");
                });
    }

    @Test
    void rejectsResourceReadWithoutRequiredRole() {
        ObjectNode req = jsonRpc(3, "resources/read");
        ObjectNode params = req.putObject("params");
        params.put("uri", "db://customers/CN-SOUTH");

        client.post().uri("/mcp")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithRoles("reader"))
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .value(n -> assertThat(n.get("error").get("code").asInt()).isEqualTo(-32003));
    }

    @Test
    void invokesMathToolWithToken() {
        ObjectNode req = jsonRpc(4, "tools/call");
        ObjectNode params = req.putObject("params");
        params.put("name", "math.fibonacci");
        ObjectNode args = params.putObject("arguments");
        args.put("n", 10);

        client.post().uri("/mcp")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithRoles("reader"))
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .value(n -> assertThat(n.get("result").get("content").get(0).get("text").asText())
                        .isEqualTo("55"));
    }

    private String tokenWithRoles(String... roles) {
        var key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return Jwts.builder()
                .issuer(jwtIssuer)
                .subject("integration-test")
                .claim("roles", List.of(roles))
                .expiration(Date.from(Instant.now().plusSeconds(120)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private ObjectNode jsonRpc(int id, String method) {
        ObjectNode root = mapper.createObjectNode();
        root.put("jsonrpc", "2.0");
        root.put("id", id);
        root.put("method", method);
        return root;
    }
}
