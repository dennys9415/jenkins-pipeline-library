#!/usr/bin/groovy

/**
 * deploy.groovy - Shared deployment pipeline steps
 */

def call(Map params = [:]) {
    def config = [
        environment: params.environment ?: 'dev',
        platform: params.platform ?: 'kubernetes',
        registry: params.registry ?: 'docker.io',
        namespace: params.namespace ?: 'default',
        deploymentStrategy: params.deploymentStrategy ?: 'rolling',
        healthCheck: params.healthCheck ?: true,
        rollbackOnFailure: params.rollbackOnFailure ?: true,
        secrets: params.secrets ?: [],
        configMaps: params.configMaps ?: [],
        imageTag: params.imageTag ?: "${env.BUILD_NUMBER}"
    ]
    
    pipeline {
        agent any
        
        environment {
            DEPLOY_ENV = "${config.environment}"
            KUBE_NAMESPACE = "${config.namespace}"
            IMAGE_TAG = "${config.imageTag}"
        }
        
        stages {
            stage('Validate Deployment') {
                steps {
                    script {
                        validateDeploymentConfig(config)
                    }
                }
            }
            
            stage('Prepare Environment') {
                steps {
                    script {
                        prepareDeploymentEnvironment(config)
                    }
                }
            }
            
            stage('Build Image') {
                steps {
                    script {
                        buildDockerImage(config)
                    }
                }
            }
            
            stage('Deploy') {
                steps {
                    script {
                        executeDeployment(config)
                    }
                }
            }
            
            stage('Health Check') {
                when {
                    expression { config.healthCheck }
                }
                steps {
                    script {
                        performHealthChecks(config)
                    }
                }
            }
            
            stage('Smoke Tests') {
                steps {
                    script {
                        runSmokeTests(config)
                    }
                }
            }
        }
        
        post {
            success {
                script {
                    notifyDeploymentSuccess(config)
                }
            }
            failure {
                script {
                    if (config.rollbackOnFailure) {
                        rollbackDeployment(config)
                    }
                    notifyDeploymentFailure(config)
                }
            }
        }
    }
}

def validateDeploymentConfig(Map config) {
    echo "Validating deployment configuration for ${config.environment}"
    
    if (!config.environment) {
        error "Environment must be specified"
    }
    
    if (!config.platform) {
        error "Platform must be specified"
    }
    
    switch(config.environment) {
        case 'prod':
            if (!env.PROD_DEPLOY_KEY) {
                error "Production deploy key not configured"
            }
            input message: "Deploy to PRODUCTION?", ok: "Deploy"
            break
        case 'staging':
            if (!env.STAGING_DEPLOY_KEY) {
                error "Staging deploy key not configured"
            }
            break
    }
}

def prepareDeploymentEnvironment(Map config) {
    echo "Preparing deployment environment: ${config.environment}"
    
    switch(config.platform) {
        case 'kubernetes':
            prepareKubernetesEnvironment(config)
            break
        case 'docker':
            prepareDockerEnvironment(config)
            break
        case 'openshift':
            prepareOpenShiftEnvironment(config)
            break
        default:
            error "Unsupported platform: ${config.platform}"
    }
}

def prepareKubernetesEnvironment(Map config) {
    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
        sh '''
            kubectl version --client
            kubectl cluster-info
        '''
    }
    
    sh """
        kubectl create namespace ${config.namespace} --dry-run=client -o yaml | kubectl apply -f - || true
    """
    
    config.secrets.each { secret ->
        withCredentials([string(credentialsId: secret, variable: 'SECRET_VALUE')]) {
            sh """
                kubectl create secret generic ${secret} \
                    --from-literal=value=\$SECRET_VALUE \
                    -n ${config.namespace} --dry-run=client -o yaml | kubectl apply -f - || true
            """
        }
    }
}

def buildDockerImage(Map config) {
    echo "Building Docker image"
    
    def imageName = "${config.registry}/${env.JOB_NAME}:${config.imageTag}"
    
    docker.build(imageName)
    
    docker.withRegistry("https://${config.registry}", 'docker-credentials') {
        docker.image(imageName).push()
        docker.image(imageName).push('latest')
    }
}

def executeDeployment(Map config) {
    echo "Executing deployment to ${config.environment}"
    
    switch(config.deploymentStrategy) {
        case 'rolling':
            rollingDeployment(config)
            break
        case 'blue-green':
            blueGreenDeployment(config)
            break
        case 'canary':
            canaryDeployment(config)
            break
        default:
            error "Unsupported deployment strategy: ${config.deploymentStrategy}"
    }
}

def rollingDeployment(Map config) {
    echo "Performing rolling deployment"
    
    def imageName = "${config.registry}/${env.JOB_NAME}:${config.imageTag}"
    def deploymentFile = "k8s/deployment-${config.environment}.yaml"
    
    if (fileExists(deploymentFile)) {
        sh """
            sed -i 's|IMAGE_PLACEHOLDER|${imageName}|g' ${deploymentFile}
            kubectl apply -f ${deploymentFile} -n ${config.namespace}
            kubectl rollout status deployment/${env.JOB_NAME} -n ${config.namespace} --timeout=600s
        """
    } else {
        def deploymentManifest = """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${env.JOB_NAME}
  namespace: ${config.namespace}
  labels:
    app: ${env.JOB_NAME}
    environment: ${config.environment}
    version: ${config.imageTag}
spec:
  replicas: 2
  selector:
    matchLabels:
      app: ${env.JOB_NAME}
  template:
    metadata:
      labels:
        app: ${env.JOB_NAME}
        version: ${config.imageTag}
    spec:
      containers:
      - name: ${env.JOB_NAME}
        image: ${imageName}
        ports:
        - containerPort: 8080
        env:
        - name: ENVIRONMENT
          value: "${config.environment}"
        readinessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        resources:
          requests:
            memory: "128Mi"
            cpu: "100m"
          limits:
            memory: "512Mi"
            cpu: "500m"
"""
        
        writeFile file: 'deployment.yaml', text: deploymentManifest
        sh """
            kubectl apply -f deployment.yaml -n ${config.namespace}
            kubectl rollout status deployment/${env.JOB_NAME} -n ${config.namespace} --timeout=600s
        """
    }
}

def blueGreenDeployment(Map config) {
    echo "Performing blue-green deployment"
    
    def imageName = "${config.registry}/${env.JOB_NAME}:${config.imageTag}"
    def activeDeployment = "${env.JOB_NAME}-blue"
    def inactiveDeployment = "${env.JOB_NAME}-green"
    
    def currentColor = sh(
        script: "kubectl get service ${env.JOB_NAME} -n ${config.namespace} -o jsonpath='{.spec.selector.version}' 2>/dev/null || echo 'blue'",
        returnStdout: true
    ).trim()
    
    def newColor = (currentColor == 'blue') ? 'green' : 'blue'
    def newDeployment = "${env.JOB_NAME}-${newColor}"
    
    def deploymentManifest = """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${newDeployment}
  namespace: ${config.namespace}
spec:
  replicas: 2
  selector:
    matchLabels:
      app: ${env.JOB_NAME}
      version: ${newColor}
  template:
    metadata:
      labels:
        app: ${env.JOB_NAME}
        version: ${newColor}
    spec:
      containers:
      - name: ${env.JOB_NAME}
        image: ${imageName}
        ports:
        - containerPort: 8080
"""
    
    writeFile file: "deployment-${newColor}.yaml", text: deploymentManifest
    
    sh """
        kubectl apply -f deployment-${newColor}.yaml -n ${config.namespace}
        kubectl rollout status deployment/${newDeployment} -n ${config.namespace} --timeout=600s
    """
    
    def serviceManifest = """
apiVersion: v1
kind: Service
metadata:
  name: ${env.JOB_NAME}
  namespace: ${config.namespace}
spec:
  selector:
    app: ${env.JOB_NAME}
    version: ${newColor}
  ports:
  - port: 80
    targetPort: 8080
"""
    
    writeFile file: 'service.yaml', text: serviceManifest
    sh "kubectl apply -f service.yaml -n ${config.namespace}"
    
    echo "Blue-green deployment completed. Active: ${newColor}, Inactive: ${currentColor}"
}

def performHealthChecks(Map config) {
    echo "Performing health checks"
    
    def retries = 12
    def delay = 10
    
    for (int i = 0; i < retries; i++) {
        try {
            def pods = sh(
                script: "kubectl get pods -n ${config.namespace} -l app=${env.JOB_NAME} -o jsonpath='{.items[*].status.phase}'",
                returnStdout: true
            ).trim()
            
            if (pods.split(' ').every { it == 'Running' }) {
                echo "All pods are running"
                
                def readyPods = sh(
                    script: "kubectl get pods -n ${config.namespace} -l app=${env.JOB_NAME} -o jsonpath='{.items[*].status.conditions[?(@.type==\"Ready\")].status}'",
                    returnStdout: true
                ).trim()
                
                if (readyPods.split(' ').every { it == 'True' }) {
                    echo "✅ Health checks passed"
                    return
                }
            }
        } catch (Exception e) {
            echo "Health check attempt ${i + 1} failed: ${e.message}"
        }
        
        sleep delay
    }
    
    error "❌ Health checks failed after ${retries} attempts"
}

def runSmokeTests(Map config) {
    echo "Running smoke tests"
    
    def serviceUrl = sh(
        script: "kubectl get service ${env.JOB_NAME} -n ${config.namespace} -o jsonpath='{.status.loadBalancer.ingress[0].ip}'",
        returnStdout: true
    ).trim()
    
    if (!serviceUrl) {
        serviceUrl = sh(
            script: "kubectl get service ${env.JOB_NAME} -n ${config.namespace} -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'",
            returnStdout: true
        ).trim()
    }
    
    if (serviceUrl) {
        sh """
            curl -f http://${serviceUrl}/health || curl -f https://${serviceUrl}/health
            curl -f http://${serviceUrl}/info || curl -f https://${serviceUrl}/info
        """
    } else {
        echo "Service URL not available, skipping smoke tests"
    }
}

def rollbackDeployment(Map config) {
    echo "Rolling back deployment"
    
    try {
        sh "kubectl rollout undo deployment/${env.JOB_NAME} -n ${config.namespace}"
        echo "✅ Rollback completed successfully"
    } catch (Exception e) {
        error "❌ Rollback failed: ${e.message}"
    }
}

def notifyDeploymentSuccess(Map config) {
    echo "✅ Deployment to ${config.environment} completed successfully"
    
    emailext (
        subject: "DEPLOYMENT SUCCESS: ${env.JOB_NAME} to ${config.environment}",
        body: """
            <p>Deployment to ${config.environment} completed successfully</p>
            <p>Build: ${env.BUILD_NUMBER}</p>
            <p>Commit: ${env.GIT_COMMIT}</p>
            <p>View: <a href='${env.BUILD_URL}'>${env.JOB_NAME}</a></p>
        """,
        to: "${env.DEFAULT_RECIPIENTS}"
    )
}

def notifyDeploymentFailure(Map config) {
    echo "❌ Deployment to ${config.environment} failed"
    
    emailext (
        subject: "DEPLOYMENT FAILED: ${env.JOB_NAME} to ${config.environment}",
        body: """
            <p>Deployment to ${config.environment} failed</p>
            <p>Build: ${env.BUILD_NUMBER}</p>
            <p>Commit: ${env.GIT_COMMIT}</p>
            <p>View: <a href='${env.BUILD_URL}'>${env.JOB_NAME}</a></p>
        """,
        to: "${env.DEFAULT_RECIPIENTS}"
    )
}

return this