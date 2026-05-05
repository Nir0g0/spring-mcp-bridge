package dev.dylanott.mcp.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dylanott.mcp.db.DatabaseResourceProvider;
import dev.dylanott.mcp.schema.JsonSchemaGenerator;
import dev.dylanott.mcp.security.JwtAuthenticator;
import dev.dylanott.mcp.security.McpAuthWebFilter;
import dev.dylanott.mcp.security.McpSecurityProperties;
import dev.dylanott.mcp.server.McpBeanScanner;
import dev.dylanott.mcp.server.McpDispatcher;
import dev.dylanott.mcp.server.ResourceRegistry;
import dev.dylanott.mcp.server.ToolRegistry;
import dev.dylanott.mcp.server.invoker.ResourceInvoker;
import dev.dylanott.mcp.server.invoker.ToolInvoker;
import dev.dylanott.mcp.transport.McpHttpController;
import dev.dylanott.mcp.transport.StdioRunner;
import dev.dylanott.mcp.transport.StdioTransport;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Optional;

@AutoConfiguration(after = JacksonAutoConfiguration.class)
@EnableConfigurationProperties({McpProperties.class, McpSecurityProperties.class})
public class McpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry mcpToolRegistry() {
        return new ToolRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public ResourceRegistry mcpResourceRegistry() {
        return new ResourceRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public JsonSchemaGenerator mcpJsonSchemaGenerator(ObjectMapper mapper) {
        return new JsonSchemaGenerator(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolInvoker mcpToolInvoker(ObjectMapper mapper) {
        return new ToolInvoker(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ResourceInvoker mcpResourceInvoker(Optional<DatabaseResourceProvider> dbProvider) {
        return new ResourceInvoker(dbProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpDispatcher mcpDispatcher(ObjectMapper mapper,
                                       ToolRegistry tools,
                                       ResourceRegistry resources,
                                       ToolInvoker toolInvoker,
                                       ResourceInvoker resourceInvoker,
                                       McpProperties properties) {
        return new McpDispatcher(mapper, tools, resources, toolInvoker, resourceInvoker,
                properties.getServerName(), properties.getServerVersion());
    }

    @Bean
    public McpBeanScanner mcpBeanScanner(ToolRegistry tools,
                                         ResourceRegistry resources,
                                         JsonSchemaGenerator schemaGenerator,
                                         ConfigurableListableBeanFactory beanFactory) {
        return new McpBeanScanner(tools, resources, schemaGenerator, beanFactory);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate")
    static class DatabaseResourceConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public DatabaseResourceProvider mcpDatabaseResourceProvider(DataSource dataSource) {
            return new DatabaseResourceProvider(
                    new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(dataSource));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.web.reactive.config.WebFluxConfigurer")
    @ConditionalOnProperty(prefix = "spring.mcp", name = "transport",
            havingValue = "sse", matchIfMissing = true)
    static class HttpTransportConfiguration {

        @Bean
        public McpHttpController mcpHttpController(McpDispatcher dispatcher) {
            return new McpHttpController(dispatcher);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.web.reactive.config.WebFluxConfigurer")
    @ConditionalOnProperty(prefix = "spring.mcp", name = "transport", havingValue = "both")
    static class HttpTransportBothConfiguration {

        @Bean
        public McpHttpController mcpHttpControllerBoth(McpDispatcher dispatcher) {
            return new McpHttpController(dispatcher);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "spring.mcp", name = "transport", havingValue = "stdio")
    static class StdioOnlyConfiguration {

        @Bean
        public StdioTransport mcpStdioTransport(McpDispatcher dispatcher, ObjectMapper mapper) {
            return new StdioTransport(dispatcher, mapper);
        }

        @Bean
        public StdioRunner mcpStdioRunner(StdioTransport transport) {
            return new StdioRunner(transport);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "spring.mcp", name = "transport", havingValue = "both")
    static class StdioBothConfiguration {

        @Bean
        public StdioTransport mcpStdioTransportBoth(McpDispatcher dispatcher, ObjectMapper mapper) {
            return new StdioTransport(dispatcher, mapper);
        }

        @Bean
        public StdioRunner mcpStdioRunnerBoth(StdioTransport transport) {
            return new StdioRunner(transport);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "io.jsonwebtoken.Jwts",
            "org.springframework.web.server.WebFilter"
    })
    @ConditionalOnProperty(prefix = "spring.mcp.security", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    static class SecurityConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public JwtAuthenticator mcpJwtAuthenticator(McpSecurityProperties properties) {
            return new JwtAuthenticator(properties.getJwt());
        }

        @Bean
        @ConditionalOnMissingBean
        public McpAuthWebFilter mcpAuthWebFilter(JwtAuthenticator authenticator, ObjectMapper mapper) {
            return new McpAuthWebFilter(authenticator, mapper);
        }
    }
}
