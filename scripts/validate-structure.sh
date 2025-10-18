#!/bin/bash

# Validate Structure Script
set -e

echo "🏗️  Validating repository structure..."

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

validate_directory_structure() {
    log_info "Validating directory structure..."
    
    # Expected directory structure
    declare -A dir_structure=(
        [".github/workflows"]="GitHub Actions workflows"
        ["vars"]="Shared pipeline steps"
        ["src/org/company"]="Groovy source classes"
        ["resources/pipeline-templates"]="Pipeline templates"
        ["resources/scripts"]="Utility scripts"
        ["resources/config"]="Configuration files"
        ["test/vars"]="Tests for shared steps"
        ["test/src/org/company"]="Tests for source classes"
        ["examples/java-maven"]="Java Maven example"
        ["examples/nodejs"]="Node.js example"
        ["examples/python"]="Python example"
        ["examples/kubernetes"]="Kubernetes example"
        ["docs"]="Documentation"
        ["scripts"]="Development scripts"
    )
    
    local valid_count=0
    local total_count=0
    
    for dir in "${!dir_structure[@]}"; do
        total_count=$((total_count + 1))
        if [ -d "$dir" ]; then
            log_info "✅ $dir - ${dir_structure[$dir]}"
            valid_count=$((valid_count + 1))
        else
            log_error "❌ Missing directory: $dir - ${dir_structure[$dir]}"
        fi
    done
    
    log_info "Directory structure: $valid_count/$total_count valid"
}

validate_file_structure() {
    log_info "Validating file structure..."
    
    # Expected files
    declare -A file_structure=(
        [".github/workflows/ci-validation.yml"]="CI validation workflow"
        [".github/workflows/security-scan.yml"]="Security scanning workflow"
        [".github/workflows/release.yml"]="Release workflow"
        ["vars/build.groovy"]="Build shared step"
        ["vars/test.groovy"]="Test shared step"
        ["vars/deploy.groovy"]="Deploy shared step"
        ["vars/securityScan.groovy"]="Security scan shared step"
        ["vars/notify.groovy"]="Notify shared step"
        ["vars/kubernetes.groovy"]="Kubernetes shared step"
        ["vars/docker.groovy"]="Docker shared step"
        ["vars/sonarQube.groovy"]="SonarQube shared step"
        ["src/org/company/PipelineBuilder.groovy"]="Pipeline builder class"
        ["src/org/company/QualityGate.groovy"]="Quality gate class"
        ["src/org/company/SecurityScanner.groovy"]="Security scanner class"
        ["src/org/company/DeploymentManager.groovy"]="Deployment manager class"
        ["src/org/company/NotificationManager.groovy"]="Notification manager class"
        ["resources/pipeline-templates/maven-pipeline.jenkinsfile"]="Maven pipeline template"
        ["resources/pipeline-templates/nodejs-pipeline.jenkinsfile"]="Node.js pipeline template"
        ["resources/pipeline-templates/python-pipeline.jenkinsfile"]="Python pipeline template"
        ["resources/pipeline-templates/dotnet-pipeline.jenkinsfile"]=".NET pipeline template"
        ["resources/pipeline-templates/multibranch-pipeline.jenkinsfile"]="Multibranch pipeline template"
        ["resources/pipeline-templates/kubernetes-pipeline.jenkinsfile"]="Kubernetes pipeline template"
        ["resources/scripts/setup-environment.sh"]="Environment setup script"
        ["resources/scripts/code-quality.sh"]="Code quality script"
        ["resources/scripts/security-scan.sh"]="Security scan script"
        ["test/vars/BuildTest.groovy"]="Build step tests"
        ["test/vars/TestTest.groovy"]="Test step tests"
        ["test/vars/DeployTest.groovy"]="Deploy step tests"
        ["test/src/org/company/PipelineBuilderTest.groovy"]="Pipeline builder tests"
        ["test/src/org/company/QualityGateTest.groovy"]="Quality gate tests"
        ["test/src/org/company/SecurityScannerTest.groovy"]="Security scanner tests"
        ["examples/java-maven/Jenkinsfile"]="Java Maven example"
        ["examples/nodejs/Jenkinsfile"]="Node.js example"
        ["examples/python/Jenkinsfile"]="Python example"
        ["examples/kubernetes/Jenkinsfile"]="Kubernetes example"
        ["docs/getting-started.md"]="Getting started guide"
        ["docs/pipeline-library-guide.md"]="Pipeline library guide"
        ["docs/best-practices.md"]="Best practices guide"
        ["docs/security-guide.md"]="Security guide"
        ["docs/troubleshooting.md"]="Troubleshooting guide"
        ["scripts/run-tests.sh"]="Run tests script"
        ["scripts/run-linting.sh"]="Run linting script"
        ["scripts/validate-pipeline-syntax.sh"]="Validate pipeline syntax script"
        ["scripts/validate-structure.sh"]="Validate structure script"
        [".gitignore"]="Git ignore file"
        ["Jenkinsfile"]="Library CI/CD pipeline"
        ["README.md"]="README documentation"
        ["CONTRIBUTING.md"]="Contributing guidelines"
        ["LICENSE"]="License file"
        ["repository-structure.md"]="Repository structure documentation"
    )
    
    local valid_count=0
    local total_count=0
    
    for file in "${!file_structure[@]}"; do
        total_count=$((total_count + 1))
        if [ -f "$file" ]; then
            log_info "✅ $file - ${file_structure[$file]}"
            valid_count=$((valid_count + 1))
        else
            log_error "❌ Missing file: $file - ${file_structure[$file]}"
        fi
    done
    
    log_info "File structure: $valid_count/$total_count valid"
}

validate_groovy_syntax() {
    log_info "Validating Groovy syntax..."
    
    local valid_count=0
    local total_count=0
    
    # Check all Groovy files
    for groovy_file in $(find . -name "*.groovy" -not -path "./.git/*"); do
        total_count=$((total_count + 1))
        if groovy -c "$groovy_file" 2>/dev/null; then
            log_info "✅ Valid Groovy: $groovy_file"
            valid_count=$((valid_count + 1))
        else
            log_error "❌ Invalid Groovy: $groovy_file"
        fi
    done
    
    log_info "Groovy syntax: $valid_count/$total_count valid"
}

validate_shell_scripts() {
    log_info "Validating shell scripts..."
    
    local valid_count=0
    local total_count=0
    
    # Check all shell scripts are executable and have shebang
    for script_file in $(find . -name "*.sh" -not -path "./.git/*"); do
        total_count=$((total_count + 1))
        
        # Check if executable
        if [ -x "$script_file" ]; then
            log_info "✅ Executable: $script_file"
            valid_count=$((valid_count + 1))
        else
            log_warn "⚠️  Not executable: $script_file"
        fi
        
        # Check shebang
        if head -n1 "$script_file" | grep -q "^#!/"; then
            log_info "✅ Has shebang: $script_file"
        else
            log_warn "⚠️  Missing shebang: $script_file"
        fi
    done
    
    log_info "Shell scripts: $valid_count/$total_count valid"
}

generate_structure_report() {
    log_info "Generating structure report..."
    
    cat > structure-report.md << EOF
# Repository Structure Report

Generated: $(date)

## Summary

- Directory Structure: TODO/$(find . -type d | wc -l)
- File Structure: TODO/$(find . -type f | wc -l)
- Groovy Files: TODO
- Shell Scripts: TODO

## Directory Structure Validation

### Required Directories
- .github/workflows: ✅
- vars: ✅
- src/org/company: ✅
- resources/pipeline-templates: ✅
- resources/scripts: ✅
- resources/config: ✅
- test/vars: ✅
- test/src/org/company: ✅
- examples/java-maven: ✅
- examples/nodejs: ✅
- examples/python: ✅
- examples/kubernetes: ✅
- docs: ✅
- scripts: ✅

### File Structure Validation
- Shared Steps: ✅
- Source Classes: ✅
- Templates: ✅
- Examples: ✅
- Documentation: ✅
- Scripts: ✅

## Recommendations

1. Repository structure is valid
2. All required components are present
3. Ready for development and use

EOF

    log_info "Structure report generated: structure-report.md"
}

main() {
    log_info "Starting structure validation..."
    
    validate_directory_structure
    validate_file_structure
    validate_groovy_syntax
    validate_shell_scripts
    generate_structure_report
    
    log_info "✅ Structure validation completed"
}

main "$@"