#!/usr/bin/groovy

/**
 * kubernetes.groovy - Shared Kubernetes pipeline steps
 */

def call(Map params = [:]) {
    def config = [
        action: params.action ?: 'deploy',
        namespace: params.namespace ?: 'default',
        manifest: params.manifest ?: 'k8s/',
        wait: params.wait ?: true,
        timeout: params.timeout ?: 300,
        kubeconfig: params.kubeconfig ?: 'kubeconfig'
    ]
    
    script {
        withCredentials([file(credentialsId: config.kubeconfig, variable: 'KUBECONFIG')]) {
            switch(config.action) {
                case 'deploy':
                    kubectlDeploy(config)
                    break
                case 'delete':
                    kubectlDelete(config)
                    break
                case 'rollout':
                    kubectlRollout(config)
                    break
                case 'status':
                    kubectlStatus(config)
                    break
                default:
                    error "Unknown Kubernetes action: ${config.action}"
            }
        }
    }
}

def kubectlDeploy(Map config) {
    echo "Deploying Kubernetes manifests from ${config.manifest}"
    
    sh """
        kubectl apply -f ${config.manifest} -n ${config.namespace} --validate=false
    """
    
    if (config.wait) {
        sh """
            kubectl wait --for=condition=ready -f ${config.manifest} -n ${config.namespace} --timeout=${config.timeout}s
        """
    }
}

def kubectlDelete(Map config) {
    echo "Deleting Kubernetes resources from ${config.manifest}"
    
    sh """
        kubectl delete -f ${config.manifest} -n ${config.namespace} --ignore-not-found=true
    """
}

def kubectlRollout(Map config) {
    echo "Managing Kubernetes rollout"
    
    def resource = config.resource ?: "deployment/${env.JOB_NAME}"
    
    switch(config.rolloutAction) {
        case 'status':
            sh """
                kubectl rollout status ${resource} -n ${config.namespace} --timeout=${config.timeout}s
            """
            break
        case 'restart':
            sh """
                kubectl rollout restart ${resource} -n ${config.namespace}
            """
            break
        case 'undo':
            sh """
                kubectl rollout undo ${resource} -n ${config.namespace}
            """
            break
        case 'history':
            sh """
                kubectl rollout history ${resource} -n ${config.namespace}
            """
            break
        default:
            error "Unknown rollout action: ${config.rolloutAction}"
    }
}

def kubectlStatus(Map config) {
    echo "Checking Kubernetes status"
    
    sh """
        echo "=== Cluster Info ==="
        kubectl cluster-info
        
        echo "=== Nodes ==="
        kubectl get nodes
        
        echo "=== Pods in ${config.namespace} ==="
        kubectl get pods -n ${config.namespace}
        
        echo "=== Services in ${config.namespace} ==="
        kubectl get services -n ${config.namespace}
        
        echo "=== Deployments in ${config.namespace} ==="
        kubectl get deployments -n ${config.namespace}
    """
}

// Helper methods
def createNamespace(String namespace) {
    sh """
        kubectl create namespace ${namespace} --dry-run=client -o yaml | kubectl apply -f -
    """
}

def createSecret(String name, Map data, String namespace = 'default') {
    def secretData = data.collect { k, v -> "--from-literal=${k}=${v}" }.join(' ')
    
    sh """
        kubectl create secret generic ${name} ${secretData} -n ${namespace} --dry-run=client -o yaml | kubectl apply -f -
    """
}

def createConfigMap(String name, Map data, String namespace = 'default') {
    def configMapData = data.collect { k, v -> "--from-literal=${k}=${v}" }.join(' ')
    
    sh """
        kubectl create configmap ${name} ${configMapData} -n ${namespace} --dry-run=client -o yaml | kubectl apply -f -
    """
}

def getPodLogs(String selector, String namespace = 'default') {
    def logs = sh(
        script: "kubectl logs -l ${selector} -n ${namespace} --tail=100",
        returnStdout: true
    ).trim()
    
    return logs
}

def healthCheck(String selector, String namespace = 'default', int timeout = 300) {
    echo "Performing health check for pods with selector: ${selector}"
    
    sh """
        kubectl wait --for=condition=ready pod -l ${selector} -n ${namespace} --timeout=${timeout}s
    """
}

return this