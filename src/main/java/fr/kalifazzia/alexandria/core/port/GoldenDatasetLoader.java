package fr.kalifazzia.alexandria.core.port;

import fr.kalifazzia.alexandria.core.evaluation.GoldenQuery;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Port interface for loading golden dataset from external source.
 *
 * <p>Follows hexagonal architecture - core defines the contract,
 * infra provides the implementation (e.g., JSONL file loader).
 */
public interface GoldenDatasetLoader {

    /**
     * Loads golden queries from the specified dataset path.
     *
     * @param datasetPath path to the golden dataset file
     * @return list of golden queries loaded from the dataset
     * @throws IOException if the file cannot be read or parsed
     */
    List<GoldenQuery> load(Path datasetPath) throws IOException;
}
