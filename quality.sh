#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# quality.sh - Local quality gate runner for Alexandria
# Provides concise summaries optimized for Claude Code token efficiency
# ---------------------------------------------------------------------------

GRADLEW="./gradlew --console=plain --no-daemon"
COMMAND="${1:-help}"
shift || true

# Parse optional flags
PACKAGE=""
WITH_INTEGRATION=false
while [[ $# -gt 0 ]]; do
    case "$1" in
        --package)
            PACKAGE="${2:-}"
            shift 2
            ;;
        --with-integration)
            WITH_INTEGRATION=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# ---------------------------------------------------------------------------
# Summary helpers
# ---------------------------------------------------------------------------

print_separator() {
    echo ""
    echo "---"
}

# Parse JUnit XML results from a test results directory
# $1: results directory (default: build/test-results/test)
# $2: label prefix (default: TESTS)
summarize_tests() {
    local results_dir="${1:-build/test-results/test}"
    local label="${2:-TESTS}"
    if [[ ! -d "$results_dir" ]]; then
        echo "${label}: Could not parse report. Check build/reports/ for details."
        return
    fi

    local total=0 failures=0 errors=0 skipped=0
    for xml_file in "$results_dir"/*.xml; do
        [[ -f "$xml_file" ]] || continue
        local t f e s
        t=$(grep -oP 'tests="[^"]*"' "$xml_file" | head -1 | grep -oP '[0-9]+') || t=0
        f=$(grep -oP 'failures="[^"]*"' "$xml_file" | head -1 | grep -oP '[0-9]+') || f=0
        e=$(grep -oP 'errors="[^"]*"' "$xml_file" | head -1 | grep -oP '[0-9]+') || e=0
        s=$(grep -oP 'skipped="[^"]*"' "$xml_file" | head -1 | grep -oP '[0-9]+') || s=0
        total=$((total + t))
        failures=$((failures + f + e))
        skipped=$((skipped + s))
    done

    local passed=$((total - failures - skipped))
    echo "${label}: ${passed} passed, ${failures} failed, ${skipped} skipped (total: ${total})"
}

# Parse JaCoCo XML report for coverage percentages
summarize_coverage() {
    local report="build/reports/jacoco/test/jacocoTestReport.xml"
    if [[ ! -f "$report" ]]; then
        echo "COVERAGE: Could not parse report. Check build/reports/ for details."
        return
    fi

    local line_missed line_covered branch_missed branch_covered
    line_missed=$(grep -oP '<counter type="LINE" missed="\K[0-9]+' "$report" | tail -1) || line_missed=0
    line_covered=$(grep -oP '<counter type="LINE" [^/]*covered="\K[0-9]+' "$report" | tail -1) || line_covered=0
    branch_missed=$(grep -oP '<counter type="BRANCH" missed="\K[0-9]+' "$report" | tail -1) || branch_missed=0
    branch_covered=$(grep -oP '<counter type="BRANCH" [^/]*covered="\K[0-9]+' "$report" | tail -1) || branch_covered=0

    local line_total=$((line_missed + line_covered))
    local branch_total=$((branch_missed + branch_covered))

    local line_pct=0 branch_pct=0
    if [[ $line_total -gt 0 ]]; then
        line_pct=$((line_covered * 100 / line_total))
    fi
    if [[ $branch_total -gt 0 ]]; then
        branch_pct=$((branch_covered * 100 / branch_total))
    fi

    echo "COVERAGE: ${line_pct}% line, ${branch_pct}% branch"
}

# Parse PIT mutation testing results
summarize_mutations() {
    local report_dir="build/reports/pitest"
    local mutations_xml=""

    # Try to find mutations.xml or the index HTML
    if [[ -f "${report_dir}/mutations.xml" ]]; then
        mutations_xml="${report_dir}/mutations.xml"
    fi

    if [[ -n "$mutations_xml" ]]; then
        local total killed survived
        total=$(grep -c '<mutation ' "$mutations_xml" 2>/dev/null) || total=0
        killed=$(grep -c "status=['\"]KILLED['\"]" "$mutations_xml" 2>/dev/null) || killed=0
        survived=$(grep -c "status=['\"]SURVIVED['\"]" "$mutations_xml" 2>/dev/null) || survived=0

        local score=0
        if [[ $total -gt 0 ]]; then
            score=$((killed * 100 / total))
        fi

        echo "MUTATIONS: ${killed} killed / ${total} total (${score}% mutation score)"
    else
        # Fallback: parse HTML index for the summary line
        local index_html="${report_dir}/index.html"
        if [[ -f "$index_html" ]]; then
            # Try to extract from the HTML summary
            local summary
            summary=$(grep -oP '[0-9]+% *- *[0-9]+/[0-9]+' "$index_html" | head -1) || summary=""
            if [[ -n "$summary" ]]; then
                echo "MUTATIONS: ${summary}"
            else
                echo "MUTATIONS: Report generated. Check build/reports/pitest/index.html for details."
            fi
        else
            echo "MUTATIONS: Could not parse report. Check build/reports/pitest/ for details."
        fi
    fi
}

# Parse SpotBugs XML report
summarize_spotbugs() {
    local report="build/reports/spotbugs/spotbugsMain.xml"
    if [[ ! -f "$report" ]]; then
        echo "SPOTBUGS: Could not parse report. Check build/reports/spotbugs/ for details."
        return
    fi

    local total high medium low
    total=$(grep -c '<BugInstance' "$report" 2>/dev/null) || total=0
    high=$(grep -c 'priority="1"' "$report" 2>/dev/null) || high=0
    medium=$(grep -c 'priority="2"' "$report" 2>/dev/null) || medium=0
    low=$(grep -c 'priority="3"' "$report" 2>/dev/null) || low=0

    echo "SPOTBUGS: ${total} bugs found (${high} high, ${medium} medium, ${low} low)"
}

# Parse ArchUnit test results from JUnit XML
summarize_arch() {
    local arch_xml="build/test-results/test/TEST-dev.alexandria.architecture.ArchitectureTest.xml"
    if [[ ! -f "$arch_xml" ]]; then
        echo "ARCHITECTURE TESTS: No results found"
        return
    fi

    local failures errors
    failures=$(grep -oP 'failures="[^"]*"' "$arch_xml" | head -1 | grep -oP '[0-9]+') || failures=0
    errors=$(grep -oP 'errors="[^"]*"' "$arch_xml" | head -1 | grep -oP '[0-9]+') || errors=0

    if [[ $((failures + errors)) -eq 0 ]]; then
        echo "ARCHITECTURE TESTS: PASSED"
    else
        echo "ARCHITECTURE TESTS: FAILED (${failures} failures, ${errors} errors)"
    fi
}

# ---------------------------------------------------------------------------
# Subcommands
# ---------------------------------------------------------------------------

cmd_test() {
    echo "Running unit tests..."
    local exit_code=0
    if [[ -n "$PACKAGE" ]]; then
        $GRADLEW test --tests "${PACKAGE}.*" || exit_code=$?
    else
        $GRADLEW test || exit_code=$?
    fi
    print_separator
    summarize_tests
    return $exit_code
}

cmd_mutation() {
    echo "Running mutation testing (PIT)..."
    if [[ -n "$PACKAGE" ]]; then
        $GRADLEW pitest -Ppitest.targetClasses="${PACKAGE}.*" || true
    else
        $GRADLEW pitest || true
    fi
    print_separator
    summarize_mutations
}

cmd_spotbugs() {
    echo "Running SpotBugs analysis..."
    $GRADLEW spotbugsMain || true
    print_separator
    summarize_spotbugs
}

cmd_arch() {
    echo "Running architecture tests..."
    local exit_code=0
    $GRADLEW test --tests "dev.alexandria.architecture.*" || exit_code=$?
    print_separator
    if [[ $exit_code -eq 0 ]]; then
        echo "ARCHITECTURE TESTS: PASSED"
    else
        echo "ARCHITECTURE TESTS: FAILED"
        return $exit_code
    fi
}

cmd_integration() {
    echo "Running integration tests (requires Docker)..."
    local exit_code=0
    $GRADLEW integrationTest || exit_code=$?
    print_separator
    summarize_tests "build/test-results/integrationTest" "INTEGRATION TESTS"
    return $exit_code
}

cmd_coverage() {
    echo "Running tests with coverage..."
    local exit_code=0
    $GRADLEW test jacocoTestReport || exit_code=$?
    print_separator
    summarize_coverage
    return $exit_code
}

cmd_owasp() {
    echo "Running OWASP Dependency-Check..."
    local exit_code=0
    $GRADLEW dependencyCheckAnalyze || exit_code=$?
    print_separator
    local report="build/reports/dependency-check-report.json"
    if [[ -f "$report" ]]; then
        local total high medium low
        total=$(python3 -c "import json; d=json.load(open('$report')); print(sum(1 for dep in d.get('dependencies',[]) if dep.get('vulnerabilities')))" 2>/dev/null) || total="?"
        high=$(python3 -c "import json; d=json.load(open('$report')); print(sum(1 for dep in d.get('dependencies',[]) for v in dep.get('vulnerabilities',[]) if v.get('severity','').upper() in ('HIGH','CRITICAL')))" 2>/dev/null) || high="?"
        medium=$(python3 -c "import json; d=json.load(open('$report')); print(sum(1 for dep in d.get('dependencies',[]) for v in dep.get('vulnerabilities',[]) if v.get('severity','').upper() == 'MEDIUM'))" 2>/dev/null) || medium="?"
        low=$(python3 -c "import json; d=json.load(open('$report')); print(sum(1 for dep in d.get('dependencies',[]) for v in dep.get('vulnerabilities',[]) if v.get('severity','').upper() == 'LOW'))" 2>/dev/null) || low="?"
        echo "OWASP: ${total} vulnerable dependencies (${high} high/critical, ${medium} medium, ${low} low)"
    else
        echo "OWASP: Report not found. Check build/reports/ for details."
    fi
    return $exit_code
}

cmd_sbom() {
    echo "Generating CycloneDX SBOM..."
    $GRADLEW cyclonedxBom
    print_separator
    local sbom_file
    sbom_file=$(find build/reports -name "*.cdx.json" -o -name "bom.json" -o -name "bom.xml" 2>/dev/null | head -1)
    if [[ -n "$sbom_file" ]]; then
        echo "SBOM: Generated at ${sbom_file}"
    else
        echo "SBOM: File not found. Check build/reports/ for details."
    fi
}

cmd_all() {
    echo "Running all quality gates..."
    echo ""

    # Phase 1: Tests + Coverage
    echo "=== Phase 1: Tests + Coverage ==="
    local test_exit=0
    $GRADLEW test jacocoTestReport || test_exit=$?
    print_separator
    summarize_tests
    summarize_coverage
    echo ""

    # Phase 2: SpotBugs (non-blocking)
    echo "=== Phase 2: SpotBugs ==="
    $GRADLEW spotbugsMain || true
    print_separator
    summarize_spotbugs
    echo ""

    # Phase 3: OWASP Dependency-Check
    echo "=== Phase 3: OWASP Dependency-Check ==="
    $GRADLEW dependencyCheckAnalyze || true
    print_separator
    echo ""

    # Phase 4: PIT mutation testing (heavy, run separately)
    echo "=== Phase 4: Mutation Testing ==="
    $GRADLEW pitest || true
    print_separator
    summarize_mutations

    # Phase 5: Integration tests (optional, requires Docker)
    local integ_exit=0
    if [[ "$WITH_INTEGRATION" == "true" ]]; then
        echo ""
        echo "=== Phase 5: Integration Tests ==="
        $GRADLEW integrationTest || integ_exit=$?
        print_separator
        summarize_tests "build/test-results/integrationTest" "INTEGRATION TESTS"
    fi

    echo ""
    echo "=== Quality Gate Summary ==="
    summarize_tests
    summarize_coverage
    summarize_spotbugs
    summarize_mutations
    summarize_arch
    if [[ "$WITH_INTEGRATION" == "true" ]]; then
        summarize_tests "build/test-results/integrationTest" "INTEGRATION TESTS"
    fi

    # Fail if unit tests or integration tests failed
    if [[ $test_exit -ne 0 ]]; then
        return $test_exit
    fi
    return $integ_exit
}

cmd_help() {
    cat <<'USAGE'
quality.sh - Alexandria local quality gate runner

USAGE:
    ./quality.sh <command> [options]

COMMANDS:
    test           Run unit tests, print pass/fail summary
    integration    Run integration tests (requires Docker), print pass/fail
    mutation       Run PIT mutation testing, print mutation score
    spotbugs       Run SpotBugs analysis, print bug count
    arch           Run ArchUnit architecture tests, print pass/fail
    coverage       Run tests + JaCoCo, print coverage percentages
    owasp          Run OWASP Dependency-Check, print vulnerability summary
    sbom           Generate CycloneDX SBOM, print file location
    all            Run all quality gates, print combined summary
    help           Show this help message

OPTIONS:
    --package <pkg>       Target a specific package (test, mutation only)
                          Example: ./quality.sh test --package dev.alexandria.search
    --with-integration    Include integration tests in 'all' (requires Docker)

EXAMPLES:
    ./quality.sh test                                    # Run all unit tests
    ./quality.sh test --package dev.alexandria.search     # Test specific package
    ./quality.sh integration                             # Run integration tests
    ./quality.sh mutation                                # Run mutation testing
    ./quality.sh mutation --package dev.alexandria.core   # Mutate specific package
    ./quality.sh spotbugs                                # Run SpotBugs analysis
    ./quality.sh coverage                                # Generate coverage report
    ./quality.sh arch                                    # Run architecture tests
    ./quality.sh all                                     # Run everything (no integ)
    ./quality.sh all --with-integration                  # Run everything + integ
USAGE
}

# ---------------------------------------------------------------------------
# Dispatch
# ---------------------------------------------------------------------------

case "$COMMAND" in
    test)          cmd_test ;;
    integration)   cmd_integration ;;
    mutation)      cmd_mutation ;;
    spotbugs)      cmd_spotbugs ;;
    arch)          cmd_arch ;;
    coverage)      cmd_coverage ;;
    owasp)         cmd_owasp ;;
    sbom)          cmd_sbom ;;
    all)           cmd_all ;;
    help|--help|-h)  cmd_help ;;
    *)
        echo "Unknown command: $COMMAND"
        echo "Run './quality.sh help' for usage."
        exit 1
        ;;
esac
