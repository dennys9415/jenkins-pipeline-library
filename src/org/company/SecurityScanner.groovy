package org.company

/**
 * SecurityScanner - Security scanning and vulnerability management
 */
class SecurityScanner implements Serializable {
    private final steps
    
    SecurityScanner(steps) {
        this.steps = steps
    }
    
    def scanDependencies(Map params = [:]) {
        def config = [
            language: params.language ?: 'java',
            failOnVulnerabilities: params.failOnVulnerabilities ?: true,
            severityThreshold: params.severityThreshold ?: 'HIGH'
        ]
        
        steps.echo "Scanning dependencies for ${config.language}"
        
        def vulnerabilities = []
        
        switch(config.language) {
            case 'java':
                vulnerabilities.addAll(scanJavaDependencies(config))
                break
            case 'nodejs':
                vulnerabilities.addAll(scanNodeJSDependencies(config))
                break
            case 'python':
                vulnerabilities.addAll(scanPythonDependencies(config))
                break
        }
        
        def criticalVulnerabilities = vulnerabilities.findAll { 
            it.severity in ['CRITICAL', 'HIGH'] 
        }
        
        if (criticalVulnerabilities.size() > 0 && config.failOnVulnerabilities) {
            def message = "Found ${criticalVulnerabilities.size()} critical/high vulnerabilities:\n"
            criticalVulnerabilities.each { vuln ->
                message += "- ${vuln.name}: ${vuln.severity} - ${vuln.description}\n"
            }
            steps.error message
        }
        
        return vulnerabilities
    }
    
    def scanJavaDependencies(Map config) {
        def vulnerabilities = []
        
        try {
            if (steps.fileExists('pom.xml')) {
                steps.sh 'mvn org.owasp:dependency-check-maven:check -Dformat=JSON -DoutputDirectory=reports'
                
                if (steps.fileExists('reports/dependency-check-report.json')) {
                    def report = steps.readJSON file: 'reports/dependency-check-report.json'
                    vulnerabilities = parseDependencyCheckReport(report)
                }
            }
        } catch (Exception e) {
            steps.echo "Failed to scan Java dependencies: ${e.message}"
        }
        
        return vulnerabilities
    }
    
    def scanNodeJSDependencies(Map config) {
        def vulnerabilities = []
        
        try {
            steps.sh 'npm audit --json > reports/npm-audit.json || true'
            
            if (steps.fileExists('reports/npm-audit.json')) {
                def report = steps.readJSON file: 'reports/npm-audit.json'
                vulnerabilities = parseNpmAuditReport(report)
            }
        } catch (Exception e) {
            steps.echo "Failed to scan Node.js dependencies: ${e.message}"
        }
        
        return vulnerabilities
    }
    
    def scanPythonDependencies(Map config) {
        def vulnerabilities = []
        
        try {
            steps.sh '. venv/bin/activate && pip-audit --format json --output reports/pip-audit.json || true'
            
            if (steps.fileExists('reports/pip-audit.json')) {
                def report = steps.readJSON file: 'reports/pip-audit.json'
                vulnerabilities = parsePipAuditReport(report)
            }
        } catch (Exception e) {
            steps.echo "Failed to scan Python dependencies: ${e.message}"
        }
        
        return vulnerabilities
    }
    
    def parseDependencyCheckReport(Map report) {
        def vulnerabilities = []
        
        report.dependencies?.each { dependency ->
            dependency.vulnerabilities?.each { vuln ->
                vulnerabilities.add([
                    name: dependency.fileName,
                    severity: vuln.severity ?: 'UNKNOWN',
                    description: vuln.name,
                    cvssScore: vuln.cvssv3?.baseScore ?: vuln.cvssv2?.score ?: 0.0
                ])
            }
        }
        
        return vulnerabilities
    }
    
    def parseNpmAuditReport(Map report) {
        def vulnerabilities = []
        
        report.vulnerabilities?.each { name, vuln ->
            vulnerabilities.add([
                name: name,
                severity: vuln.severity ?: 'UNKNOWN',
                description: vuln.name,
                cvssScore: vuln.cvssScore ?: 0.0
            ])
        }
        
        return vulnerabilities
    }
    
    def parsePipAuditReport(Map report) {
        def vulnerabilities = []
        
        report.vulnerabilities?.each { vuln ->
            vulnerabilities.add([
                name: vuln.name,
                severity: vuln.severity ?: 'UNKNOWN',
                description: vuln.description,
                cvssScore: vuln.cvssScore ?: 0.0
            ])
        }
        
        return vulnerabilities
    }
    
    def scanContainer(String imageName) {
        steps.echo "Scanning container image: ${imageName}"
        
        def vulnerabilities = []
        
        try {
            steps.sh """
                trivy image --format json --output reports/trivy-container.json ${imageName} || true
            """
            
            if (steps.fileExists('reports/trivy-container.json')) {
                def report = steps.readJSON file: 'reports/trivy-container.json'
                vulnerabilities = parseTrivyReport(report)
            }
        } catch (Exception e) {
            steps.echo "Failed to scan container: ${e.message}"
        }
        
        return vulnerabilities
    }
    
    def parseTrivyReport(Map report) {
        def vulnerabilities = []
        
        report.Results?.each { result ->
            result.Vulnerabilities?.each { vuln ->
                vulnerabilities.add([
                    name: vuln.PkgName,
                    severity: vuln.Severity ?: 'UNKNOWN',
                    description: vuln.Title,
                    cvssScore: vuln.CVSS?.nvd?.V3Score ?: vuln.CVSS?.nvd?.V2Score ?: 0.0,
                    fixedVersion: vuln.FixedVersion
                ])
            }
        }
        
        return vulnerabilities
    }
    
    def generateSecurityReport(List vulnerabilities) {
        def criticalCount = vulnerabilities.count { it.severity == 'CRITICAL' }
        def highCount = vulnerabilities.count { it.severity == 'HIGH' }
        def mediumCount = vulnerabilities.count { it.severity == 'MEDIUM' }
        def lowCount = vulnerabilities.count { it.severity == 'LOW' }
        
        def report = """
# Security Scan Report

## Summary
- Critical: ${criticalCount}
- High: ${highCount}
- Medium: ${mediumCount}
- Low: ${lowCount}
- Total: ${vulnerabilities.size()}

## Critical/High Vulnerabilities
"""
        
        vulnerabilities.findAll { it.severity in ['CRITICAL', 'HIGH'] }.each { vuln ->
            report += """
### ${vuln.name}
- **Severity**: ${vuln.severity}
- **CVSS Score**: ${vuln.cvssScore}
- **Description**: ${vuln.description}
- **Fixed Version**: ${vuln.fixedVersion ?: 'Not available'}

"""
        }
        
        steps.writeFile file: 'security-report.md', text: report
        steps.archiveArtifacts artifacts: 'security-report.md'
        
        return report
    }
}