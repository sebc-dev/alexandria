package dev.alexandria.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.mockito.Mockito;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

/**
 * Utility to extract MCP tool schemas from {@link McpToolService} via reflection on {@code @Tool}
 * annotations. Produces a sorted, deterministic JSON representation for snapshot testing.
 *
 * <p>Uses Mockito to create a dummy McpToolService instance (only annotations are read, no runtime
 * invocations occur).
 */
public final class McpSchemaSnapshotGenerator {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  private McpSchemaSnapshotGenerator() {}

  /**
   * Generates a sorted JSON string representing all MCP tool schemas extracted from {@link
   * McpToolService}.
   *
   * @return JSON array of tool objects sorted by name, with sorted keys and 2-space indent
   */
  public static String generateSchema() {
    McpToolService toolService = createMockToolService();
    ToolCallback[] callbacks =
        MethodToolCallbackProvider.builder().toolObjects(toolService).build().getToolCallbacks();

    List<Map<String, Object>> tools = new ArrayList<>();
    for (ToolCallback callback : callbacks) {
      ToolDefinition def = callback.getToolDefinition();
      Map<String, Object> tool = new LinkedHashMap<>();
      tool.put("description", def.description());
      tool.put("inputSchema", parseJson(def.inputSchema()));
      tool.put("name", def.name());
      tools.add(tool);
    }

    tools.sort(Comparator.comparing(t -> (String) t.get("name")));

    try {
      return MAPPER.writeValueAsString(tools);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize tool schemas", e);
    }
  }

  /**
   * Writes the current schema to the specified file path.
   *
   * @param path target file path for the reference JSON
   */
  public static void writeSchemaToFile(Path path) throws IOException {
    String schema = generateSchema();
    Files.createDirectories(path.getParent());
    Files.writeString(path, schema + "\n");
  }

  /**
   * Entry point for the Gradle {@code updateMcpSnapshot} task. Writes schema to {@code
   * src/test/resources/mcp/tools-schema.json} relative to the project root.
   */
  public static void main(String[] args) throws IOException {
    Path target = Path.of("src/test/resources/mcp/tools-schema.json");
    writeSchemaToFile(target);
    System.out.println("MCP snapshot updated: " + target);
  }

  @SuppressWarnings("NullAway") // Mocks return null by default; only annotation scanning occurs
  private static McpToolService createMockToolService() {
    return new McpToolService(
        Mockito.mock(dev.alexandria.search.SearchService.class),
        Mockito.mock(dev.alexandria.source.SourceRepository.class),
        Mockito.mock(TokenBudgetTruncator.class),
        Mockito.mock(dev.alexandria.crawl.CrawlService.class),
        Mockito.mock(dev.alexandria.crawl.CrawlProgressTracker.class),
        Mockito.mock(dev.alexandria.ingestion.IngestionService.class),
        Mockito.mock(dev.alexandria.document.DocumentChunkRepository.class));
  }

  private static Object parseJson(String json) {
    try {
      return MAPPER.readValue(json, Object.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse inputSchema JSON: " + json, e);
    }
  }
}
