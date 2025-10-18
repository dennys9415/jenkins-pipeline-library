package org.company

/**
 * PipelineBuilder - Advanced pipeline construction with fluent API
 */
class PipelineBuilder implements Serializable {
    private final steps
    private Map config = [:]
    private List<String> stages = []
    private List<String> postActions = []
    
    PipelineBuilder(steps) {
        this.steps = steps
    }
    
    def forLanguage(String language) {
        config.language = language
        return this
    }
    
    def withBuildTool(String buildTool) {
        config.buildTool = buildTool
        return this
    }
    
    def withEnvironment(String environment) {
        config.environment = environment
        return this
    }
    
    def withQualityGates(boolean enabled = true) {
        config.qualityGates = enabled
        return this
    }
    
    def withSecurityScan(boolean enabled = true) {
        config.securityScan = enabled
        return this
    }
    
    def withNotifications(boolean enabled = true) {
        config.notifications = enabled
        return this
    }
    
    def withDocker(boolean enabled = true) {
        config.docker = enabled
        return this
    }
    
    def withKubernetes(boolean enabled = true) {
        config.kubernetes = enabled
        return this
    }
    
    def withParameters(Map parameters) {
        config.parameters = parameters
        return this
    }
    
    def withOptions(Map options) {
        config.options = options
        return this
    }
    
    def addStage(String name, Closure stageDefinition) {
        stages.add(name)
        config["stage_${name}"] = stageDefinition
        return this
    }
    
    def addPostAction(String condition, Closure action) {
        postActions.add(condition)
        config["post_${condition}"] = action
        return this
    }
    
    def build() {
        return { ->
            steps.pipeline {
                // Pipeline options
                steps.options {
                    buildDiscarder(steps.logRotator(numToKeepStr: '10'))
                    timeout(time: config.options?.timeout ?: 60, unit: 'MINUTES')
                    steps.timestamps()
                    steps.disableConcurrentBuilds()
                    steps.ansiColor('xterm')
                }
                
                // Parameters
                if (config.parameters) {
                    steps.parameters {
                        config.parameters.each { name, definition ->
                            if (definition.type == 'choice') {
                                steps.choice(
                                    name: name,
                                    choices: definition.choices,
                                    description: definition.description
                                )
                            } else if (definition.type == 'boolean') {
                                steps.booleanParam(
                                    name: name,
                                    defaultValue: definition.default ?: false,
                                    description: definition.description
                                )
                            } else if (definition.type == 'string') {
                                steps.string(
                                    name: name,
                                    defaultValue: definition.default ?: '',
                                    description: definition.description
                                )
                            }
                        }
                    }
                } else {
                    steps.parameters {
                        steps.choice(
                            name: 'DEPLOY_ENV',
                            choices: ['dev', 'staging', 'prod'],
                            description: 'Deployment environment'
                        )
                        steps.booleanParam(
                            name: 'SKIP_TESTS',
                            defaultValue: false,
                            description: 'Skip running tests'
                        )
                        steps.booleanParam(
                            name: 'DRY_RUN',
                            defaultValue: false,
                            description: 'Dry run without actual deployment'
                        )
                    }
                }
                
                // Environment variables
                steps.environment {
                    BUILD_NUMBER = "${env.BUILD_NUMBER}"
                    BUILD_URL = "${env.BUILD_URL}"
                    JOB_NAME = "${env.JOB_NAME}"
                    GIT_COMMIT = "${env.GIT_COMMIT}"
                    GIT_BRANCH = "${env.GIT_BRANCH}"
                    
                    // Common tools
                    if (config.language == 'java') {
                        JAVA_HOME = steps.tool "${config.jdkVersion ?: '11'}"
                    } else if (config.language == 'nodejs') {
                        NODE_HOME = steps.tool "${config.nodeVersion ?: '18'}"
                    }
                    
                    // Registry configuration
                    DOCKER_REGISTRY = config.dockerRegistry ?: 'docker.io'
                    KUBE_NAMESPACE = config.namespace ?: 'default'
                }
                
                stages.stages {
                    // Source stage
                    stages.stage('Source') {
                        steps.steps {
                            steps.checkout steps.scm
                            steps.script {
                                currentBuild.displayName = "#${env.BUILD_NUMBER} - ${env.BRANCH_NAME}"
                                currentBuild.description = "Building ${config.language} application"
                                
                                if (config.notifications) {
                                    steps.notify(
                                        type: 'slack',
                                        status: 'started',
                                        message: "Build started for ${env.JOB_NAME}"
                                    )
                                }
                            }
                        }
                    }
                    
                    // Build stage
                    stages.stage('Build') {
                        steps.steps {
                            steps.script {
                                buildPipeline(config)
                            }
                        }
                    }
                    
                    // Test stage
                    if (!steps.params.SKIP_TESTS) {
                        stages.stage('Test') {
                            steps.steps {
                                steps.script {
                                    testPipeline(config)
                                }
                            }
                        }
                    }
                    
                    // Security scan stage
                    if (config.securityScan) {
                        stages.stage('Security Scan') {
                            steps.steps {
                                steps.script {
                                    securityScanPipeline(config)
                                }
                            }
                        }
                    }
                    
                    // Quality gate stage
                    if (config.qualityGates) {
                        stages.stage('Quality Gate') {
                            steps.steps {
                                steps.script {
                                    qualityGatePipeline(config)
                                }
                            }
                        }
                    }
                    
                    // Docker build stage
                    if (config.docker) {
                        stages.stage('Docker Build') {
                            steps.steps {
                                steps.script {
                                    dockerBuildPipeline(config)
                                }
                            }
                        }
                    }
                    
                    // Custom stages
                    stages.each { stageName ->
                        def stageClosure = config["stage_${stageName}"]
                        if (stageClosure) {
                            stages.stage(stageName) {
                                steps.steps {
                                    steps.script {
                                        stageClosure.call()
                                    }
                                }
                            }
                        }
                    }
                    
                    // Deploy stage
                    if (config.kubernetes && !steps.params.DRY_RUN) {
                        stages.stage('Deploy') {
                            steps.steps {
                                steps.script {
                                    deployPipeline(config)
                                }
                            }
                        }
                    }
                }
                
                // Post actions
                steps.post {
                    always {
                        steps.script {
                            cleanupPipeline()
                            steps.recordIssues(
                                tools: [steps.java(), steps.checkStyle(), steps.pmd(), steps.spotBugs()],
                                enabledForFailure: true
                            )
                        }
                    }
                    success {
                        steps.script {
                            if (config.notifications) {
                                notifyPipelineSuccess(config)
                            }
                        }
                    }
                    failure {
                        steps.script {
                            if (config.notifications) {
                                notifyPipelineFailure(config)
                            }
                        }
                    }
                    unstable {
                        steps.script {
                            if (config.notifications) {
                                notifyPipelineUnstable(config)
                            }
                        }
                    }
                    changed {
                        steps.script {
                            if (config.notifications) {
                                steps.notify(
                                    type: 'slack',
                                    status: 'changed',
                                    message: "Build status changed for ${env.JOB_NAME}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    private def buildPipeline(Map config) {
        steps.echo "Building ${config.language} application"
        
        switch(config.language) {
            case 'java':
                buildJavaApplication(config)
                break
            case 'nodejs':
                buildNodeJSApplication(config)
                break
            case 'python':
                buildPythonApplication(config)
                break
            default:
                steps.error "Unsupported language: ${config.language}"
        }
    }
    
    private def buildJavaApplication(Map config) {
        if (config.buildTool == 'maven') {
            steps.sh 'mvn clean compile -DskipTests'
        } else if (config.buildTool == 'gradle') {
            steps.sh 'gradle clean build -x test'
        }
    }
    
    private def buildNodeJSApplication(Map config) {
        steps.sh 'npm run build --if-present'
    }
    
    private def buildPythonApplication(Map config) {
        steps.sh 'python -m py_compile src/**/*.py || true'
    }
    
    private def testPipeline(Map config) {
        steps.echo "Running tests for ${config.language}"
        
        steps.test(
            language: config.language,
            testType: 'all',
            coverage: true
        )
    }
    
    private def securityScanPipeline(Map config) {
        steps.echo "Running security scan for ${config.language}"
        
        steps.securityScan(
            language: config.language,
            scanType: 'all',
            failOnVulnerabilities: true
        )
    }
    
    private def qualityGatePipeline(Map config) {
        steps.echo "Checking quality gates"
        
        steps.waitForQualityGate abortPipeline: true
        
        // Additional quality checks
        if (fileExists('checkstyle-result.xml')) {
            steps.checkstyle canComputeNew: false, defaultEncoding: '', healthy: '', pattern: '**/checkstyle-result.xml', unHealthy: ''
        }
        
        if (fileExists('pmd.xml')) {
            steps.pmd canComputeNew: false, defaultEncoding: '', healthy: '', pattern: '**/pmd.xml', unHealthy: ''
        }
    }
    
    private def dockerBuildPipeline(Map config) {
        steps.echo "Building Docker image"
        
        steps.docker.buildAndPush(
            image: config.dockerImage ?: "${env.JOB_NAME}",
            tag: "${env.BUILD_NUMBER}",
            push: true
        )
    }
    
    private def deployPipeline(Map config) {
        steps.echo "Deploying to ${steps.params.DEPLOY_ENV}"
        
        steps.deploy(
            environment: steps.params.DEPLOY_ENV,
            platform: 'kubernetes',
            deploymentStrategy: 'rolling',
            imageTag: "${env.BUILD_NUMBER}"
        )
    }
    
    private def cleanupPipeline() {
        steps.echo "Cleaning up pipeline"
        
        steps.sh '''
            rm -rf node_modules || true
            rm -rf target || true
            rm -rf build || true
            rm -rf .gradle || true
            rm -rf venv || true
            rm -rf .pytest_cache || true
            rm -rf reports || true
        '''
        
        steps.dockerCleanup()
    }
    
    private def notifyPipelineSuccess(Map config) {
        steps.echo "✅ Pipeline completed successfully"
        
        steps.notify(
            type: 'slack',
            status: 'success',
            message: "Pipeline completed successfully for ${env.JOB_NAME}"
        )
    }
    
    private def notifyPipelineFailure(Map config) {
        steps.echo "❌ Pipeline failed"
        
        steps.notify(
            type: 'slack',
            status: 'failure',
            message: "Pipeline failed for ${env.JOB_NAME}"
        )
    }
    
    private def notifyPipelineUnstable(Map config) {
        steps.echo "⚠️ Pipeline is unstable"
        
        steps.notify(
            type: 'slack',
            status: 'unstable',
            message: "Pipeline is unstable for ${env.JOB_NAME}"
        )
    }
}