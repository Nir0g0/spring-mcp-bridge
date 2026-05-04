package dev.dylanott.mcp.server;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dylanott.mcp.annotation.MCPResource;
import dev.dylanott.mcp.annotation.MCPTool;
import dev.dylanott.mcp.protocol.Resource;
import dev.dylanott.mcp.protocol.Tool;
import dev.dylanott.mcp.schema.JsonSchemaGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;

public class McpBeanScanner implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(McpBeanScanner.class);

    private final ToolRegistry tools;
    private final ResourceRegistry resources;
    private final JsonSchemaGenerator schemaGenerator;
    private final ConfigurableListableBeanFactory beanFactory;

    public McpBeanScanner(ToolRegistry tools,
                          ResourceRegistry resources,
                          JsonSchemaGenerator schemaGenerator) {
        this(tools, resources, schemaGenerator, null);
    }

    public McpBeanScanner(ToolRegistry tools,
                          ResourceRegistry resources,
                          JsonSchemaGenerator schemaGenerator,
                          ConfigurableListableBeanFactory beanFactory) {
        this.tools = tools;
        this.resources = resources;
        this.schemaGenerator = schemaGenerator;
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (beanFactory == null) {
            return;
        }
        for (String name : beanFactory.getBeanDefinitionNames()) {
            if (!beanFactory.containsBean(name) || !beanFactory.isSingleton(name)) {
                continue;
            }
            Object bean;
            try {
                bean = beanFactory.getBean(name);
            } catch (Exception e) {
                continue;
            }
            if (bean != null) {
                scan(bean);
            }
        }
    }

    public void scan(Object bean) {
        Class<?> targetClass = AopUtils.isAopProxy(bean)
                ? AopProxyUtils.ultimateTargetClass(bean)
                : bean.getClass();
        for (Method method : targetClass.getDeclaredMethods()) {
            MCPTool toolAnno = AnnotationUtils.findAnnotation(method, MCPTool.class);
            if (toolAnno != null) {
                registerTool(bean, method, toolAnno);
            }
            MCPResource resourceAnno = AnnotationUtils.findAnnotation(method, MCPResource.class);
            if (resourceAnno != null) {
                registerResource(bean, method, resourceAnno);
            }
        }
    }

    private void registerTool(Object bean, Method method, MCPTool anno) {
        String name = anno.name().isEmpty() ? method.getName() : anno.name();
        ObjectNode schema = schemaGenerator.generate(method);
        Tool descriptor = new Tool(name, anno.description(), schema);
        tools.register(new RegisteredTool(descriptor, bean, method, anno.roles()));
        log.info("Registered MCP tool '{}' -> {}.{}",
                name, method.getDeclaringClass().getSimpleName(), method.getName());
    }

    private void registerResource(Object bean, Method method, MCPResource anno) {
        UriTemplate.Compiled compiled = UriTemplate.compile(anno.uri());
        String resourceName = method.getName();
        Resource descriptor = new Resource(anno.uri(), resourceName, anno.description(), anno.mimeType());
        RegisteredResource registered = new RegisteredResource(
                descriptor, bean, method, anno.query(), anno.roles(),
                compiled.pattern(), compiled.names());
        resources.register(registered);
        log.info("Registered MCP resource '{}' -> {}.{}",
                anno.uri(), method.getDeclaringClass().getSimpleName(), method.getName());
    }
}
