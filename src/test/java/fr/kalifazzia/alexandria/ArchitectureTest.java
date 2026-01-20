package fr.kalifazzia.alexandria;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Tests d'architecture pour enforcer la separation en couches.
 *
 * Architecture:
 * - api: Points d'entree (REST, CLI, MCP) - couche superieure
 * - core: Domaine metier et services - couche centrale
 * - infra: Infrastructure (DB, embeddings, external) - couche basse
 */
@AnalyzeClasses(
        packages = "fr.kalifazzia.alexandria",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    @ArchTest
    static final ArchRule layerDependencies = layeredArchitecture()
            .consideringAllDependencies()
            .layer("API").definedBy("..api..")
            .layer("Core").definedBy("..core..")
            .layer("Infra").definedBy("..infra..")
            .whereLayer("API").mayNotBeAccessedByAnyLayer()
            .whereLayer("Core").mayOnlyBeAccessedByLayers("API", "Infra")
            .whereLayer("Infra").mayOnlyBeAccessedByLayers("Core");

    @ArchTest
    static final ArchRule noCyclicDependencies = slices()
            .matching("fr.kalifazzia.alexandria.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule infraShouldNotAccessApi = noClasses()
            .that().resideInAPackage("..infra..")
            .should().accessClassesThat().resideInAPackage("..api..");

    @ArchTest
    static final ArchRule coreShouldNotAccessApi = noClasses()
            .that().resideInAPackage("..core..")
            .should().accessClassesThat().resideInAPackage("..api..");
}
