#!/bin/bash

#####################################################################
# Alexandria Performance Benchmark Runner
#
# Facilitates running k6 performance tests and baseline benchmarking
#
# Usage:
#   ./run-benchmarks.sh all              # Run all tests
#   ./run-benchmarks.sh layer1           # Run Layer 1 only
#   ./run-benchmarks.sh layer2           # Run Layer 2 only
#   ./run-benchmarks.sh e2e              # Run end-to-end only
#   ./run-benchmarks.sh baseline         # Run full baseline suite
#   ./run-benchmarks.sh smoke            # Quick smoke tests
#####################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULTS_DIR="${SCRIPT_DIR}/results"
BASE_URL="${ALEXANDRIA_BASE_URL:-http://localhost:3000}"
PROJECT_ID="${ALEXANDRIA_PROJECT_ID:-test-project}"

# Functions
print_header() {
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║  $1${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

check_prerequisites() {
    print_header "Checking Prerequisites"

    # Check k6 installation
    if ! command -v k6 &> /dev/null; then
        print_error "k6 is not installed. Please install k6 first:"
        echo ""
        echo "  macOS:        brew install k6"
        echo "  Ubuntu:       See README.md for apt installation"
        echo "  Windows:      choco install k6"
        echo ""
        exit 1
    fi
    print_success "k6 installed: $(k6 version --no-color | head -n 1)"

    # Check if Alexandria is running
    if ! curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/health" | grep -q "200"; then
        print_warning "Alexandria MCP server may not be running at ${BASE_URL}"
        print_info "Start Alexandria with: bun run dev"
        read -p "Continue anyway? (y/n) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    else
        print_success "Alexandria MCP server running at ${BASE_URL}"
    fi

    # Create results directory
    mkdir -p "${RESULTS_DIR}"
    print_success "Results directory: ${RESULTS_DIR}"

    echo ""
}

run_layer1() {
    print_header "Running Layer 1: Vector Search Tests"
    k6 run \
        --env ALEXANDRIA_BASE_URL="${BASE_URL}" \
        --env ALEXANDRIA_PROJECT_ID="${PROJECT_ID}" \
        --out "json=${RESULTS_DIR}/layer1-$(date +%Y%m%d-%H%M%S).json" \
        "${SCRIPT_DIR}/layer1-vector-search.k6.js"
}

run_layer2() {
    print_header "Running Layer 2: SQL Joins Tests"
    k6 run \
        --env ALEXANDRIA_BASE_URL="${BASE_URL}" \
        --env ALEXANDRIA_PROJECT_ID="${PROJECT_ID}" \
        --out "json=${RESULTS_DIR}/layer2-$(date +%Y%m%d-%H%M%S).json" \
        "${SCRIPT_DIR}/layer2-sql-joins.k6.js"
}

run_e2e() {
    print_header "Running End-to-End: Complete Pipeline Tests"
    k6 run \
        --env ALEXANDRIA_BASE_URL="${BASE_URL}" \
        --env ALEXANDRIA_PROJECT_ID="${PROJECT_ID}" \
        --out "json=${RESULTS_DIR}/e2e-$(date +%Y%m%d-%H%M%S).json" \
        "${SCRIPT_DIR}/end-to-end-retrieval.k6.js"
}

run_smoke() {
    print_header "Running Smoke Tests (Quick Validation)"

    print_info "Layer 1 smoke test..."
    k6 run --vus 1 --duration 30s \
        --env ALEXANDRIA_BASE_URL="${BASE_URL}" \
        --env ALEXANDRIA_PROJECT_ID="${PROJECT_ID}" \
        "${SCRIPT_DIR}/layer1-vector-search.k6.js"

    print_info "Layer 2 smoke test..."
    k6 run --vus 1 --duration 30s \
        --env ALEXANDRIA_BASE_URL="${BASE_URL}" \
        --env ALEXANDRIA_PROJECT_ID="${PROJECT_ID}" \
        "${SCRIPT_DIR}/layer2-sql-joins.k6.js"

    print_info "End-to-end smoke test..."
    k6 run --vus 2 --duration 1m \
        --env ALEXANDRIA_BASE_URL="${BASE_URL}" \
        --env ALEXANDRIA_PROJECT_ID="${PROJECT_ID}" \
        "${SCRIPT_DIR}/end-to-end-retrieval.k6.js"

    print_success "Smoke tests completed"
}

run_baseline() {
    print_header "Running Full Baseline Benchmark Suite"

    print_warning "This will run tests with 100, 1K, and 10K embeddings."
    print_warning "Total estimated time: 30-45 minutes"
    print_info "Ensure PostgreSQL is running and seeding script is available."
    echo ""
    read -p "Continue? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_info "Baseline cancelled"
        exit 0
    fi

    BASELINE_FILE="${RESULTS_DIR}/baseline-$(date +%Y%m%d-%H%M%S).txt"

    echo "# Alexandria Performance Baseline - $(date)" | tee "${BASELINE_FILE}"
    echo "# Environment: ${BASE_URL}" | tee -a "${BASELINE_FILE}"
    echo "# Project: ${PROJECT_ID}" | tee -a "${BASELINE_FILE}"
    echo "" | tee -a "${BASELINE_FILE}"

    # Test 1: 100 embeddings
    print_info "Step 1/4: Testing with 100 embeddings..."
    if command -v bun &> /dev/null; then
        print_info "Seeding 100 embeddings..."
        bun run db:seed --count 100 || print_warning "Seed command failed, continuing anyway"
    else
        print_warning "Bun not installed, skipping seed. Ensure DB has ~100 embeddings manually."
    fi

    echo "## Baseline: 100 Embeddings" | tee -a "${BASELINE_FILE}"
    k6 run \
        --env ALEXANDRIA_BASE_URL="${BASE_URL}" \
        --env ALEXANDRIA_PROJECT_ID="${PROJECT_ID}" \
        --env ALEXANDRIA_EMBEDDING_COUNT=100 \
        "${SCRIPT_DIR}/layer1-vector-search.k6.js" | tee -a "${BASELINE_FILE}"

    # Test 2: 1,000 embeddings
    print_info "Step 2/4: Testing with 1,000 embeddings..."
    if command -v bun &> /dev/null; then
        print_info "Seeding 1,000 embeddings..."
        bun run db:seed --count 1000 || print_warning "Seed command failed, continuing anyway"
    else
        print_warning "Bun not installed, skipping seed. Ensure DB has ~1K embeddings manually."
    fi

    echo "" | tee -a "${BASELINE_FILE}"
    echo "## Baseline: 1,000 Embeddings" | tee -a "${BASELINE_FILE}"
    k6 run \
        --env ALEXANDRIA_BASE_URL="${BASE_URL}" \
        --env ALEXANDRIA_PROJECT_ID="${PROJECT_ID}" \
        --env ALEXANDRIA_EMBEDDING_COUNT=1000 \
        "${SCRIPT_DIR}/layer1-vector-search.k6.js" | tee -a "${BASELINE_FILE}"

    # Test 3: 10,000 embeddings
    print_info "Step 3/4: Testing with 10,000 embeddings..."
    if command -v bun &> /dev/null; then
        print_info "Seeding 10,000 embeddings..."
        bun run db:seed --count 10000 || print_warning "Seed command failed, continuing anyway"
    else
        print_warning "Bun not installed, skipping seed. Ensure DB has ~10K embeddings manually."
    fi

    echo "" | tee -a "${BASELINE_FILE}"
    echo "## Baseline: 10,000 Embeddings" | tee -a "${BASELINE_FILE}"
    k6 run \
        --env ALEXANDRIA_BASE_URL="${BASE_URL}" \
        --env ALEXANDRIA_PROJECT_ID="${PROJECT_ID}" \
        --env ALEXANDRIA_EMBEDDING_COUNT=10000 \
        "${SCRIPT_DIR}/layer1-vector-search.k6.js" | tee -a "${BASELINE_FILE}"

    # Test 4: End-to-end
    print_info "Step 4/4: Running end-to-end baseline..."
    echo "" | tee -a "${BASELINE_FILE}"
    echo "## Baseline: End-to-End Pipeline" | tee -a "${BASELINE_FILE}"
    k6 run \
        --env ALEXANDRIA_BASE_URL="${BASE_URL}" \
        --env ALEXANDRIA_PROJECT_ID="${PROJECT_ID}" \
        "${SCRIPT_DIR}/end-to-end-retrieval.k6.js" | tee -a "${BASELINE_FILE}"

    print_success "Baseline benchmarking complete!"
    print_info "Results saved to: ${BASELINE_FILE}"
    echo ""
    print_info "Next steps:"
    echo "  1. Review results in ${BASELINE_FILE}"
    echo "  2. Document baseline in docs/performance-baselines.md"
    echo "  3. Update test-design-system to mark TC-006 as RESOLVED"
}

run_all() {
    print_header "Running All Performance Tests"
    run_layer1
    echo ""
    run_layer2
    echo ""
    run_e2e
    print_success "All tests completed!"
}

show_usage() {
    echo "Usage: $0 {all|layer1|layer2|e2e|baseline|smoke}"
    echo ""
    echo "Commands:"
    echo "  all       - Run all performance tests (Layer 1, Layer 2, E2E)"
    echo "  layer1    - Run Layer 1 vector search tests only"
    echo "  layer2    - Run Layer 2 SQL joins tests only"
    echo "  e2e       - Run end-to-end pipeline tests only"
    echo "  baseline  - Run full baseline suite (100/1K/10K embeddings)"
    echo "  smoke     - Run quick smoke tests for validation"
    echo ""
    echo "Environment variables:"
    echo "  ALEXANDRIA_BASE_URL     - Base URL (default: http://localhost:3000)"
    echo "  ALEXANDRIA_PROJECT_ID   - Project ID (default: test-project)"
    echo ""
    echo "Examples:"
    echo "  $0 smoke"
    echo "  ALEXANDRIA_BASE_URL=http://staging.example.com $0 all"
    echo "  $0 baseline"
}

# Main
main() {
    check_prerequisites

    case "${1:-}" in
        all)
            run_all
            ;;
        layer1)
            run_layer1
            ;;
        layer2)
            run_layer2
            ;;
        e2e)
            run_e2e
            ;;
        baseline)
            run_baseline
            ;;
        smoke)
            run_smoke
            ;;
        *)
            show_usage
            exit 1
            ;;
    esac
}

main "$@"
