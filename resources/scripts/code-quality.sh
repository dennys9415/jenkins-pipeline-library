#!/bin/bash

# Code Quality Script
set -e

echo "🔍 Running code quality checks..."

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

# Create reports directory
mkdir -p reports

# Detect project type
detect_project_type() {
    if [ -f "pom.xml" ]; then
        echo "java-maven"
    elif [ -f "build.gradle" ]; then
        echo "java-gradle"
    elif [ -f "package.json" ]; then
        echo "nodejs"
    elif [ -f "requirements.txt" ] || [ -f "setup.py" ] || [ -f "pyproject.toml" ]; then
        echo "python"
    elif [ -f "*.csproj" ]; then
        echo "dotnet"
    else
        echo "unknown"
    fi
}

# Run Java code quality checks
run_java_checks() {
    local project_type=$1
    
    log_info "Running Java code quality checks"
    
    if [ "$project_type" = "java-maven" ]; then
        # Checkstyle
        if [ -f "checkstyle.xml" ] || [ -f "src/main/resources/checkstyle.xml" ]; then
            log_info "Running Checkstyle"
            mvn checkstyle:checkstyle -Dcheckstyle.failOnViolation=false || true
            if [ -f "target/checkstyle-result.xml" ]; then
                log_info "Checkstyle report generated: target/checkstyle-result.xml"
            fi
        else
            log_warn "Checkstyle configuration not found, skipping"
        fi
        
        # PMD
        log_info "Running PMD"
        mvn pmd:pmd -Dpmd.failOnViolation=false || true
        if [ -f "target/pmd.xml" ]; then
            log_info "PMD report generated: target/pmd.xml"
        fi
        
        # SpotBugs
        log_info "Running SpotBugs"
        mvn spotbugs:spotbugs -Dspotbugs.failOnError=false || true
        if [ -f "target/spotbugsXml.xml" ]; then
            log_info "SpotBugs report generated: target/spotbugsXml.xml"
        fi
        
        # JaCoCo coverage
        log_info "Running JaCoCo coverage"
        mvn jacoco:prepare-agent test jacoco:report || true
        if [ -f "target/site/jacoco/jacoco.xml" ]; then
            log_info "JaCoCo report generated: target/site/jacoco/jacoco.xml"
        fi
        
    elif [ "$project_type" = "java-gradle" ]; then
        # Checkstyle
        if [ -f "config/checkstyle/checkstyle.xml" ]; then
            log_info "Running Checkstyle"
            gradle checkstyleMain checkstyleTest || true
        fi
        
        # PMD
        log_info "Running PMD"
        gradle pmdMain pmdTest || true
        
        # SpotBugs
        log_info "Running SpotBugs"
        gradle spotbugsMain spotbugsTest || true
        
        # JaCoCo coverage
        log_info "Running JaCoCo coverage"
        gradle jacocoTestReport || true
    fi
    
    # SonarQube analysis (if configured)
    if [ -n "$SONAR_HOST_URL" ] && [ -n "$SONAR_TOKEN" ]; then
        log_info "Running SonarQube analysis"
        mvn sonar:sonar -Dsonar.host.url=$SONAR_HOST_URL -Dsonar.login=$SONAR_TOKEN || true
    fi
}

# Run Node.js code quality checks
run_nodejs_checks() {
    log_info "Running Node.js code quality checks"
    
    # ESLint
    if [ -f ".eslintrc.js" ] || [ -f ".eslintrc.json" ] || [ -f ".eslintrc.yml" ]; then
        log_info "Running ESLint"
        npx eslint . --ext .js,.jsx,.ts,.tsx --format json --output-file reports/eslint-report.json || true
    else
        log_warn "ESLint configuration not found, skipping"
    fi
    
    # Prettier check
    if [ -f ".prettierrc" ] || [ -f ".prettierrc.js" ] || [ -f ".prettierrc.json" ]; then
        log_info "Running Prettier check"
        npx prettier --check . --loglevel warn || true
    fi
    
    # TypeScript type check
    if [ -f "tsconfig.json" ]; then
        log_info "Running TypeScript type check"
        npx tsc --noEmit --skipLibCheck || true
    fi
    
    # Jest coverage
    if [ -f "jest.config.js" ] || [ -f "jest.config.ts" ] || [ -f "package.json" ]; then
        log_info "Running Jest coverage"
        npx jest --coverage --coverageReporters=json,lcov,text --coverageDirectory=reports/coverage || true
    fi
    
    # Audit dependencies
    log_info "Running npm audit"
    npm audit --json > reports/npm-audit.json 2>/dev/null || true
}

# Run Python code quality checks
run_python_checks() {
    log_info "Running Python code quality checks"
    
    # Activate virtual environment if exists
    if [ -d "venv" ]; then
        source venv/bin/activate
    fi
    
    # Black code formatting check
    log_info "Running Black check"
    python -m black --check . --verbose > reports/black-report.txt 2>&1 || true
    
    # isort import sorting check
    log_info "Running isort check"
    python -m isort --check-only . --verbose > reports/isort-report.txt 2>&1 || true
    
    # Flake8 linting
    log_info "Running Flake8"
    python -m flake8 . --format=json --output-file=reports/flake8-report.json || true
    
    # Pylint
    log_info "Running Pylint"
    find . -name "*.py" -not -path "./venv/*" -not -path "./.venv/*" -exec python -m pylint {} --output-format=json:reports/pylint-report.json \; || true
    
    # MyPy type checking
    log_info "Running MyPy"
    python -m mypy . --junit-xml=reports/mypy-report.xml || true
    
    # Bandit security scanning
    log_info "Running Bandit security scan"
    python -m bandit -r . -f json -o reports/bandit-report.json || true
    
    # Safety dependency check
    log_info "Running Safety check"
    python -m safety check --json --output reports/safety-report.json || true
    
    # pytest with coverage
    log_info "Running pytest with coverage"
    python -m pytest tests/ -v --junitxml=reports/pytest-report.xml --cov=. --cov-report=xml:reports/coverage.xml --cov-report=html:reports/coverage-html || true
}

# Run .NET code quality checks
run_dotnet_checks() {
    log_info "Running .NET code quality checks"
    
    # StyleCop analyzers (if configured)
    log_info "Running build with analyzers"
    dotnet build --verbosity minimal || true
    
    # Security code scan
    log_info "Running security scan"
    dotnet list package --vulnerable --include-transitive > reports/dotnet-vulnerabilities.txt || true
    
    # Unit tests with coverage
    log_info "Running unit tests with coverage"
    dotnet test --configuration Release --verbosity normal --logger "trx;LogFileName=reports/test-results.trx" --collect:"XPlat Code Coverage" --results-directory reports/ || true
    
    # SonarScanner for .NET (if configured)
    if [ -n "$SONAR_HOST_URL" ] && [ -n "$SONAR_TOKEN" ]; then
        log_info "Running SonarScanner for .NET"
        dotnet sonarscanner begin /k:"$SONAR_PROJECT_KEY" /d:sonar.host.url="$SONAR_HOST_URL" /d:sonar.login="$SONAR_TOKEN" || true
        dotnet build || true
        dotnet sonarscanner end /d:sonar.login="$SONAR_TOKEN" || true
    fi
}

# Run general code quality checks
run_general_checks() {
    log_info "Running general code quality checks"
    
    # Check for large files
    log_info "Checking for large files (>1MB)"
    find . -type f -size +1M -not -path "./.git/*" -not -path "./node_modules/*" -not -path "./target/*" -not -path "./build/*" -not -path "./venv/*" -not -path "./.venv/*" -exec ls -lh {} \; > reports/large-files.txt 2>/dev/null || true
    
    # Check for TODO comments
    log_info "Checking for TODO comments"
    grep -r "TODO" --include="*.java" --include="*.js" --include="*.ts" --include="*.py" --include="*.cs" . > reports/todo-comments.txt 2>/dev/null || true
    
    # Check for FIXME comments
    log_info "Checking for FIXME comments"
    grep -r "FIXME" --include="*.java" --include="*.js" --include="*.ts" --include="*.py" --include="*.cs" . > reports/fixme-comments.txt 2>/dev/null || true
    
    # Check for console.log statements (Node.js/JavaScript)
    if [ -f "package.json" ]; then
        log_info "Checking for console.log statements"
        grep -r "console.log" --include="*.js" --include="*.ts" . > reports/console-logs.txt 2>/dev/null || true
    fi
    
    # Check for print statements (Python)
    if [ -f "requirements.txt" ] || [ -f "pyproject.toml" ]; then
        log_info "Checking for print statements"
        grep -r "print(" --include="*.py" . > reports/print-statements.txt 2>/dev/null || true
    fi
    
    # YAML linting
    log_info "Linting YAML files"
    find . -name "*.yml" -o -name "*.yaml" -not -path "./.git/*" -exec yamllint {} \; > reports/yamllint-report.txt 2>/dev/null || true
    
    # Shell script linting
    log_info "Linting shell scripts"
    find . -name "*.sh" -not -path "./.git/*" -exec shellcheck {} \; > reports/shellcheck-report.txt 2>/dev/null || true
}

# Generate code quality report
generate_report() {
    log_info "Generating code quality report"
    
    local project_type=$(detect_project_type)
    local timestamp=$(date +"%Y-%m-%d %H:%M:%S")
    
    cat > reports/code-quality-report.md << EOF
# Code Quality Report

**Project Type:** ${project_type}  
**Generated:** ${timestamp}  
**Branch:** $(git branch --show-current 2>/dev/null || echo "unknown")  
**Commit:** $(git rev-parse --short HEAD 2>/dev/null || echo "unknown")

## Summary

- **Project Type:** ${project_type}
- **Total Issues:** $(find reports/ -name "*.json" -o -name "*.txt" -o -name "*.xml" | wc -l) reports generated
- **Quality Score:** To be calculated

## Checks Performed

### Code Style & Formatting
- Code style validation
- Formatting checks
- Import organization

### Static Analysis
- Bug detection
- Code smells
- Complexity analysis

### Security
- Vulnerability scanning
- Dependency checking
- Security best practices

### Testing & Coverage
- Unit test execution
- Code coverage analysis
- Test quality assessment

## Detailed Findings

### Java Projects
- Checkstyle: $(if [ -f "reports/checkstyle-result.xml" ]; then echo "✅ Report generated"; else echo "❌ Not run"; fi)
- PMD: $(if [ -f "reports/pmd.xml" ]; then echo "✅ Report generated"; else echo "❌ Not run"; fi)
- SpotBugs: $(if [ -f "reports/spotbugsXml.xml" ]; then echo "✅ Report generated"; else echo "❌ Not run"; fi)
- JaCoCo: $(if [ -f "reports/jacoco.xml" ]; then echo "✅ Report generated"; else echo "❌ Not run"; fi)

### Node.js Projects
- ESLint: $(if [ -f "reports/eslint-report.json" ]; then echo "✅ Report generated"; else echo "❌ Not run"; fi)
- TypeScript: $(if [ -f "reports/tsc-report.txt" ]; then echo "✅ Report generated"; else echo "❌ Not run"; fi)
- Jest Coverage: $(if [ -f "reports/coverage/lcov.info" ]; then echo "✅ Report generated"; else echo "❌ Not run"; fi)

### Python Projects
- Black: $(if [ -f "reports/black-report.txt" ]; then echo "✅ Report generated"; else echo "❌ Not run"; fi)
- Flake8: $(if [ -f "reports/flake8-report.json" ]; then echo "✅ Report generated"; else echo "❌ Not run"; fi)
- Pylint: $(if [ -f "reports/pylint-report.json" ]; then echo "✅ Report generated"; else echo "❌ Not run"; fi)
- Bandit: $(if [ -f "reports/bandit-report.json" ]; then echo "✅ Report generated"; else echo "❌ Not run"; fi)

### .NET Projects
- Security Scan: $(if [ -f "reports/dotnet-vulnerabilities.txt" ]; then echo "✅ Report generated"; else echo "❌ Not run"; fi)
- Test Results: $(if [ -f "reports/test-results.trx" ]; then echo "✅ Report generated"; else echo "❌ Not run"; fi)

## General Checks
- Large Files: $(if [ -f "reports/large-files.txt" ]; then echo "✅ Check completed"; else echo "❌ Not run"; fi)
- TODO Comments: $(if [ -f "reports/todo-comments.txt" ]; then echo "✅ Check completed"; else echo "❌ Not run"; fi)
- YAML Linting: $(if [ -f "reports/yamllint-report.txt" ]; then echo "✅ Check completed"; else echo "❌ Not run"; fi)

## Recommendations

### Critical Issues
1. Address security vulnerabilities immediately
2. Fix build-breaking code style issues
3. Resolve high-priority static analysis findings

### High Priority
1. Improve test coverage to meet minimum standards
2. Address code complexity issues
3. Remove deprecated API usage

### Medium Priority
1. Clean up TODO and FIXME comments
2. Standardize code formatting
3. Optimize import statements

### Low Priority
1. Remove unused code and dependencies
2. Improve documentation
3. Enhance logging consistency

## Next Steps

1. **Review Security Findings**: Check security reports for critical vulnerabilities
2. **Address Code Style Issues**: Fix formatting and style violations
3. **Improve Test Coverage**: Add missing tests to meet coverage targets
4. **Reduce Technical Debt**: Address static analysis warnings
5. **Monitor Dependencies**: Keep dependencies updated and secure

## Quality Metrics

| Metric | Target | Current | Status |
|--------|--------|---------|--------|
| Test Coverage | 80% | TODO | ⚠️ |
| Code Duplication | <5% | TODO | ⚠️ |
| Security Vulnerabilities | 0 | TODO | ⚠️ |
| Code Smells | <10 | TODO | ⚠️ |
| Bugs | 0 | TODO | ⚠️ |

---

*This report was automatically generated by the Code Quality Script.*
EOF

    log_info "Code quality report generated: reports/code-quality-report.md"
}

# Archive reports
archive_reports() {
    log_info "Archiving quality reports"
    
    # Create zip archive of all reports
    if command -v zip &> /dev/null; then
        zip -r code-quality-reports.zip reports/ || true
        log_info "Reports archived: code-quality-reports.zip"
    fi
    
    # Display summary
    echo ""
    echo "📊 Code Quality Check Summary"
    echo "============================"
    echo "Project Type: $(detect_project_type)"
    echo "Reports Generated: $(find reports/ -type f | wc -l)"
    echo "Main Report: reports/code-quality-report.md"
    echo ""
    echo "Next steps:"
    echo "1. Review the code quality report"
    echo "2. Address critical issues first"
    echo "3. Implement recommended improvements"
    echo "4. Re-run checks after fixes"
}

# Main function
main() {
    local project_type=$(detect_project_type)
    
    log_info "Detected project type: $project_type"
    log_info "Starting code quality checks..."
    
    # Run project-specific checks
    case "$project_type" in
        java-maven|java-gradle)
            run_java_checks "$project_type"
            ;;
        nodejs)
            run_nodejs_checks
            ;;
        python)
            run_python_checks
            ;;
        dotnet)
            run_dotnet_checks
            ;;
        *)
            log_warn "Unknown project type: $project_type"
            log_info "Running general checks only"
            ;;
    esac
    
    # Run general checks (always)
    run_general_checks
    
    # Generate comprehensive report
    generate_report
    
    # Archive reports
    archive_reports
    
    log_info "✅ Code quality checks completed"
}

# Run main function
main "$@"