package dev.alexandria.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "dev.alexandria", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // Feature packages should not depend on adapter packages.
    // allowEmptyShould: on a skeleton project no feature/adapter packages exist yet.
    // The rules enforce structure as the codebase grows.
    @ArchTest
    static final ArchRule features_should_not_depend_on_adapters =
        noClasses().that().resideInAnyPackage(
                "..ingestion..", "..search..", "..source..", "..document.."
            )
            .should().dependOnClassesThat().resideInAnyPackage(
                "..mcp..", "..api.."
            )
            .allowEmptyShould(true);

    // Adapter packages should not depend on each other
    @ArchTest
    static final ArchRule adapters_should_not_depend_on_each_other =
        noClasses().that().resideInAPackage("..mcp..")
            .should().dependOnClassesThat().resideInAPackage("..api..")
            .allowEmptyShould(true);

    // Config package should not depend on feature or adapter packages
    @ArchTest
    static final ArchRule config_should_not_depend_on_features =
        noClasses().that().resideInAPackage("..config..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..mcp..", "..api.."
            )
            .allowEmptyShould(true);

    // No cyclic dependencies between top-level packages
    @ArchTest
    static final ArchRule no_package_cycles =
        slices().matching("dev.alexandria.(*)..").should().beFreeOfCycles()
            .allowEmptyShould(true);
}
