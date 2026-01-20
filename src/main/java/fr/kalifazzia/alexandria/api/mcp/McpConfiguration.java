package fr.kalifazzia.alexandria.api.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * MCP server configuration.
 * Registers Alexandria tools with the MCP server via ToolCallbackProvider.
 */
@Configuration
@Profile("mcp")
public class McpConfiguration {

    @Bean
    ToolCallbackProvider alexandriaToolCallbackProvider(AlexandriaTools alexandriaTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(alexandriaTools)
                .build();
    }
}
