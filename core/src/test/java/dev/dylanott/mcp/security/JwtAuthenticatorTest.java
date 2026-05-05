package dev.dylanott.mcp.security;

import dev.dylanott.mcp.protocol.JsonRpcError;
import dev.dylanott.mcp.server.AuthContext;
import dev.dylanott.mcp.server.McpException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAuthenticatorTest {

    private static final String SECRET = "test-secret-that-is-at-least-thirty-two-bytes!";
    private static final String ISSUER = "test-issuer";

    private JwtAuthenticator authenticator;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        McpSecurityProperties.Jwt config = new McpSecurityProperties.Jwt();
        config.setSecret(SECRET);
        config.setIssuer(ISSUER);
        authenticator = new JwtAuthenticator(config);
        key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Test
    void acceptsValidTokenWithRoles() {
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject("dylan")
                .claim("roles", List.of("admin", "analyst"))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        AuthContext ctx = authenticator.authenticate("Bearer " + token);
        assertThat(ctx.authenticated()).isTrue();
        assertThat(ctx.principal()).isEqualTo("dylan");
        assertThat(ctx.roles()).containsExactlyInAnyOrder("admin", "analyst");
    }

    @Test
    void rejectsMissingHeader() {
        assertThatThrownBy(() -> authenticator.authenticate(null))
                .isInstanceOf(McpException.class)
                .extracting(e -> ((McpException) e).code())
                .isEqualTo(JsonRpcError.UNAUTHORIZED);
    }

    @Test
    void rejectsHeaderWithoutBearerPrefix() {
        assertThatThrownBy(() -> authenticator.authenticate("Basic abc"))
                .isInstanceOf(McpException.class);
    }

    @Test
    void rejectsExpiredToken() {
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject("dylan")
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> authenticator.authenticate("Bearer " + token))
                .isInstanceOf(McpException.class)
                .extracting(e -> ((McpException) e).code())
                .isEqualTo(JsonRpcError.UNAUTHORIZED);
    }

    @Test
    void rejectsWrongIssuer() {
        String token = Jwts.builder()
                .issuer("someone-else")
                .subject("dylan")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> authenticator.authenticate("Bearer " + token))
                .isInstanceOf(McpException.class);
    }

    @Test
    void parsesCsvRolesString() {
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject("dylan")
                .claim("roles", "admin,analyst,reader")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        AuthContext ctx = authenticator.authenticate("Bearer " + token);
        assertThat(ctx.roles()).containsExactlyInAnyOrder("admin", "analyst", "reader");
    }

    @Test
    void shortSecretIsRejected() {
        McpSecurityProperties.Jwt bad = new McpSecurityProperties.Jwt();
        bad.setSecret("too-short");
        assertThatThrownBy(() -> new JwtAuthenticator(bad))
                .isInstanceOf(IllegalStateException.class);
    }
}
