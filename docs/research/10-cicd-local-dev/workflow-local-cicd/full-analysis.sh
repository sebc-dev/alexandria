#!/bin/bash
# scripts/full-analysis.sh
# Analyse complète locale incluant mutation testing (PIT)
# Exécuter avant de créer/mettre à jour une PR

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Parse arguments
SKIP_PIT=false
VERBOSE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-pit)
            SKIP_PIT=true
            shift
            ;;
        --verbose|-v)
            VERBOSE=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --skip-pit    Skip mutation testing (saves 8-15 minutes)"
            echo "  --verbose     Show detailed output"
            echo "  --help        Show this help"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

MVN_QUIET=""
if [ "$VERBOSE" = false ]; then
    MVN_QUIET="-q"
fi

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}         ALEXANDRIA - Full Local Analysis Pipeline              ${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

START_TIME=$(date +%s)

# Compteur d'étapes
if [ "$SKIP_PIT" = true ]; then
    TOTAL_STEPS=5
else
    TOTAL_STEPS=6
fi
CURRENT_STEP=0

next_step() {
    CURRENT_STEP=$((CURRENT_STEP + 1))
    echo -e "${YELLOW}[$CURRENT_STEP/$TOTAL_STEPS] $1${NC}"
}

# ============================================================
# Étape 1: Compilation avec Error Prone
# ============================================================
next_step "Compiling with Error Prone..."
if mvn clean compile -Perror-prone $MVN_QUIET; then
    echo -e "${GREEN}  ✓ Compilation OK${NC}"
else
    echo -e "${RED}❌ Compilation failed${NC}"
    echo ""
    echo "Fix compilation errors before proceeding."
    exit 1
fi

# ============================================================
# Étape 2: Tests unitaires
# ============================================================
next_step "Running unit tests..."
if mvn test -Dtest='!**/*IT.java' $MVN_QUIET; then
    echo -e "${GREEN}  ✓ Unit tests OK${NC}"
else
    echo -e "${RED}❌ Unit tests failed${NC}"
    echo ""
    echo "Check test failures in target/surefire-reports/"
    exit 1
fi

# ============================================================
# Étape 3: PMD + CPD (Copy-Paste Detection)
# ============================================================
next_step "Running PMD analysis..."
if mvn pmd:check pmd:cpd-check $MVN_QUIET; then
    echo -e "${GREEN}  ✓ PMD OK${NC}"
else
    echo -e "${RED}❌ PMD violations found${NC}"
    echo ""
    echo "Run 'mvn pmd:pmd' for HTML report at target/pmd.html"
    exit 1
fi

# ============================================================
# Étape 4: SpotBugs
# ============================================================
next_step "Running SpotBugs..."
if mvn spotbugs:check $MVN_QUIET; then
    echo -e "${GREEN}  ✓ SpotBugs OK${NC}"
else
    echo -e "${RED}❌ SpotBugs issues found${NC}"
    echo ""
    echo "Run 'mvn spotbugs:gui' for interactive review"
    exit 1
fi

# ============================================================
# Étape 5: Checkstyle
# ============================================================
next_step "Running Checkstyle..."
if mvn checkstyle:check $MVN_QUIET; then
    echo -e "${GREEN}  ✓ Checkstyle OK${NC}"
else
    echo -e "${RED}❌ Checkstyle violations found${NC}"
    echo ""
    echo "Check target/checkstyle-result.xml for details"
    exit 1
fi

# ============================================================
# Étape 6: Mutation Testing (PIT) - Optionnel
# ============================================================
if [ "$SKIP_PIT" = false ]; then
    next_step "Running PIT Mutation Testing..."
    echo -e "  ${CYAN}(This may take 8-15 minutes - use --skip-pit to skip)${NC}"
    
    PIT_START=$(date +%s)
    
    if mvn test-compile org.pitest:pitest-maven:mutationCoverage \
        -DwithHistory=true \
        -Dthreads=4 \
        -DtimestampedReports=false $MVN_QUIET; then
        
        PIT_END=$(date +%s)
        PIT_DURATION=$((PIT_END - PIT_START))
        echo -e "${GREEN}  ✓ Mutation testing OK (${PIT_DURATION}s)${NC}"
    else
        echo -e "${RED}❌ Mutation testing failed threshold${NC}"
        echo ""
        echo "Report: target/pit-reports/index.html"
        echo ""
        echo "Low mutation score indicates tests don't catch all bugs."
        echo "Consider adding more assertions or edge case tests."
        exit 1
    fi
fi

# ============================================================
# Résumé final
# ============================================================
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))
MINUTES=$((DURATION / 60))
SECONDS=$((DURATION % 60))

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✅ All local analyses passed!${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "Duration: ${CYAN}${MINUTES}m ${SECONDS}s${NC}"
echo ""
echo "Reports generated:"
echo "  • PMD:           target/pmd.html"
echo "  • CPD:           target/cpd.html"
echo "  • SpotBugs:      target/spotbugsXml.xml"
echo "  • Checkstyle:    target/checkstyle-result.xml"
if [ "$SKIP_PIT" = false ]; then
    echo "  • PIT Mutations: target/pit-reports/index.html"
fi
echo ""
echo -e "${GREEN}Ready to create/update your PR!${NC}"
echo ""
