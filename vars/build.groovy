#!/usr/bin/groovy

/**
 * build.groovy - Shared build pipeline steps
 */

def call(Map params = [:]) {
    def config = [
        language: params.language ?: 'java',
        buildTool: params.buildTool ?: 'maven',
        jdkVersion: params.jdkVersion ?: '11',
        nodeVersion: params.nodeVersion ?: '18',
        pythonVersion: params.pythonVersion ?: '3.9',
        buildArgs: params.buildArgs ?: '',
        skipTests: params.skipTests ?: false,
        sonarQube: params.sonarQube ?: false,
        qualityGate: params.qualityGate ?: true
    ]
    
    pipeline {
        agent any
        
        tools {
            if (config.language == 'java') {
                jdk config.jdkVersion
                if (config.buildTool == 'maven') {
                    maven 'maven-3.8'
                } else if (config.buildTool == 'gradle') {
                    gradle 'gradle-7'
                }
            } else if (config.language == 'nodejs') {
                nodejs config.nodeVersion
            }
        }
        
        environment {
            BUILD_NUMBER = "${env.BUILD_NUMBER}"
            BUILD_URL = "${env.BUILD_URL}"
            GIT_COMMIT = "${env.GIT_COMMIT}"
            GIT_BRANCH = "${env.GIT_BRANCH}"
        }
        
        stages {
            stage('Checkout') {
                steps {
                    checkout scm
                    script {
                        currentBuild.description = "Building ${env.JOB_NAME} - ${env.BRANCH_NAME}"
                        echo "Building ${config.language} project with ${config.buildTool}"
                    }
                }
            }
            
            stage('Setup Environment') {
                steps {
                    script {
                        setupBuildEnvironment(config)
                    }
                }
            }
            
            stage('Dependency Resolution') {
                steps {
                    script {
                        resolveDependencies(config)
                    }
                }
            }
            
            stage('Build') {
                steps {
                    script {
                        executeBuild(config)
                    }
                }
            }
            
            stage('Static Analysis') {
                when {
                    expression { config.sonarQube }
                }
                steps {
                    script {
                        runSonarQubeAnalysis(config)
                    }
                }
            }
            
            stage('Quality Gate') {
                when {
                    expression { config.qualityGate && config.sonarQube }
                }
                steps {
                    script {
                        waitForQualityGate()
                    }
                }
            }
        }
        
        post {
            always {
                script {
                    cleanupBuild()
                    recordIssues(
                        tools: [java(), checkStyle(), pmd(), spotBugs()],
                        enabledForFailure: true
                    )
                }
            }
            success {
                script {
                    notifyBuildSuccess(config)
                }
            }
            failure {
                script {
                    notifyBuildFailure(config)
                }
            }
            unstable {
                script {
                    notifyBuildUnstable(config)
                }
            }
        }
    }
}

def setupBuildEnvironment(Map config) {
    echo "Setting up build environment for ${config.language}"
    
    switch(config.language) {
        case 'java':
            setupJavaEnvironment(config)
            break
        case 'nodejs':
            setupNodeJSEnvironment(config)
            break
        case 'python':
            setupPythonEnvironment(config)
            break
        case 'dotnet':
            setupDotNetEnvironment(config)
            break
        default:
            error "Unsupported language: ${config.language}"
    }
}

def setupJavaEnvironment(Map config) {
    echo "Setting up Java environment with JDK ${config.jdkVersion}"
    
    env.JAVA_HOME = tool "${config.jdkVersion}"
    env.PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
    
    if (config.buildTool == 'maven') {
        def mavenHome = tool 'maven-3.8'
        env.M2_HOME = mavenHome
        env.PATH = "${mavenHome}/bin:${env.PATH}"
        sh 'mvn --version'
    } else if (config.buildTool == 'gradle') {
        def gradleHome = tool 'gradle-7'
        env.PATH = "${gradleHome}/bin:${env.PATH}"
        sh 'gradle --version'
    }
}

def setupNodeJSEnvironment(Map config) {
    echo "Setting up Node.js environment with version ${config.nodeVersion}"
    
    def nodeHome = tool "${config.nodeVersion}"
    env.PATH = "${nodeHome}/bin:${env.PATH}"
    
    sh '''
        npm --version
        node --version
    '''
}

def setupPythonEnvironment(Map config) {
    echo "Setting up Python environment with version ${config.pythonVersion}"
    
    sh """
        python${config.pythonVersion} --version
        python${config.pythonVersion} -m venv venv
        . venv/bin/activate
        pip install --upgrade pip
    """
}

def resolveDependencies(Map config) {
    echo "Resolving dependencies for ${config.language}"
    
    switch(config.language) {
        case 'java':
            if (config.buildTool == 'maven') {
                sh 'mvn dependency:resolve -q'
            } else if (config.buildTool == 'gradle') {
                sh 'gradle dependencies -q'
            }
            break
        case 'nodejs':
            if (fileExists('package-lock.json')) {
                sh 'npm ci --silent'
            } else {
                sh 'npm install --silent'
            }
            break
        case 'python':
            sh '. venv/bin/activate && pip install -r requirements.txt -q'
            break
    }
}

def executeBuild(Map config) {
    echo "Executing build for ${config.language}"
    
    switch(config.language) {
        case 'java':
            if (config.buildTool == 'maven') {
                def goals = config.skipTests ? 'clean compile' : 'clean compile test'
                sh "mvn ${goals} ${config.buildArgs} -Dmaven.test.failure.ignore=true"
            } else if (config.buildTool == 'gradle') {
                def tasks = config.skipTests ? 'clean build -x test' : 'clean build'
                sh "gradle ${tasks} ${config.buildArgs}"
            }
            break
        case 'nodejs':
            if (config.skipTests) {
                sh 'npm run build --if-present'
            } else {
                sh 'npm run build --if-present && npm test'
            }
            break
        case 'python':
            sh '. venv/bin/activate && python -m pytest tests/ -v --tb=short'
            break
    }
}

def runSonarQubeAnalysis(Map config) {
    echo "Running SonarQube analysis"
    
    def scannerHome = tool 'sonar-scanner'
    
    withSonarQubeEnv('sonar') {
        switch(config.language) {
            case 'java':
                if (config.buildTool == 'maven') {
                    sh 'mvn sonar:sonar -Dsonar.projectKey=${env.JOB_NAME}'
                } else {
                    sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${env.JOB_NAME}"
                }
                break
            case 'nodejs':
                sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${env.JOB_NAME}"
                break
        }
    }
}

def waitForQualityGate() {
    timeout(time: 10, unit: 'MINUTES') {
        def qg = waitForQualityGate()
        if (qg.status != 'OK') {
            error "Quality Gate failed: ${qg.status}"
        }
    }
}

def cleanupBuild() {
    echo "Cleaning up build environment"
    
    sh '''
        rm -rf node_modules || true
        rm -rf target || true
        rm -rf build || true
        rm -rf .gradle || true
        rm -rf venv || true
        rm -rf .pytest_cache || true
    '''
}

def notifyBuildSuccess(Map config) {
    echo "✅ Build completed successfully for ${config.language}"
    emailext (
        subject: "SUCCESS: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'",
        body: """
            <p>Build SUCCESS</p>
            <p>Check console output at <a href='${env.BUILD_URL}'>${env.JOB_NAME} [${env.BUILD_NUMBER}]</a></p>
        """,
        to: "${env.DEFAULT_RECIPIENTS}",
        recipientProviders: [[$class: 'DevelopersRecipientProvider']]
    )
}

def notifyBuildFailure(Map config) {
    echo "❌ Build failed for ${config.language}"
    emailext (
        subject: "FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'",
        body: """
            <p>Build FAILED</p>
            <p>Check console output at <a href='${env.BUILD_URL}'>${env.JOB_NAME} [${env.BUILD_NUMBER}]</a></p>
        """,
        to: "${env.DEFAULT_RECIPIENTS}",
        recipientProviders: [[$class: 'DevelopersRecipientProvider']]
    )
}

def notifyBuildUnstable(Map config) {
    echo "⚠️ Build unstable for ${config.language}"
}

return this