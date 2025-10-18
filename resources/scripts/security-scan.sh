#!/bin/bash

# Security Scan Script
set -e

echo "🛡️  Running security scans..."

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

# Run dependency vulnerability scanning
run_dependency_scan() {
    local project_type=$1
    
    log_info "Running dependency vulnerability scan for $project_type"
    
    case "$project_type" in
        java-maven)
            # OWASP Dependency Check
            if command -v dependency-check.sh &> /dev/null; then
                log_info "Running OWASP Dependency Check"
                dependency-check.sh \
                    --project "Java-Maven-Project" \
                    --scan . \
                    --format HTML \
                    --format JSON \
                    --out reports/dependency-check \
                    --enableRetired \
                    --failOnCVSS 7 || true
            else
                log_warn "OWASP Dependency Check not installed"
            fi
            
            # Snyk for Java
            if command -v snyk &> /dev/null && [ -n "$SNYK_TOKEN" ]; then
                log_info "Running Snyk test"
                snyk test --all-projects --org=my-org --json-file-output=reports/snyk-dependencies.json || true
            fi
            ;;
        java-gradle)
            if command -v dependency-check.sh &> /dev/null; then
                log_info "Running OWASP Dependency Check"
                dependency-check.sh \
                    --project "Java-Gradle-Project" \
                    --scan . \
                    --format HTML \
                    --format JSON \
                    --out reports/dependency-check \
                    --enableRetired \
                    --failOnCVSS 7 || true
            fi
            ;;
        nodejs)
            # npm audit
            log_info "Running npm audit"
            npm audit --json > reports/npm-audit.json 2>/dev/null || true
            
            # npm audit fix (dry run)
            log_info "Running npm audit fix (dry run)"
            npm audit fix --dry-run --json > reports/npm-audit-fix.json 2>/dev/null || true
            
            # Snyk for Node.js
            if command -v snyk &> /dev/null && [ -n "$SNYK_TOKEN" ]; then
                log_info "Running Snyk test"
                snyk test --json-file-output=reports/snyk-dependencies.json || true
            fi
            
            # Check for known vulnerabilities in dependencies
            log_info "Checking for known vulnerabilities"
            npx check-node-version --package 2>/dev/null || true
            ;;
        python)
            # pip-audit
            log_info "Running pip-audit"
            if command -v pip-audit &> /dev/null; then
                pip-audit --format json --output reports/pip-audit.json || true
            else
                log_warn "pip-audit not installed"
            fi
            
            # Safety check
            log_info "Running Safety check"
            if command -v safety &> /dev/null; then
                safety check --json --output reports/safety-report.json || true
            else
                log_warn "safety not installed"
            fi
            
            # Bandit security scan
            log_info "Running Bandit security scan"
            if command -v bandit &> /dev/null; then
                bandit -r . -f json -o reports/bandit-report.json || true
            fi
            
            # Snyk for Python
            if command -v snyk &> /dev/null && [ -n "$SNYK_TOKEN" ]; then
                log_info "Running Snyk test"
                snyk test --command=venv/bin/python --json-file-output=reports/snyk-dependencies.json || true
            fi
            ;;
        dotnet)
            log_info "Running .NET dependency vulnerability scan"
            dotnet list package --vulnerable --include-transitive > reports/dotnet-vulnerabilities.txt || true
            
            # Security Code Scan
            if command -v security-scan &> /dev/null; then
                log_info "Running Security Code Scan"
                dotnet build /p:SecurityCodeScanConfig=security-scan.config || true
            fi
            ;;
    esac
}

# Run SAST (Static Application Security Testing)
run_sast_scan() {
    local project_type=$1
    
    log_info "Running SAST scan for $project_type"
    
    case "$project_type" in
        java-maven|java-gradle)
            # SpotBugs Security
            if [ "$project_type" = "java-maven" ]; then
                log_info "Running SpotBugs Security"
                mvn spotbugs:spotbugs -Dspotbugs.failOnError=false || true
                if [ -f "target/spotbugsXml.xml" ]; then
                    cp target/spotbugsXml.xml reports/spotbugs-security.xml
                fi
            fi
            
            # PMD CPD (Copy-Paste Detection)
            log_info "Running PMD CPD"
            mvn pmd:cpd-check -Dpmd.failOnViolation=false || true
            
            # Checkstyle security rules
            log_info "Running Checkstyle security rules"
            mvn checkstyle:checkstyle -Dcheckstyle.failOnViolation=false || true
            
            # SonarQube security analysis
            if [ -n "$SONAR_HOST_URL" ] && [ -n "$SONAR_TOKEN" ]; then
                log_info "Running SonarQube security analysis"
                mvn sonar:sonar -Dsonar.security.sources=java -Dsonar.host.url=$SONAR_HOST_URL -Dsonar.login=$SONAR_TOKEN || true
            fi
            ;;
        nodejs)
            # ESLint security rules
            log_info "Running ESLint security rules"
            if [ -f ".eslintrc.js" ] || [ -f ".eslintrc.json" ]; then
                npx eslint . --ext .js,.jsx,.ts,.tsx --config @eslint/js --rule 'security/detect-object-injection: error' --format json --output-file reports/eslint-security.json || true
            fi
            
            # NodeJSScan
            log_info "Running NodeJSScan"
            if command -v nodejsscan &> /dev/null; then
                nodejsscan --directory . --output reports/nodejsscan-report.json || true
            fi
            
            # Semgrep
            log_info "Running Semgrep"
            if command -v semgrep &> /dev/null; then
                semgrep --config=auto --json --output reports/semgrep-report.json . || true
            fi
            ;;
        python)
            # Bandit SAST
            log_info "Running Bandit SAST"
            if command -v bandit &> /dev/null; then
                bandit -r . -f json -o reports/bandit-sast.json || true
            fi
            
            # Safety SAST
            log_info "Running Safety SAST"
            if command -v safety &> /dev/null; then
                safety scan --json --output reports/safety-sast.json || true
            fi
            
            # Semgrep for Python
            log_info "Running Semgrep for Python"
            if command -v semgrep &> /dev/null; then
                semgrep --config=p/python --json --output reports/semgrep-python.json . || true
            fi
            
            # Pylint security
            log_info "Running Pylint security checks"
            find . -name "*.py" -not -path "./venv/*" -not -path "./.venv/*" -exec python -m pylint {} --disable=all --enable=security --output-format=json:reports/pylint-security.json \; || true
            ;;
        dotnet)
            # Security Code Scan
            log_info "Running Security Code Scan"
            dotnet build /p:SecurityCodeScanConfig=security-scan.config || true
            
            # Roslyn Security Guard
            log_info "Running Roslyn Security Guard"
            dotnet build /p:RoslynSecurityGuardConfig=security-guard.config || true
            ;;
    esac
}

# Run container security scanning
run_container_scan() {
    log_info "Running container security scanning"
    
    local image_name="${1:-${DOCKER_IMAGE:-$JOB_NAME}}"
    local image_tag="${2:-${DOCKER_TAG:-latest}}"
    local full_image="${image_name}:${image_tag}"
    
    if command -v docker &> /dev/null && command -v trivy &> /dev/null; then
        # Check if image exists
        if docker image inspect "$full_image" &> /dev/null; then
            # Trivy vulnerability scan
            log_info "Running Trivy vulnerability scan"
            trivy image --format json --output reports/trivy-container.json "$full_image" || true
            trivy image --severity HIGH,CRITICAL --exit-code 0 "$full_image" || true
            
            # Trivy configuration scan
            log_info "Running Trivy configuration scan"
            trivy image --security-checks config --format json --output reports/trivy-config.json "$full_image" || true
            
            # Docker Scout
            log_info "Running Docker Scout"
            if command -v docker &> /dev/null && docker scout &> /dev/null; then
                docker scout cves "$full_image" --format sarif --output reports/docker-scout.sarif || true
            fi
            
            # Grype
            log_info "Running Grype scan"
            if command -v grype &> /dev/null; then
                grype "$full_image" -o json --file reports/grype-container.json || true
            fi
        else
            log_warn "Docker image $full_image not found, skipping container scan"
        fi
    else
        log_warn "Docker or Trivy not available, skipping container scan"
    fi
}

# Run DAST (Dynamic Application Security Testing)
run_dast_scan() {
    log_info "Running DAST scan"
    
    # OWASP ZAP baseline scan
    if command -v docker &> /dev/null; then
        log_info "Running OWASP ZAP baseline scan"
        
        # Start target application if needed
        if [ -f "docker-compose.yml" ]; then
            log_info "Starting application with Docker Compose"
            docker-compose up -d
            sleep 30  # Wait for application to start
        fi
        
        # Run ZAP scan
        docker run --rm -v $(pwd)/reports:/zap/wrk/:rw -t owasp/zap2docker-stable zap-baseline.py \
            -t http://localhost:8080 \
            -g gen.conf \
            -r zap-report.html \
            -x zap-report.xml || true
        
        # Stop application if started
        if [ -f "docker-compose.yml" ]; then
            docker-compose down
        fi
    else
        log_warn "Docker not available, skipping DAST scan"
    fi
    
    # Nuclei scan
    if command -v nuclei &> /dev/null; then
        log_info "Running Nuclei scan"
        nuclei -u http://localhost:8080 -o reports/nuclei-scan.json -json || true
    fi
}

# Run infrastructure as code security scanning
run_iac_scan() {
    log_info "Running Infrastructure as Code security scanning"
    
    # Check for Kubernetes manifests
    if find . -name "*.yaml" -o -name "*.yml" | grep -q .; then
        log_info "Scanning Kubernetes manifests"
        
        # Kubeaudit
        if command -v kubeaudit &> /dev/null; then
            find . -name "*.yaml" -o -name "*.yml" -exec kubeaudit all -f {} \; > reports/kubeaudit-report.txt 2>/dev/null || true
        fi
        
        # Kubesec
        if command -v kubesec &> /dev/null; then
            find . -name "*.yaml" -o -name "*.yml" -exec kubesec scan {} \; > reports/kubesec-report.txt 2>/dev/null || true
        fi
        
        # Checkov for Kubernetes
        if command -v checkov &> /dev/null; then
            checkov -d . --framework kubernetes -o json > reports/checkov-kubernetes.json 2>/dev/null || true
        fi
    fi
    
    # Check for Terraform files
    if find . -name "*.tf" | grep -q .; then
        log_info "Scanning Terraform files"
        
        # Tfsec
        if command -v tfsec &> /dev/null; then
            tfsec . --format json --out reports/tfsec-report.json 2>/dev/null || true
        fi
        
        # Checkov for Terraform
        if command -v checkov &> /dev/null; then
            checkov -d . --framework terraform -o json > reports/checkov-terraform.json 2>/dev/null || true
        fi
        
        # Terrascan
        if command -v terrascan &> /dev/null; then
            terrascan scan -i terraform -o json > reports/terrascan-report.json 2>/dev/null || true
        fi
    fi
    
    # Check for Dockerfiles
    if find . -name "Dockerfile*" | grep -q .; then
        log_info "Scanning Dockerfiles"
        
        # Hadolint
        if command -v hadolint &> /dev/null; then
            find . -name "Dockerfile*" -exec hadolint {} \; > reports/hadolint-report.txt 2>/dev/null || true
        fi
        
        # Dockle
        if command -v dockle &> /dev/null; then
            find . -name "Dockerfile*" -exec dockle {} \; > reports/dockle-report.txt 2>/dev/null || true
        fi
    fi
}

# Run secrets detection
run_secrets_detection() {
    log_info "Running secrets detection"
    
    # Gitleaks
    if command -v gitleaks &> /dev/null; then
        log_info "Running Gitleaks"
        gitleaks detect --source . --report-format json --report-path reports/gitleaks-report.json || true
    fi
    
    # TruffleHog
    if command -v trufflehog &> /dev/null; then
        log_info "Running TruffleHog"
        trufflehog filesystem . --json > reports/trufflehog-report.json 2>/dev/null || true
    fi
    
    # Git secrets
    if command -v git-secrets &> /dev/null; then
        log_info "Running git-secrets"
        git-secrets --scan > reports/git-secrets-report.txt 2>/dev/null || true
    fi
}

# Generate security report
generate_security_report() {
    log_info "Generating security report"
    
    local project_type=$(detect_project_type)
    local timestamp=$(date +"%Y-%m-%d %H:%M:%S")
    
    # Count vulnerabilities by severity
    local critical_count=0
    local high_count=0
    local medium_count=0
    local low_count=0
    
    # Parse Trivy report if exists
    if [ -f "reports/trivy-container.json" ]; then
        critical_count=$((critical_count + $(jq '.Results[].Vulnerabilities[]? | select(.Severity == "CRITICAL") | .VulnerabilityID' reports/trivy-container.json 2>/dev/null | wc -l || echo 0)))
        high_count=$((high_count + $(jq '.Results[].Vulnerabilities[]? | select(.Severity == "HIGH") | .VulnerabilityID' reports/trivy-container.json 2>/dev/null | wc -l || echo 0)))
    fi
    
    # Parse dependency check report if exists
    if [ -f "reports/dependency-check/dependency-check-report.json" ]; then
        critical_count=$((critical_count + $(jq '.dependencies[].vulnerabilities[]? | select(.severity == "CRITICAL") | .name' reports/dependency-check/dependency-check-report.json 2>/dev/null | wc -l || echo 0)))
        high_count=$((high_count + $(jq '.dependencies[].vulnerabilities[]? | select(.severity == "HIGH") | .name' reports/dependency-check/dependency-check-report.json 2>/dev/null | wc -l || echo 0)))
    fi
    
    cat > reports/security-report.md << EOF
# Security Scan Report

**Project Type:** ${project_type}  
**Generated:** ${timestamp}  
**Branch:** $(git branch --show-current 2>/dev/null || echo "unknown")  
**Commit:** $(git rev-parse --short HEAD 2>/dev/null || echo "unknown")

## Executive Summary

- **Total Scans:** $(find reports/ -name "*.json" -o -name "*.html" -o -name "*.xml" -o -name "*.txt" | wc -l)
- **Critical Vulnerabilities:** ${critical_count}
- **High Vulnerabilities:** ${high_count}
- **Medium Vulnerabilities:** ${medium_count}
- **Low Vulnerabilities:** ${low_count}
- **Security Score:** $(if [ $critical_count -eq 0 ] && [ $high_count -eq 0 ]; then echo "✅ Good"; elif [ $critical_count -eq 0 ]; then echo "⚠️  Needs Attention"; else echo "❌ Critical"; fi)

## Scan Results

### Dependency Security
$(if [ -f "reports/dependency-check/dependency-check-report.html" ]; then echo "- ✅ OWASP Dependency Check: [View Report](reports/dependency-check/dependency-check-report.html)"; else echo "- ❌ OWASP Dependency Check: Not run"; fi)
$(if [ -f "reports/snyk-dependencies.json" ]; then echo "- ✅ Snyk Dependency Scan: [View Report](reports/snyk-dependencies.json)"; else echo "- ❌ Snyk Dependency Scan: Not run"; fi)
$(if [ -f "reports/npm-audit.json" ]; then echo "- ✅ npm Audit: [View Report](reports/npm-audit.json)"; else echo "- ❌ npm Audit: Not run"; fi)
$(if [ -f "reports/pip-audit.json" ]; then echo "- ✅ pip-audit: [View Report](reports/pip-audit.json)"; else echo "- ❌ pip-audit: Not run"; fi)

### Static Application Security Testing (SAST)
$(if [ -f "reports/spotbugs-security.xml" ]; then echo "- ✅ SpotBugs Security: [View Report](reports/spotbugs-security.xml)"; else echo "- ❌ SpotBugs Security: Not run"; fi)
$(if [ -f "reports/bandit-sast.json" ]; then echo "- ✅ Bandit SAST: [View Report](reports/bandit-sast.json)"; else echo "- ❌ Bandit SAST: Not run"; fi)
$(if [ -f "reports/semgrep-report.json" ]; then echo "- ✅ Semgrep: [View Report](reports/semgrep-report.json)"; else echo "- ❌ Semgrep: Not run"; fi)

### Container Security
$(if [ -f "reports/trivy-container.json" ]; then echo "- ✅ Trivy Container Scan: [View Report](reports/trivy-container.json)"; else echo "- ❌ Trivy Container Scan: Not run"; fi)
$(if [ -f "reports/grype-container.json" ]; then echo "- ✅ Grype Container Scan: [View Report](reports/grype-container.json)"; else echo "- ❌ Grype Container Scan: Not run"; fi)
$(if [ -f "reports/hadolint-report.txt" ]; then echo "- ✅ Dockerfile Lint: [View Report](reports/hadolint-report.txt)"; else echo "- ❌ Dockerfile Lint: Not run"; fi)

### Infrastructure as Code Security
$(if [ -f "reports/checkov-kubernetes.json" ]; then echo "- ✅ Checkov Kubernetes: [View Report](reports/checkov-kubernetes.json)"; else echo "- ❌ Checkov Kubernetes: Not run"; fi)
$(if [ -f "reports/tfsec-report.json" ]; then echo "- ✅ Tfsec Terraform: [View Report](reports/tfsec-report.json)"; else echo "- ❌ Tfsec Terraform: Not run"; fi)

### Secrets Detection
$(if [ -f "reports/gitleaks-report.json" ]; then echo "- ✅ Gitleaks: [View Report](reports/gitleaks-report.json)"; else echo "- ❌ Gitleaks: Not run"; fi)
$(if [ -f "reports/trufflehog-report.json" ]; then echo "- ✅ TruffleHog: [View Report](reports/trufflehog-report.json)"; else echo "- ❌ TruffleHog: Not run"; fi)

### Dynamic Application Security Testing (DAST)
$(if [ -f "reports/zap-report.html" ]; then echo "- ✅ OWASP ZAP: [View Report](reports/zap-report.html)"; else echo "- ❌ OWASP ZAP: Not run"; fi)

## Critical Findings

$(if [ $critical_count -gt 0 ]; then
echo "### 🚨 Critical Vulnerabilities Found"
echo ""
echo "| Component | Vulnerability | Description |"
echo "|-----------|---------------|-------------|"
# Extract critical vulnerabilities from reports
if [ -f "reports/trivy-container.json" ]; then
    jq -r '.Results[]?.Vulnerabilities[]? | select(.Severity == "CRITICAL") | "| Container | \(.VulnerabilityID) | \(.Title) |"' reports/trivy-container.json 2>/dev/null || true
fi
if [ -f "reports/dependency-check/dependency-check-report.json" ]; then
    jq -r '.dependencies[]?.vulnerabilities[]? | select(.severity == "CRITICAL") | "| Dependency | \(.name) | \(.description) |"' reports/dependency-check/dependency-check-report.json 2>/dev/null || true
fi
else
echo "### ✅ No Critical Vulnerabilities Found"
fi)

## High Priority Findings

$(if [ $high_count -gt 0 ]; then
echo "### ⚠️ High Priority Vulnerabilities Found"
echo ""
echo "| Component | Vulnerability | Description |"
echo "|-----------|---------------|-------------|"
# Extract high vulnerabilities from reports
if [ -f "reports/trivy-container.json" ]; then
    jq -r '.Results[]?.Vulnerabilities[]? | select(.Severity == "HIGH") | "| Container | \(.VulnerabilityID) | \(.Title) |"' reports/trivy-container.json 2>/dev/null || true
fi
if [ -f "reports/dependency-check/dependency-check-report.json" ]; then
    jq -r '.dependencies[]?.vulnerabilities[]? | select(.severity == "HIGH") | "| Dependency | \(.name) | \(.description) |"' reports/dependency-check/dependency-check-report.json 2>/dev/null || true
fi
else
echo "### ✅ No High Priority Vulnerabilities Found"
fi)

## Recommendations

### Immediate Actions (Critical/High)
1. **Patch Critical Vulnerabilities**: Address all critical and high severity vulnerabilities immediately
2. **Update Dependencies**: Upgrade vulnerable dependencies to patched versions
3. **Container Base Images**: Use updated base images with security patches
4. **Secrets Rotation**: Rotate any exposed secrets immediately

### Short-term Actions (Medium)
1. **Security Configuration**: Fix security misconfigurations in infrastructure
2. **Code Security**: Address SAST findings in the codebase
3. **Access Controls**: Review and tighten access controls

### Long-term Actions
1. **Security Training**: Conduct security awareness training for developers
2. **Security Gates**: Implement security gates in CI/CD pipeline
3. **Monitoring**: Set up security monitoring and alerting
4. **Threat Modeling**: Conduct regular threat modeling sessions

## Security Metrics

| Metric | Target | Current | Status |
|--------|--------|---------|--------|
| Critical Vulnerabilities | 0 | ${critical_count} | $(if [ $critical_count -eq 0 ]; then echo "✅"; else echo "❌"; fi) |
| High Vulnerabilities | 0 | ${high_count} | $(if [ $high_count -eq 0 ]; then echo "✅"; else echo "❌"; fi) |
| Dependencies Scanned | 100% | TODO | ⚠️ |
| SAST Coverage | 100% | TODO | ⚠️ |
| Container Security | Pass | $(if [ $critical_count -eq 0 ] && [ $high_count -eq 0 ]; then echo "Pass"; else echo "Fail"; fi) | $(if [ $critical_count -eq 0 ] && [ $high_count -eq 0 ]; then echo "✅"; else echo "❌"; fi) |

## Next Steps

1. **Review Critical Findings**: Address all critical vulnerabilities within 24 hours
2. **Create Remediation Plan**: Develop a plan for addressing high and medium severity issues
3. **Update Dependencies**: Upgrade all vulnerable dependencies
4. **Security Review**: Conduct a security code review for identified issues
5. **Monitor**: Set up continuous security monitoring

---

*This report was automatically generated by the Security Scan Script.*  
*For security concerns, contact the security team immediately.*
EOF

    log_info "Security report generated: reports/security-report.md"
}

# Archive security reports
archive_reports() {
    log_info "Archiving security reports"
    
    # Create zip archive of all reports
    if command -v zip &> /dev/null; then
        zip -r security-scan-reports.zip reports/ || true
        log_info "Reports archived: security-scan-reports.zip"
    fi
    
    # Display summary
    echo ""
    echo "🛡️  Security Scan Summary"
    echo "========================"
    echo "Project Type: $(detect_project_type)"
    echo "Total Reports: $(find reports/ -type f | wc -l)"
    echo "Critical Vulnerabilities: $critical_count"
    echo "High Vulnerabilities: $high_count"
    echo "Main Report: reports/security-report.md"
    echo ""
    echo "Next steps:"
    echo "1. Review the security report immediately"
    echo "2. Address critical vulnerabilities within 24 hours"
    echo "3. Create a remediation plan for other findings"
    echo "4. Implement security gates in your pipeline"
}

# Main function
main() {
    local project_type=$(detect_project_type)
    
    log_info "Detected project type: $project_type"
    log_info "Starting comprehensive security scan..."
    
    # Run security scans
    run_dependency_scan "$project_type"
    run_sast_scan "$project_type"
    run_container_scan
    run_iac_scan
    run_secrets_detection
    run_dast_scan
    
    # Generate comprehensive report
    generate_security_report
    
    # Archive reports
    archive_reports
    
    log_info "✅ Security scans completed"
    
    # Exit with appropriate code based on critical vulnerabilities
    if [ $critical_count -gt 0 ]; then
        log_error "🚨 Critical vulnerabilities found! Please address immediately."
        exit 1
    elif [ $high_count -gt 0 ]; then
        log_warn "⚠️  High priority vulnerabilities found. Please review and address."
        exit 0
    else
        log_info "✅ No critical or high priority vulnerabilities found."
        exit 0
    fi
}

# Run main function
main "$@"