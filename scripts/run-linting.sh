#!/bin/bash

# Run Linting Script
set -e

echo "🔍 Running linting..."

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check for npx and npm
check_dependencies() {
    log_info "Checking dependencies..."
    
    if ! command -v npx &> /dev/null; then
        log_error "npx not installed"
        exit 1
    fi
}

lint_groovy_files() {
    log_info "Linting Groovy files..."
    
    # Check for groovy linter
    if npx groovy-lint --version &> /dev/null; then
        # Lint all Groovy files
        find . -name "*.groovy" -not -path "./.git/*" -exec npx groovy-lint {} \; || true
    else
        log_warn "groovy-lint not installed, skipping Groovy linting"
        log_info "Install with: npm install -g groovy-lint"
    fi
    
    # Basic Groovy syntax check
    for file in $(find . -name "*.groovy" -not -path "./.git/*"); do
        log_info "Checking syntax: $file"
        groovy -c "$file" && echo "✅ Valid" || echo "❌ Invalid"
    done
}

lint_shell_scripts() {
    log_info "Linting shell scripts..."
    
    # Check for shellcheck
    if command -v shellcheck &> /dev/null; then
        find . -name "*.sh" -not -path "./.git/*" -exec shellcheck {} \; || true
    else
        log_warn "shellcheck not installed, skipping shell script linting"
        log_info "Install with: apt-get install shellcheck"
    fi
}

lint_yaml_files() {
    log_info "Linting YAML files..."
    
    # Check for yamllint
    if command -v yamllint &> /dev/null; then
        find . -name "*.yml" -name "*.yaml" -not -path "./.git/*" -exec yamllint {} \; || true
    else
        log_warn "yamllint not installed, skipping YAML linting"
    fi
}

check_code_style() {
    log_info "Checking code style..."
    
    # Check for consistent indentation
    log_info "Checking indentation..."
    find . -name "*.groovy" -not -path "./.git/*" -exec awk '
        /^ / { if (length($0) > 0 && !/^  [^ ]/) { print FILENAME ":" FNR ": Mixed indentation"; exit 1 } }
    ' {} \; || true
    
    # Check for trailing whitespace
    log_info "Checking trailing whitespace..."
    find . -name "*.groovy" -name "*.md" -name "*.sh" -not -path "./.git/*" -exec grep -n "[[:blank:]]$" {} \; | head -10 || true
}

generate_lint_report() {
    log_info "Generating lint report..."
    
    cat > lint-report.md << EOF
# Lint Report

Generated: $(date)

## Summary

- Groovy Files: TODO
- Shell Scripts: TODO  
- YAML Files: TODO
- Issues Found: TODO

## Issues

### Groovy Issues
- [ ] TODO

### Shell Script Issues  
- [ ] TODO

### YAML Issues
- [ ] TODO

## Recommendations

1. Fix critical issues first
2. Address style violations
3. Improve code consistency

EOF

    log_info "Lint report generated: lint-report.md"
}

main() {
    log_info "Starting linting process..."
    
    check_dependencies
    lint_groovy_files
    lint_shell_scripts
    lint_yaml_files
    check_code_style
    generate_lint_report
    
    log_info "✅ Linting completed"
}

main "$@"