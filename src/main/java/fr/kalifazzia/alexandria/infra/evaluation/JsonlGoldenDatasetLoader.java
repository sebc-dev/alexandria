package fr.kalifazzia.alexandria.infra.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.kalifazzia.alexandria.core.evaluation.GoldenQuery;
import fr.kalifazzia.alexandria.core.port.GoldenDatasetLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * JSONL-based golden dataset loader implementation.
 *
 * <p>Reads golden queries from a JSONL (JSON Lines) file where each line
 * is a valid JSON object representing a {@link GoldenQuery}.
 *
 * <p>Supports streaming large datasets by processing line-by-line
 * without loading the entire file into memory.
 */
@Component
public class JsonlGoldenDatasetLoader implements GoldenDatasetLoader {

    private final ObjectMapper objectMapper;

    public JsonlGoldenDatasetLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<GoldenQuery> load(Path datasetPath) throws IOException {
        List<GoldenQuery> queries = new ArrayList<>();
        int lineNumber = 0;

        try (BufferedReader reader = Files.newBufferedReader(datasetPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    GoldenQuery query = objectMapper.readValue(line, GoldenQuery.class);
                    queries.add(query);
                } catch (JsonProcessingException e) {
                    throw new IOException("Invalid JSON at line " + lineNumber + ": " + e.getMessage(), e);
                }
            }
        }

        return List.copyOf(queries);
    }
}
