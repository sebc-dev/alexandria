package dev.alexandria.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link McpToolService} methods as MCP tools via Spring AI auto-configuration.
 *
 * <p>The {@link ToolCallbackProvider} bean is discovered by Spring AI's MCP server
 * auto-configuration, which registers each {@code @Tool}-annotated method as a callable
 * tool over the configured transport (stdio or SSE).
 *
 * @see McpToolService
 */
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider alexandriaTools(McpToolService toolService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(toolService)
                .build();
    }
}
