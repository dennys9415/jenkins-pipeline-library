#!/usr/bin/groovy

/**
 * securityScan.groovy - Shared security scanning pipeline steps
 */

def call(Map params = [:]) {
    def config = [
        language: params.language ?: 'java',
        scanType: params.scanType ?: 'all',
        failOnVulnerabilities: params.failOnVulnerabilities ?: true,
        severityThreshold: params.severityThreshold ?: 'HIGH',
        generateReports: params.generateReports ?: true,
        tools: params.tools ?: ['trivy', 'owasp', 'snyk']
    ]
    
    pipeline {
        agent any
        
        stages {
            stage('Dependency Scan') {
                when {
                    expression { 
                        config.scanType == 'dependency' || config.scanType == 'all' 
                    }
                }
                steps {
                    script {
                        scanDependencies(config)
                    }
                }
            }
            
            stage('SAST Scan') {
                when {
                    expression { 
                        config.scanType == 'sast' || config.scanType == 'all' 
                    }
                }
                steps {
                    script {
                        runSASTScan(config)
                    }
                }
            }
            
            stage('Container Scan') {
                when {
                    expression { 
                        config.scanType == 'container' || config.scanType == 'all' 
                    }
                }
                steps {
                    script {
                        scanContainerImage(config)
                    }
                }
            }
            
            stage('DAST Scan') {
                when {
                    expression { 
                        config.scanType == 'dast' || config.scanType == 'all' 
                    }
                }
                steps {
                    script {
                        runDASTScan(config)
                    }
                }
            }
            
            stage('Security Report') {
                steps {
                    script {
                        generateSecurityReport(config)
                    }
                }
            }
        }
        
        post {
            always {
                script {
                    archiveSecurityArtifacts(config)
                }
            }
            success {
                script {
                    notifySecurityScanSuccess(config)
                }
            }
            failure {
                script {
                    notifySecurityScanFailure(config)
                }
            }
        }
    }
}

def scanDependencies(Map config) {
    echo "Scanning dependencies for ${config.language}"
    
    switch(config.language) {
        case 'java':
            scanJavaDependencies(config)
            break
        case 'nodejs':
            scanNodeJSDependencies(config)
            break
        case 'python':
            scanPythonDependencies(config)
            break
        default:
            echo "Dependency scanning not configured for ${config.language}"
    }
}

def scanJavaDependencies(Map config) {
    dir('.') {
        if (config.tools.contains('owasp')) {
            dependencyCheck arguments: '''
                --scan . 
                --format HTML 
                --format JSON 
                --out reports/dependency-check
                --enableRetired
            ''', odcInstallation: 'OWASP-Dependency-Check'
            
            dependencyCheckPublisher pattern: 'reports/dependency-check/dependency-check-report.json'
        }
        
        if (config.tools.contains('snyk')) {
            withCredentials([string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {
                if (fileExists('pom.xml')) {
                    sh 'snyk test --all-projects --org=my-org --json-file-output=reports/snyk-dependencies.json || true'
                } else if (fileExists('build.gradle')) {
                    sh 'snyk test --all-projects --org=my-org --json-file-output=reports/snyk-dependencies.json || true'
                }
            }
        }
    }
}

def scanNodeJSDependencies(Map config) {
    dir('.') {
        sh 'npm audit --json > reports/npm-audit.json || true'
        
        if (config.tools.contains('snyk')) {
            withCredentials([string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {
                sh 'snyk test --json-file-output=reports/snyk-dependencies.json || true'
            }
        }
    }
}

def scanPythonDependencies(Map config) {
    dir('.') {
        sh '. venv/bin/activate && pip-audit --format json --output reports/pip-audit.json || true'
        
        if (config.tools.contains('snyk')) {
            withCredentials([string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {
                sh 'snyk test --command=venv/bin/python --json-file-output=reports/snyk-dependencies.json || true'
            }
        }
    }
}

def runSASTScan(Map config) {
    echo "Running SAST scan for ${config.language}"
    
    switch(config.language) {
        case 'java':
            runJavaSASTScan(config)
            break
        case 'nodejs':
            runNodeJSSASTScan(config)
            break
        case 'python':
            runPythonSASTScan(config)
            break
        default:
            echo "SAST scanning not configured for ${config.language}"
    }
}

def runJavaSASTScan(Map config) {
    dir('.') {
        if (fileExists('pom.xml')) {
            sh 'mvn spotbugs:spotbugs -Dspotbugs.failOnError=false'
            spotbugs(pattern: '**/spotbugsXml.xml')
            
            sh 'mvn pmd:pmd -Dpmd.failOnError=false'
            pmd(pattern: '**/pmd.xml')
            
            sh 'mvn checkstyle:checkstyle -Dcheckstyle.failOnError=false'
            checkstyle(pattern: '**/checkstyle-result.xml')
        }
        
        withSonarQubeEnv('sonar') {
            if (fileExists('pom.xml')) {
                sh 'mvn sonar:sonar -Dsonar.security.sources=java -Dsonar.failOnError=false'
            }
        }
    }
}

def runNodeJSSASTScan(Map config) {
    dir('.') {
        sh 'npm run lint || npx eslint . --format json --output-file reports/eslint-report.json || true'
        sh 'npx semgrep --config=auto --json --output reports/semgrep-report.json . || true'
    }
}

def runPythonSASTScan(Map config) {
    dir('.') {
        sh '. venv/bin/activate && bandit -r . -f json -o reports/bandit-report.json || true'
        sh '. venv/bin/activate && pylint **/*.py --output-format=json:reports/pylint-report.json || true'
    }
}

def scanContainerImage(Map config) {
    echo "Scanning container image for vulnerabilities"
    
    def imageName = "${env.JOB_NAME}:${env.BUILD_NUMBER}"
    
    dir('.') {
        if (config.tools.contains('trivy')) {
            sh """
                trivy image --format json --output reports/trivy-container.json ${imageName} || true
                trivy image --severity ${config.severityThreshold} --exit-code 0 ${imageName}
            """
        }
        
        sh "grype ${imageName} -o json --file reports/grype-container.json || true"
    }
}

def runDASTScan(Map config) {
    echo "Running DAST scan"
    
    dir('.') {
        sh '''
            docker run --rm -v $(pwd):/zap/wrk/:rw -t owasp/zap2docker-stable zap-baseline.py \
                -t http://localhost:8080 \
                -g gen.conf \
                -r zap-report.html \
                -x zap-report.xml || true
        '''
        
        archiveArtifacts artifacts: 'zap-report.html,zap-report.xml', allowEmptyArchive: true
    }
}

def generateSecurityReport(Map config) {
    echo "Generating security report"
    
    dir('.') {
        sh '''
            echo "# Security Scan Report" > security-report.md
            echo "Generated: $(date)" >> security-report.md
            echo "" >> security-report.md
        '''
        
        if (fileExists('reports/dependency-check/dependency-check-report.json')) {
            sh '''
                echo "## Dependency Vulnerabilities" >> security-report.md
                echo "" >> security-report.md
                jq -r '.dependencies[] | select(.vulnerabilities != null and .vulnerabilities != []) | "**\(.fileName)** - \(.vulnerabilities | length) vulnerabilities"' reports/dependency-check/dependency-check-report.json >> security-report.md || echo "No dependency vulnerabilities found" >> security-report.md
                echo "" >> security-report.md
            '''
        }
        
        if (fileExists('reports/trivy-container.json')) {
            sh '''
                echo "## Container Vulnerabilities" >> security-report.md
                echo "" >> security-report.md
                jq -r '.Results[]?.Vulnerabilities[]? | "\(.Severity): \(.VulnerabilityID) - \(.Title)"' reports/trivy-container.json | sort | uniq -c >> security-report.md || echo "No container vulnerabilities found" >> security-report.md
                echo "" >> security-report.md
            '''
        }
        
        archiveArtifacts artifacts: 'security-report.md', allowEmptyArchive: true
    }
}

def archiveSecurityArtifacts(Map config) {
    echo "Archiving security artifacts"
    
    archiveArtifacts artifacts: 'reports/**/*, security-report.md', allowEmptyArchive: true
    
    publishHTML([
        allowMissing: true,
        alwaysLinkToLastBuild: true,
        keepAll: true,
        reportDir: 'reports/dependency-check',
        reportFiles: 'dependency-check-report.html',
        reportName: 'Dependency Check Report'
    ])
}

def notifySecurityScanSuccess(Map config) {
    echo "✅ Security scan completed successfully"
}

def notifySecurityScanFailure(Map config) {
    echo "❌ Security scan failed or found vulnerabilities"
    
    if (config.failOnVulnerabilities) {
        emailext (
            subject: "SECURITY SCAN FAILED: ${env.JOB_NAME}",
            body: """
                <p>Security scan found vulnerabilities above threshold: ${config.severityThreshold}</p>
                <p>Build: ${env.BUILD_NUMBER}</p>
                <p>View: <a href='${env.BUILD_URL}'>${env.JOB_NAME}</a></p>
            """,
            to: "${env.SECURITY_TEAM_RECIPIENTS}"
        )
    }
}

return this