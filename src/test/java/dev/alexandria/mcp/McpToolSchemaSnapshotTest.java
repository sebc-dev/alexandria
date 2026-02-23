package dev.alexandria.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Snapshot test verifying MCP tool schemas remain stable. Compares the live tool definitions
 * extracted from {@link McpToolService} against a versioned reference file.
 *
 * <p>If this test fails, either:
 *
 * <ul>
 *   <li>An intentional schema change was made -- run {@code ./gradlew updateMcpSnapshot} to update
 *       the reference file, then review and commit it.
 *   <li>An unintentional change was introduced -- revert the code change.
 * </ul>
 *
 * <p>This is a pure unit test: no Spring context, no I/O beyond classloader resource loading.
 */
class McpToolSchemaSnapshotTest {

  private static final String REFERENCE_PATH = "mcp/tools-schema.json";
  private static final int EXPECTED_TOOL_COUNT = 7;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final Set<String> EXPECTED_TOOL_NAMES =
      Set.of(
          "search_docs",
          "list_sources",
          "add_source",
          "remove_source",
          "crawl_status",
          "recrawl_source",
          "index_statistics");

  @Test
  void tool_count_guard_expects_exactly_seven_tools() {
    List<Map<String, Object>> tools = parseGeneratedSchema();

    assertThat(tools)
        .as(
            "Expected exactly %d MCP tools. If a tool was added or removed, "
                + "update EXPECTED_TOOL_COUNT and EXPECTED_TOOL_NAMES, "
                + "then run ./gradlew updateMcpSnapshot",
            EXPECTED_TOOL_COUNT)
        .hasSize(EXPECTED_TOOL_COUNT);
  }

  @Test
  void all_seven_tool_names_present() {
    List<Map<String, Object>> tools = parseGeneratedSchema();

    Set<String> actualNames =
        tools.stream().map(t -> (String) t.get("name")).collect(Collectors.toSet());

    assertThat(actualNames)
        .as("MCP tool names must match the expected set")
        .isEqualTo(EXPECTED_TOOL_NAMES);
  }

  @Test
  void schema_matches_reference() throws IOException {
    String referenceJson = loadReferenceSchema();
    String currentJson = McpSchemaSnapshotGenerator.generateSchema();

    JsonNode referenceTree = MAPPER.readTree(referenceJson);
    JsonNode currentTree = MAPPER.readTree(currentJson);

    if (!referenceTree.equals(currentTree)) {
      String diffMessage = buildDiffMessage(referenceTree, currentTree);
      fail(diffMessage);
    }
  }

  // --- Helpers ---

  private static List<Map<String, Object>> parseGeneratedSchema() {
    String json = McpSchemaSnapshotGenerator.generateSchema();
    try {
      return MAPPER.readValue(json, new TypeReference<>() {});
    } catch (IOException e) {
      throw new IllegalStateException("Failed to parse generated schema", e);
    }
  }

  private static String loadReferenceSchema() throws IOException {
    try (InputStream is =
        McpToolSchemaSnapshotTest.class.getClassLoader().getResourceAsStream(REFERENCE_PATH)) {
      if (is == null) {
        throw new IllegalStateException(
            "Reference schema not found at "
                + REFERENCE_PATH
                + ". Run ./gradlew updateMcpSnapshot to generate it.");
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String buildDiffMessage(JsonNode reference, JsonNode current) {
    Map<String, JsonNode> refTools = indexByName(reference);
    Map<String, JsonNode> curTools = indexByName(current);

    StringBuilder sb = new StringBuilder();
    sb.append("MCP schema has changed. Run `./gradlew updateMcpSnapshot` to update.\n\n");

    // Added tools
    Set<String> added =
        curTools.keySet().stream()
            .filter(name -> !refTools.containsKey(name))
            .collect(Collectors.toSet());
    if (!added.isEmpty()) {
      sb.append("Added tools: ").append(added).append("\n");
    }

    // Removed tools
    Set<String> removed =
        refTools.keySet().stream()
            .filter(name -> !curTools.containsKey(name))
            .collect(Collectors.toSet());
    if (!removed.isEmpty()) {
      sb.append("Removed tools: ").append(removed).append("\n");
    }

    // Modified tools
    StringBuilder modified = new StringBuilder();
    for (String name : refTools.keySet()) {
      if (curTools.containsKey(name)) {
        JsonNode refTool = refTools.get(name);
        JsonNode curTool = curTools.get(name);
        if (!refTool.equals(curTool)) {
          modified.append("  - ").append(name).append(":\n");
          appendFieldDiff(modified, "description", refTool, curTool);
          appendFieldDiff(modified, "inputSchema", refTool, curTool);
        }
      }
    }
    if (!modified.isEmpty()) {
      sb.append("Modified tools:\n").append(modified);
    }

    return sb.toString();
  }

  private static void appendFieldDiff(
      StringBuilder sb, String field, JsonNode refTool, JsonNode curTool) {
    JsonNode refField = refTool.get(field);
    JsonNode curField = curTool.get(field);
    if (refField != null && curField != null && !refField.equals(curField)) {
      sb.append("      ").append(field).append(" changed\n");
      sb.append("        expected: ").append(refField).append("\n");
      sb.append("        actual:   ").append(curField).append("\n");
    }
  }

  private static Map<String, JsonNode> indexByName(JsonNode arrayNode) {
    Map<String, JsonNode> map = new LinkedHashMap<>();
    for (JsonNode tool : arrayNode) {
      String name = tool.get("name").asText();
      map.put(name, tool);
    }
    return map;
  }
}
