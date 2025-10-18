#!/usr/bin/groovy

/**
 * test.groovy - Shared testing pipeline steps
 */

def call(Map params = [:]) {
    def config = [
        language: params.language ?: 'java',
        testType: params.testType ?: 'all',
        coverage: params.coverage ?: true,
        parallel: params.parallel ?: false,
        testReports: params.testReports ?: true,
        failFast: params.failFast ?: false,
        testPattern: params.testPattern ?: '**/*Test.*'
    ]
    
    pipeline {
        agent any
        
        stages {
            stage('Unit Tests') {
                when {
                    expression { 
                        config.testType == 'unit' || config.testType == 'all' 
                    }
                }
                steps {
                    script {
                        runUnitTests(config)
                    }
                }
                post {
                    always {
                        script {
                            publishTestResults(config, 'unit')
                        }
                    }
                }
            }
            
            stage('Integration Tests') {
                when {
                    expression { 
                        config.testType == 'integration' || config.testType == 'all' 
                    }
                }
                steps {
                    script {
                        runIntegrationTests(config)
                    }
                }
                post {
                    always {
                        script {
                            publishTestResults(config, 'integration')
                        }
                    }
                }
            }
            
            stage('E2E Tests') {
                when {
                    expression { 
                        config.testType == 'e2e' || config.testType == 'all' 
                    }
                }
                steps {
                    script {
                        runE2ETests(config)
                    }
                }
                post {
                    always {
                        script {
                            publishTestResults(config, 'e2e')
                        }
                    }
                }
            }
            
            stage('Code Coverage') {
                when {
                    expression { config.coverage }
                }
                steps {
                    script {
                        generateCoverageReport(config)
                    }
                }
            }
        }
        
        post {
            always {
                script {
                    archiveTestArtifacts(config)
                }
            }
        }
    }
}

def runUnitTests(Map config) {
    echo "Running unit tests for ${config.language}"
    
    switch(config.language) {
        case 'java':
            runJavaUnitTests(config)
            break
        case 'nodejs':
            runNodeJSUnitTests(config)
            break
        case 'python':
            runPythonUnitTests(config)
            break
        default:
            error "Unsupported language for unit tests: ${config.language}"
    }
}

def runJavaUnitTests(Map config) {
    if (fileExists('pom.xml')) {
        if (config.parallel) {
            sh "mvn test -Dtest='${config.testPattern}' -Dparallel=classes -DthreadCount=2"
        } else {
            sh "mvn test -Dtest='${config.testPattern}'"
        }
    } else if (fileExists('build.gradle')) {
        if (config.parallel) {
            sh "gradle test --parallel --tests '${config.testPattern}'"
        } else {
            sh "gradle test --tests '${config.testPattern}'"
        }
    }
}

def runNodeJSUnitTests(Map config) {
    if (config.parallel) {
        sh 'npm run test:unit -- --maxWorkers=2'
    } else {
        sh 'npm run test:unit || npm test'
    }
}

def runPythonUnitTests(Map config) {
    sh '. venv/bin/activate && python -m pytest tests/unit/ -v --junitxml=test-reports/unit-tests.xml'
}

def runIntegrationTests(Map config) {
    echo "Running integration tests for ${config.language}"
    
    switch(config.language) {
        case 'java':
            if (fileExists('pom.xml')) {
                sh 'mvn verify -Dit.test=**/*IT -DfailIfNoTests=false'
            } else if (fileExists('build.gradle')) {
                sh 'gradle integrationTest'
            }
            break
        case 'nodejs':
            sh 'npm run test:integration'
            break
        case 'python':
            sh '. venv/bin/activate && python -m pytest tests/integration/ -v --junitxml=test-reports/integration-tests.xml'
            break
    }
}

def runE2ETests(Map config) {
    echo "Running E2E tests for ${config.language}"
    
    switch(config.language) {
        case 'java':
            sh 'mvn verify -Dtest=**/*E2ETest -DfailIfNoTests=false'
            break
        case 'nodejs':
            sh 'npm run test:e2e'
            break
        case 'python':
            sh '. venv/bin/activate && python -m pytest tests/e2e/ -v --junitxml=test-reports/e2e-tests.xml'
            break
    }
}

def generateCoverageReport(Map config) {
    echo "Generating coverage report for ${config.language}"
    
    switch(config.language) {
        case 'java':
            if (fileExists('pom.xml')) {
                sh 'mvn jacoco:report'
                jacoco(
                    execPattern: 'target/**/*.exec',
                    classPattern: 'target/classes',
                    sourcePattern: 'src/main/java',
                    exclusionPattern: '**/test/**'
                )
            } else if (fileExists('build.gradle')) {
                sh 'gradle jacocoTestReport'
            }
            break
        case 'nodejs':
            sh 'npm run coverage || npx nyc report --reporter=html'
            publishHTML([
                allowMissing: false,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'coverage',
                reportFiles: 'index.html',
                reportName: 'Coverage Report'
            ])
            break
        case 'python':
            sh '. venv/bin/activate && python -m pytest --cov-report html:coverage --cov=. tests/'
            publishHTML([
                allowMissing: false,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'coverage',
                reportFiles: 'index.html',
                reportName: 'Coverage Report'
            ])
            break
    }
}

def publishTestResults(Map config, String testType) {
    echo "Publishing ${testType} test results"
    
    def pattern = '**/test-reports/*.xml'
    
    switch(config.language) {
        case 'java':
            if (fileExists('target/surefire-reports')) {
                junit 'target/surefire-reports/*.xml'
            }
            if (fileExists('target/failsafe-reports') && testType == 'integration') {
                junit 'target/failsafe-reports/*.xml'
            }
            break
        case 'nodejs':
            junit 'test-results/**/*.xml'
            break
        case 'python':
            junit 'test-reports/**/*.xml'
            break
        default:
            junit pattern
    }
}

def archiveTestArtifacts(Map config) {
    echo "Archiving test artifacts"
    
    archiveArtifacts artifacts: '**/test-reports/**, **/coverage/**, **/target/site/**, **/build/reports/**', 
                    allowEmptyArchive: true
}

return this