#!/usr/bin/groovy

/**
 * sonarQube.groovy - Shared SonarQube pipeline steps
 */

def call(Map params = [:]) {
    def config = [
        language: params.language ?: 'java',
        qualityGate: params.qualityGate ?: true,
        timeout: params.timeout ?: 10,
        projectKey: params.projectKey ?: "${env.JOB_NAME}",
        projectName: params.projectName ?: "${env.JOB_NAME}",
        projectVersion: params.projectVersion ?: "${env.BUILD_NUMBER}",
        sources: params.sources ?: '.',
        tests: params.tests ?: '.',
        javaCoveragePlugin: params.javaCoveragePlugin ?: 'jacoco'
    ]
    
    script {
        withSonarQubeEnv('sonar') {
            switch(config.language) {
                case 'java':
                    sonarJavaAnalysis(config)
                    break
                case 'nodejs':
                    sonarNodeJSAnalysis(config)
                    break
                case 'python':
                    sonarPythonAnalysis(config)
                    break
                case 'dotnet':
                    sonarDotNetAnalysis(config)
                    break
                default:
                    sonarGenericAnalysis(config)
            }
        }
        
        if (config.qualityGate) {
            waitForQualityGate(config)
        }
    }
}

def sonarJavaAnalysis(Map config) {
    echo "Running SonarQube analysis for Java"
    
    if (fileExists('pom.xml')) {
        def goals = "sonar:sonar"
        def properties = """
            -Dsonar.projectKey=${config.projectKey}
            -Dsonar.projectName=${config.projectName}
            -Dsonar.projectVersion=${config.projectVersion}
            -Dsonar.sources=${config.sources}
            -Dsonar.tests=${config.tests}
            -Dsonar.java.binaries=target/classes
            -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
        """.stripIndent().trim()
        
        sh "mvn ${goals} ${properties}"
    } else if (fileExists('build.gradle')) {
        sh """
            gradle sonarqube \
                -Dsonar.projectKey=${config.projectKey} \
                -Dsonar.projectName=${config.projectName} \
                -Dsonar.projectVersion=${config.projectVersion} \
                -Dsonar.sources=${config.sources} \
                -Dsonar.tests=${config.tests}
        """
    } else {
        def scannerHome = tool 'sonar-scanner'
        sh """
            ${scannerHome}/bin/sonar-scanner \
                -Dsonar.projectKey=${config.projectKey} \
                -Dsonar.projectName=${config.projectName} \
                -Dsonar.projectVersion=${config.projectVersion} \
                -Dsonar.sources=${config.sources} \
                -Dsonar.tests=${config.tests} \
                -Dsonar.java.binaries=build/classes \
                -Dsonar.coverage.jacoco.xmlReportPaths=build/reports/jacoco/test/jacocoTestReport.xml
        """
    }
}

def sonarNodeJSAnalysis(Map config) {
    echo "Running SonarQube analysis for Node.js"
    
    def scannerHome = tool 'sonar-scanner'
    
    sh """
        ${scannerHome}/bin/sonar-scanner \
            -Dsonar.projectKey=${config.projectKey} \
            -Dsonar.projectName=${config.projectName} \
            -Dsonar.projectVersion=${config.projectVersion} \
            -Dsonar.sources=${config.sources} \
            -Dsonar.tests=${config.tests} \
            -Dsonar.javascript.lcov.reportPaths=coverage/lcov.info \
            -Dsonar.coverage.exclusions=**/node_modules/**,**/test/**,**/spec/**
    """
}

def sonarPythonAnalysis(Map config) {
    echo "Running SonarQube analysis for Python"
    
    def scannerHome = tool 'sonar-scanner'
    
    sh """
        ${scannerHome}/bin/sonar-scanner \
            -Dsonar.projectKey=${config.projectKey} \
            -Dsonar.projectName=${config.projectName} \
            -Dsonar.projectVersion=${config.projectVersion} \
            -Dsonar.sources=${config.sources} \
            -Dsonar.tests=${config.tests} \
            -Dsonar.python.coverage.reportPaths=coverage.xml \
            -Dsonar.python.xunit.reportPath=test-reports/xunit.xml
    """
}

def sonarGenericAnalysis(Map config) {
    echo "Running generic SonarQube analysis"
    
    def scannerHome = tool 'sonar-scanner'
    
    sh """
        ${scannerHome}/bin/sonar-scanner \
            -Dsonar.projectKey=${config.projectKey} \
            -Dsonar.projectName=${config.projectName} \
            -Dsonar.projectVersion=${config.projectVersion} \
            -Dsonar.sources=${config.sources} \
            -Dsonar.tests=${config.tests}
    """
}

def waitForQualityGate(Map config) {
    echo "Waiting for SonarQube Quality Gate"
    
    timeout(time: config.timeout, unit: 'MINUTES') {
        def qualityGate = waitForQualityGate()
        
        if (qualityGate.status != 'OK') {
            error "Quality Gate failed: ${qualityGate.status}. Details: ${qualityGate.url}"
        }
        
        echo "✅ Quality Gate passed: ${qualityGate.status}"
    }
}

// Helper methods
def getSonarProjectUrl() {
    def projectKey = "${env.JOB_NAME}".replaceAll('/', ':')
    return "${env.SONAR_HOST_URL}/dashboard?id=${projectKey}"
}

def analysisWithCoverage(Map params = [:]) {
    def config = [
        language: params.language ?: 'java',
        coverageTool: params.coverageTool ?: 'jacoco'
    ]
    
    echo "Running analysis with coverage for ${config.language}"
    
    switch(config.language) {
        case 'java':
            if (config.coverageTool == 'jacoco') {
                sh 'mvn jacoco:prepare-agent test jacoco:report'
            }
            break
        case 'nodejs':
            sh 'npm test -- --coverage'
            break
        case 'python':
            sh '. venv/bin/activate && coverage run -m pytest && coverage xml'
            break
    }
    
    call(params)
}

return this