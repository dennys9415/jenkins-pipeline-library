package org.company

/**
 * DeploymentManager - Deployment orchestration and management
 */
class DeploymentManager implements Serializable {
    private final steps
    
    DeploymentManager(steps) {
        this.steps = steps
    }
    
    def deployToKubernetes(Map params = [:]) {
        def config = [
            environment: params.environment ?: 'dev',
            namespace: params.namespace ?: 'default',
            image: params.image ?: "${env.JOB_NAME}",
            tag: params.tag ?: "${env.BUILD_NUMBER}",
            strategy: params.strategy ?: 'rolling',
            replicas: params.replicas ?: 2,
            healthCheck: params.healthCheck ?: true
        ]
        
        steps.echo "Deploying to Kubernetes: ${config.environment}"
        
        // Validate deployment
        validateDeployment(config)
        
        // Prepare manifests
        def manifests = prepareManifests(config)
        
        // Apply manifests
        applyManifests(manifests, config.namespace)
        
        // Wait for rollout
        waitForRollout(config)
        
        // Health checks
        if (config.healthCheck) {
            performHealthChecks(config)
        }
        
        steps.echo "✅ Deployment to ${config.environment} completed successfully"
    }
    
    def validateDeployment(Map config) {
        steps.echo "Validating deployment configuration"
        
        if (!config.environment) {
            steps.error "Environment must be specified"
        }
        
        if (!config.image) {
            steps.error "Image must be specified"
        }
        
        if (config.environment == 'prod') {
            steps.input message: "Confirm production deployment?", ok: "Deploy"
        }
    }
    
    def prepareManifests(Map config) {
        steps.echo "Preparing Kubernetes manifests"
        
        def manifests = [:]
        
        // Deployment manifest
        manifests.deployment = """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${config.image}
  namespace: ${config.namespace}
  labels:
    app: ${config.image}
    environment: ${config.environment}
    version: ${config.tag}
spec:
  replicas: ${config.replicas}
  selector:
    matchLabels:
      app: ${config.image}
  template:
    metadata:
      labels:
        app: ${config.image}
        version: ${config.tag}
    spec:
      containers:
      - name: ${config.image}
        image: ${config.image}:${config.tag}
        ports:
        - containerPort: 8080
        env:
        - name: ENVIRONMENT
          value: "${config.environment}"
        - name: VERSION
          value: "${config.tag}"
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
        
        // Service manifest
        manifests.service = """
apiVersion: v1
kind: Service
metadata:
  name: ${config.image}
  namespace: ${config.namespace}
spec:
  selector:
    app: ${config.image}
  ports:
  - port: 80
    targetPort: 8080
  type: ClusterIP
"""
        
        // Ingress manifest (if needed)
        if (config.environment != 'dev') {
            manifests.ingress = """
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ${config.image}
  namespace: ${config.namespace}
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  rules:
  - host: ${config.image}.${config.environment}.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: ${config.image}
            port:
              number: 80
"""
        }
        
        return manifests
    }
    
    def applyManifests(Map manifests, String namespace) {
        steps.echo "Applying Kubernetes manifests"
        
        steps.withCredentials([steps.file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
            // Create namespace if it doesn't exist
            steps.sh """
                kubectl create namespace ${namespace} --dry-run=client -o yaml | kubectl apply -f - || true
            """
            
            // Apply each manifest
            manifests.each { name, manifest ->
                steps.writeFile file: "${name}.yaml", text: manifest
                steps.sh "kubectl apply -f ${name}.yaml -n ${namespace}"
            }
        }
    }
    
    def waitForRollout(Map config) {
        steps.echo "Waiting for rollout to complete"
        
        steps.withCredentials([steps.file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
            steps.sh """
                kubectl rollout status deployment/${config.image} -n ${config.namespace} --timeout=600s
            """
        }
    }
    
    def performHealthChecks(Map config) {
        steps.echo "Performing health checks"
        
        steps.withCredentials([steps.file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
            def retries = 12
            def delay = 10
            
            for (int i = 0; i < retries; i++) {
                try {
                    def pods = steps.sh(
                        script: "kubectl get pods -n ${config.namespace} -l app=${config.image} -o jsonpath='{.items[*].status.phase}'",
                        returnStdout: true
                    ).trim()
                    
                    if (pods.split(' ').every { it == 'Running' }) {
                        steps.echo "All pods are running"
                        
                        def ready = steps.sh(
                            script: "kubectl get pods -n ${config.namespace} -l app=${config.image} -o jsonpath='{.items[*].status.conditions[?(@.type==\"Ready\")].status}'",
                            returnStdout: true
                        ).trim()
                        
                        if (ready.split(' ').every { it == 'True' }) {
                            steps.echo "✅ All pods are ready"
                            return
                        }
                    }
                } catch (Exception e) {
                    steps.echo "Health check attempt ${i + 1} failed: ${e.message}"
                }
                
                steps.sleep delay
            }
            
            steps.error "Health checks failed after ${retries} attempts"
        }
    }
    
    def rollback(Map config) {
        steps.echo "Rolling back deployment"
        
        steps.withCredentials([steps.file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
            try {
                steps.sh """
                    kubectl rollout undo deployment/${config.image} -n ${config.namespace}
                    kubectl rollout status deployment/${config.image} -n ${config.namespace} --timeout=300s
                """
                steps.echo "✅ Rollback completed successfully"
            } catch (Exception e) {
                steps.error "❌ Rollback failed: ${e.message}"
            }
        }
    }
    
    def getDeploymentStatus(Map config) {
        steps.withCredentials([steps.file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
            def status = [:]
            
            status.pods = steps.sh(
                script: "kubectl get pods -n ${config.namespace} -l app=${config.image} -o json",
                returnStdout: true
            )
            
            status.deployment = steps.sh(
                script: "kubectl get deployment ${config.image} -n ${config.namespace} -o json",
                returnStdout: true
            )
            
            status.service = steps.sh(
                script: "kubectl get service ${config.image} -n ${config.namespace} -o json",
                returnStdout: true
            )
            
            return status
        }
    }
}