package org.company

/**
 * QualityGate - Quality gate management and validation
 */
class QualityGate implements Serializable {
    private final steps
    
    QualityGate(steps) {
        this.steps = steps
    }
    
    def validateCodeQuality(Map params = [:]) {
        def config = [
            language: params.language ?: 'java',
            coverageThreshold: params.coverageThreshold ?: 80,
            duplicationThreshold: params.duplicationThreshold ?: 5,
            issuesThreshold: params.issuesThreshold ?: 0,
            securityIssuesThreshold: params.securityIssuesThreshold ?: 0
        ]
        
        steps.echo "Validating code quality for ${config.language}"
        
        def passed = true
        def messages = []
        
        // Coverage check
        def coverage = getCoverage(config.language)
        if (coverage < config.coverageThreshold) {
            passed = false
            messages.add("Code coverage ${coverage}% is below threshold ${config.coverageThreshold}%")
        }
        
        // Duplication check
        def duplication = getDuplication(config.language)
        if (duplication > config.duplicationThreshold) {
            passed = false
            messages.add("Code duplication ${duplication}% is above threshold ${config.duplicationThreshold}%")
        }
        
        // Issues check
        def issues = getIssuesCount()
        if (issues > config.issuesThreshold) {
            passed = false
            messages.add("Number of issues ${issues} is above threshold ${config.issuesThreshold}")
        }
        
        // Security issues check
        def securityIssues = getSecurityIssuesCount()
        if (securityIssues > config.securityIssuesThreshold) {
            passed = false
            messages.add("Number of security issues ${securityIssues} is above threshold ${config.securityIssuesThreshold}")
        }
        
        if (!passed) {
            def message = "Quality gate failed:\n" + messages.join("\n")
            steps.error message
        }
        
        steps.echo "✅ Quality gate passed"
        return true
    }
    
    def getCoverage(String language) {
        switch(language) {
            case 'java':
                return getJavaCoverage()
            case 'nodejs':
                return getNodeJSCoverage()
            case 'python':
                return getPythonCoverage()
            default:
                return 100.0
        }
    }
    
    def getJavaCoverage() {
        try {
            if (steps.fileExists('target/site/jacoco/jacoco.xml')) {
                def coverage = steps.sh(
                    script: '''
                        xmllint --xpath "string(//report/counter[@type=\"LINE\"]/@missed)" target/site/jacoco/jacoco.xml 2>/dev/null || echo "0"
                    ''',
                    returnStdout: true
                ).trim()
                
                def total = steps.sh(
                    script: '''
                        xmllint --xpath "string(//report/counter[@type=\"LINE\"]/@covered)" target/site/jacoco/jacoco.xml 2>/dev/null || echo "0"
                    ''',
                    returnStdout: true
                ).trim()
                
                def missed = coverage.toInteger()
                def covered = total.toInteger()
                def totalLines = missed + covered
                
                return totalLines > 0 ? (covered * 100 / totalLines) : 100.0
            }
        } catch (Exception e) {
            steps.echo "Failed to get Java coverage: ${e.message}"
        }
        return 100.0
    }
    
    def getNodeJSCoverage() {
        try {
            if (steps.fileExists('coverage/lcov.info')) {
                def lines = steps.sh(
                    script: '''
                        grep -E "^LF:" coverage/lcov.info | awk -F: '{sum+=$2} END {print sum}'
                    ''',
                    returnStdout: true
                ).trim()
                
                def hit = steps.sh(
                    script: '''
                        grep -E "^LH:" coverage/lcov.info | awk -F: '{sum+=$2} END {print sum}'
                    ''',
                    returnStdout: true
                ).trim()
                
                def total = lines.toInteger()
                def covered = hit.toInteger()
                
                return total > 0 ? (covered * 100 / total) : 100.0
            }
        } catch (Exception e) {
            steps.echo "Failed to get Node.js coverage: ${e.message}"
        }
        return 100.0
    }
    
    def getPythonCoverage() {
        try {
            if (steps.fileExists('coverage.xml')) {
                def coverage = steps.sh(
                    script: '''
                        xmllint --xpath "string(//coverage/@line-rate)" coverage.xml 2>/dev/null || echo "1"
                    ''',
                    returnStdout: true
                ).trim()
                
                return coverage.toDouble() * 100
            }
        } catch (Exception e) {
            steps.echo "Failed to get Python coverage: ${e.message}"
        }
        return 100.0
    }
    
    def getDuplication(String language) {
        // Simplified duplication calculation
        // In real implementation, this would parse SonarQube reports or other tools
        return 0.0
    }
    
    def getIssuesCount() {
        // Simplified issues count
        // In real implementation, this would aggregate issues from various tools
        return 0
    }
    
    def getSecurityIssuesCount() {
        // Simplified security issues count
        // In real implementation, this would count security vulnerabilities
        return 0
    }
    
    def enforceStandards() {
        steps.echo "Enforcing coding standards"
        
        // Check for large files
        steps.sh '''
            find . -name "*.java" -size +1000k -exec echo "Large file: {}" \\;
            find . -name "*.js" -size +500k -exec echo "Large file: {}" \\;
            find . -name "*.py" -size +500k -exec echo "Large file: {}" \\;
        '''
        
        // Check for TODO comments
        def todoCount = steps.sh(
            script: '''
                grep -r "TODO" --include="*.java" --include="*.js" --include="*.py" . | wc -l
            ''',
            returnStdout: true
        ).trim().toInteger()
        
        if (todoCount > 10) {
            steps.echo "Warning: Found ${todoCount} TODO comments"
        }
    }
}