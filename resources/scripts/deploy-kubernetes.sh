#!/bin/bash

# Deploy to Kubernetes Script
set -e

echo "🚀 Deploying to Kubernetes..."

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Validate required environment variables
validate_environment() {
    log_info "Validating environment variables"
    
    local required_vars=("KUBECONFIG" "DEPLOY_ENV" "IMAGE_TAG")
    
    for var in "${required_vars[@]}"; do
        if [ -z "${!var}" ]; then
            log_error "Required environment variable $var is not set"
            exit 1
        fi
    done
    
    log_info "✅ Environment validation passed"
}

# Validate Kubernetes access
validate_kubernetes_access() {
    log_info "Validating Kubernetes access"
    
    if ! kubectl cluster-info &> /dev/null; then
        log_error "Cannot connect to Kubernetes cluster"
        exit 1
    fi
    
    local current_context=$(kubectl config current-context)
    log_info "Kubernetes context: $current_context"
    
    # Check if we can list pods
    if ! kubectl get pods &> /dev/null; then
        log_error "Cannot list pods - check RBAC permissions"
        exit 1
    fi
    
    log_info "✅ Kubernetes access validated"
}

# Create namespace if it doesn't exist
create_namespace() {
    local namespace=${1:-$DEPLOY_NAMESPACE}
    
    if [ -z "$namespace" ]; then
        namespace="default"
    fi
    
    log_info "Checking namespace: $namespace"
    
    if ! kubectl get namespace "$namespace" &> /dev/null; then
        log_info "Creating namespace: $namespace"
        kubectl create namespace "$namespace" || true
        
        # Add labels to namespace
        kubectl label namespace "$namespace" \
            environment="$DEPLOY_ENV" \
            team="$TEAM" \
            managed-by="jenkins" || true
    else
        log_info "Namespace $namespace already exists"
    fi
}

# Create Kubernetes secrets from environment
create_secrets() {
    local namespace=${1:-$DEPLOY_NAMESPACE}
    
    if [ -z "$namespace" ]; then
        namespace="default"
    fi
    
    log_info "Creating/updating secrets in namespace: $namespace"
    
    # Create docker-registry secret if registry credentials are provided
    if [ -n "$DOCKER_REGISTRY" ] && [ -n "$DOCKER_USERNAME" ] && [ -n "$DOCKER_PASSWORD" ]; then
        log_info "Creating docker-registry secret"
        kubectl create secret docker-registry docker-registry \
            --docker-server="$DOCKER_REGISTRY" \
            --docker-username="$DOCKER_USERNAME" \
            --docker-password="$DOCKER_PASSWORD" \
            --namespace="$namespace" \
            --dry-run=client -o yaml | kubectl apply -f - || true
    fi
    # Create generic secrets from environment variables prefixed with K8S_SECRET_
    for var in $(env | grep ^K8S_SECRET_); do
        local secret_name=$(echo "$var" | cut -d'=' -f1 | sed 's/K8S_SECRET_//' | tr '[:upper:]' '[:lower:]')
        local secret_value=$(echo "$var" | cut -d'=' -f2-)
        
        log_info "Creating secret: $secret_name"
        kubectl create secret generic "$secret_name" \
            --from-literal=value="$secret_value" \
            --namespace="$namespace" \
            --dry-run=client -o yaml | kubectl apply -f - || true
    done
    
    # Create secrets from files if secret files directory exists
    if [ -d "k8s/secrets" ]; then
        for secret_file in k8s/secrets/*; do
            if [ -f "$secret_file" ]; then
                local secret_name=$(basename "$secret_file" | sed 's/\..*//')
                log_info "Creating secret from file: $secret_name"
                kubectl create secret generic "$secret_name" \
                    --from-file="$secret_file" \
                    --namespace="$namespace" \
                    --dry-run=client -o yaml | kubectl apply -f - || true
            fi
        done
    fi
}

# Create config maps
create_config_maps() {
    local namespace=${1:-$DEPLOY_NAMESPACE}
    
    if [ -z "$namespace" ]; then
        namespace="default"
    fi
    
    log_info "Creating/updating config maps in namespace: $namespace"
    
    # Create app-config configmap
    if [ -f "config/application.properties" ] || [ -f "config/app-config.yaml" ]; then
        log_info "Creating app-config configmap"
        kubectl create configmap app-config \
            --from-file=config/ \
            --namespace="$namespace" \
            --dry-run=client -o yaml | kubectl apply -f - || true
    fi
    
    # Create environment specific configmap
    kubectl create configmap environment-config \
        --from-literal=environment="$DEPLOY_ENV" \
        --from-literal=version="$IMAGE_TAG" \
        --from-literal=deployed-by="jenkins" \
        --namespace="$namespace" \
        --dry-run=client -o yaml | kubectl apply -f - || true
}

# Validate Kubernetes manifests
validate_manifests() {
    log_info "Validating Kubernetes manifests"
    
    if [ ! -d "k8s" ]; then
        log_warn "k8s directory not found, skipping manifest validation"
        return 0
    fi
    
    local manifests=($(find k8s -name "*.yaml" -o -name "*.yml"))
    
    if [ ${#manifests[@]} -eq 0 ]; then
        log_warn "No Kubernetes manifests found in k8s directory"
        return 0
    fi
    
    for manifest in "${manifests[@]}"; do
        log_info "Validating manifest: $manifest"
        
        # Validate YAML syntax
        if ! kubectl apply --dry-run=client --validate=true -f "$manifest" &> /dev/null; then
            log_error "Invalid Kubernetes manifest: $manifest"
            exit 1
        fi
        
        # Use kubeval if available
        if command -v kubeval &> /dev/null; then
            kubeval "$manifest" || log_warn "Kubeval validation failed for $manifest"
        fi
    done
    
    log_info "✅ All Kubernetes manifests validated"
}

# Deploy using kubectl
deploy_kubectl() {
    local namespace=${1:-$DEPLOY_NAMESPACE}
    local strategy=${2:-"rolling"}
    
    if [ -z "$namespace" ]; then
        namespace="default"
    fi
    
    log_info "Deploying using kubectl to namespace: $namespace"
    
    if [ ! -d "k8s" ]; then
        log_error "k8s directory not found"
        exit 1
    fi
    
    # Process and apply all manifests
    for manifest in k8s/*.yaml k8s/*.yml; do
        if [ -f "$manifest" ]; then
            log_info "Processing manifest: $manifest"
            
            # Replace environment variables in manifest
            local processed_manifest=$(mktemp)
            envsubst < "$manifest" > "$processed_manifest"
            
            # Apply the manifest
            kubectl apply -f "$processed_manifest" --namespace="$namespace" || {
                log_error "Failed to apply manifest: $manifest"
                rm "$processed_manifest"
                exit 1
            }
            
            rm "$processed_manifest"
        fi
    done
    
    # Wait for deployment to complete
    wait_for_deployment "$namespace" "$strategy"
}

# Deploy using Helm
deploy_helm() {
    local namespace=${1:-$DEPLOY_NAMESPACE}
    local strategy=${2:-"rolling"}
    
    if [ -z "$namespace" ]; then
        namespace="default"
    fi
    
    if [ ! -d "charts" ] && [ ! -f "Chart.yaml" ]; then
        log_warn "Helm charts not found, skipping Helm deployment"
        return 0
    fi
    
    log_info "Deploying using Helm to namespace: $namespace"
    
    # Add Helm repositories if required
    if [ -f "helm-repositories.yaml" ]; then
        while read -r repo; do
            local repo_name=$(echo "$repo" | cut -d' ' -f1)
            local repo_url=$(echo "$repo" | cut -d' ' -f2)
            helm repo add "$repo_name" "$repo_url" || true
        done < helm-repositories.yaml
        helm repo update
    fi
    
    # Create values file for environment
    local values_file="values-${DEPLOY_ENV}.yaml"
    if [ ! -f "$values_file" ]; then
        values_file="values.yaml"
    fi
    
    # Deploy using Helm
    if [ -f "Chart.yaml" ]; then
        # Single chart in current directory
        local release_name="${HELM_RELEASE_NAME:-$JOB_NAME}"
        
        helm upgrade --install "$release_name" . \
            --namespace "$namespace" \
            --set image.tag="$IMAGE_TAG" \
            --set image.repository="$DOCKER_IMAGE" \
            --values "$values_file" \
            --atomic \
            --timeout 10m \
            --wait || {
            log_error "Helm deployment failed"
            exit 1
        }
    elif [ -d "charts" ]; then
        # Multiple charts
        for chart_dir in charts/*; do
            if [ -f "$chart_dir/Chart.yaml" ]; then
                local chart_name=$(basename "$chart_dir")
                local release_name="${HELM_RELEASE_NAME:-$chart_name}"
                
                log_info "Deploying Helm chart: $chart_name"
                
                helm upgrade --install "$release_name" "$chart_dir" \
                    --namespace "$namespace" \
                    --set image.tag="$IMAGE_TAG" \
                    --set image.repository="$DOCKER_IMAGE" \
                    --values "$values_file" \
                    --atomic \
                    --timeout 10m \
                    --wait || {
                    log_error "Helm deployment failed for chart: $chart_name"
                    exit 1
                }
            fi
        done
    fi
    
    log_info "✅ Helm deployment completed"
}

# Wait for deployment to complete
wait_for_deployment() {
    local namespace=${1:-$DEPLOY_NAMESPACE}
    local strategy=${2:-"rolling"}
    
    if [ -z "$namespace" ]; then
        namespace="default"
    fi
    
    log_info "Waiting for deployment to complete in namespace: $namespace"
    
    # Get deployment names
    local deployments=$(kubectl get deployments -n "$namespace" -o jsonpath='{.items[*].metadata.name}')
    
    for deployment in $deployments; do
        log_info "Waiting for deployment: $deployment"
        
        kubectl rollout status "deployment/$deployment" \
            --namespace "$namespace" \
            --timeout=600s || {
            log_error "Deployment $deployment failed to rollout"
            
            # Show rollout history for debugging
            kubectl rollout history "deployment/$deployment" --namespace "$namespace"
            
            # Show failing pods
            kubectl get pods --namespace "$namespace" -l app="$deployment" --field-selector=status.phase!=Running
            
            exit 1
        }
        
        log_info "✅ Deployment $deployment is ready"
    done
    
    # Wait for statefulsets if any
    local statefulsets=$(kubectl get statefulsets -n "$namespace" -o jsonpath='{.items[*].metadata.name}' 2>/dev/null || true)
    for statefulset in $statefulsets; do
        log_info "Waiting for statefulset: $statefulset"
        kubectl rollout status "statefulset/$statefulset" --namespace "$namespace" --timeout=300s || true
    done
    
    log_info "✅ All deployments are ready"
}

# Perform health checks
perform_health_checks() {
    local namespace=${1:-$DEPLOY_NAMESPACE}
    
    if [ -z "$namespace" ]; then
        namespace="default"
    fi
    
    log_info "Performing health checks in namespace: $namespace"
    
    # Get all services
    local services=$(kubectl get services -n "$namespace" -o jsonpath='{.items[*].metadata.name}')
    
    for service in $services; do
        local service_type=$(kubectl get service "$service" -n "$namespace" -o jsonpath='{.spec.type}')
        
        if [ "$service_type" = "LoadBalancer" ] || [ "$service_type" = "ClusterIP" ]; then
            log_info "Performing health check for service: $service"
            
            # Get service port
            local port=$(kubectl get service "$service" -n "$namespace" -o jsonpath='{.spec.ports[0].port}')
            
            # Try to access the service
            if kubectl run health-check --rm -i --restart=Never --image=curlimages/curl --namespace="$namespace" -- \
                curl -f "http://$service:$port/health" > /dev/null 2>&1; then
                log_info "✅ Health check passed for service: $service"
            else
                log_warn "⚠️ Health check failed for service: $service"
            fi
        fi
    done
    
    # Check pod status
    local failing_pods=$(kubectl get pods -n "$namespace" --field-selector=status.phase!=Running --no-headers | wc -l)
    if [ "$failing_pods" -gt 0 ]; then
        log_warn "There are $failing_pods pods not in Running state"
        kubectl get pods -n "$namespace" --field-selector=status.phase!=Running
    fi
    
    log_info "✅ Health checks completed"
}

# Perform rolling update
perform_rolling_update() {
    local namespace=${1:-$DEPLOY_NAMESPACE}
    
    if [ -z "$namespace" ]; then
        namespace="default"
    fi
    
    log_info "Performing rolling update in namespace: $namespace"
    
    # Get deployment names
    local deployments=$(kubectl get deployments -n "$namespace" -o jsonpath='{.items[*].metadata.name}')
    
    for deployment in $deployments; do
        log_info "Performing rolling update for: $deployment"
        
        # Trigger rolling update by updating annotation
        kubectl patch deployment "$deployment" \
            --namespace "$namespace" \
            -p "{\"spec\":{\"template\":{\"metadata\":{\"annotations\":{\"deployed-at\":\"$(date +%s)\"}}}}}" || {
            log_error "Failed to trigger rolling update for: $deployment"
            exit 1
        }
    done
    
    wait_for_deployment "$namespace" "rolling"
}

# Perform blue-green deployment
perform_blue_green_deployment() {
    local namespace=${1:-$DEPLOY_NAMESPACE}
    
    if [ -z "$namespace" ]; then
        namespace="default"
    fi
    
    log_info "Performing blue-green deployment in namespace: $namespace"
    
    # This is a simplified blue-green deployment
    # In production, you might want to use a more sophisticated approach
    
    local deployment_name="${DEPLOYMENT_NAME:-$JOB_NAME}"
    local active_deployment="$deployment_name-blue"
    local inactive_deployment="$deployment_name-green"
    
    # Determine current active deployment
    local current_service=$(kubectl get service "$deployment_name" -n "$namespace" -o jsonpath='{.spec.selector.version}' 2>/dev/null || echo "blue")
    local new_deployment=""
    
    if [ "$current_service" = "blue" ]; then
        new_deployment="$inactive_deployment"
        new_color="green"
    else
        new_deployment="$active_deployment"
        new_color="blue"
    fi
    
    log_info "Current active: $current_service, deploying to: $new_color"
    
    # Deploy new version
    log_info "Deploying new version to: $new_deployment"
    
    # Apply new deployment
    for manifest in k8s/*.yaml k8s/*.yml; do
        if [ -f "$manifest" ]; then
            # Update the deployment name and labels for blue-green
            sed "s/name: $deployment_name/name: $new_deployment/g" "$manifest" | \
            sed "s/version: $current_service/version: $new_color/g" | \
            kubectl apply -n "$namespace" -f - || {
                log_error "Failed to apply blue-green deployment"
                exit 1
            }
        fi
    done
    
    # Wait for new deployment to be ready
    log_info "Waiting for new deployment to be ready"
    kubectl rollout status "deployment/$new_deployment" --namespace "$namespace" --timeout=600s || {
        log_error "New deployment failed to become ready"
        exit 1
    }
    
    # Switch traffic to new deployment
    log_info "Switching traffic to new deployment"
    kubectl patch service "$deployment_name" \
        --namespace "$namespace" \
        -p "{\"spec\":{\"selector\":{\"version\":\"$new_color\"}}}" || {
        log_error "Failed to switch traffic to new deployment"
        exit 1
    }
    
    # Keep old deployment for rollback capability
    log_info "Blue-green deployment completed. Old deployment ($current_service) kept for rollback"
}

# Perform canary deployment
perform_canary_deployment() {
    local namespace=${1:-$DEPLOY_NAMESPACE}
    local canary_percentage=${2:-10}
    
    if [ -z "$namespace" ]; then
        namespace="default"
    fi
    
    log_info "Performing canary deployment (${canary_percentage}%) in namespace: $namespace"
    
    local deployment_name="${DEPLOYMENT_NAME:-$JOB_NAME}"
    local canary_deployment="$deployment_name-canary"
    
    # Deploy canary version
    log_info "Deploying canary version"
    
    # Apply canary deployment with reduced replicas
    for manifest in k8s/*.yaml k8s/*.yml; do
        if [ -f "$manifest" ]; then
            # Create canary deployment
            sed "s/name: $deployment_name/name: $canary_deployment/g" "$manifest" | \
            sed "s/replicas: [0-9]*/replicas: 1/g" | \
            kubectl apply -n "$namespace" -f - || {
                log_error "Failed to apply canary deployment"
                exit 1
            }
        fi
    done
    
    # Wait for canary to be ready
    log_info "Waiting for canary deployment to be ready"
    kubectl rollout status "deployment/$canary_deployment" --namespace "$namespace" --timeout=300s || {
        log_error "Canary deployment failed"
        exit 1
    }
    
    log_info "Canary deployment is ready. Monitor metrics before proceeding with full rollout."
    
    # In a real scenario, you would:
    # 1. Monitor canary metrics
    # 2. Gradually increase traffic to canary
    # 3. Roll out to full deployment if metrics are good
    # 4. Roll back if metrics are bad
    
    log_info "Canary deployment phase completed. Manual approval required for full rollout."
}

# Rollback deployment
rollback_deployment() {
    local namespace=${1:-$DEPLOY_NAMESPACE}
    
    if [ -z "$namespace" ]; then
        namespace="default"
    fi
    
    log_info "Rolling back deployment in namespace: $namespace"
    
    # Get deployment names
    local deployments=$(kubectl get deployments -n "$namespace" -o jsonpath='{.items[*].metadata.name}')
    
    for deployment in $deployments; do
        log_info "Rolling back deployment: $deployment"
        
        kubectl rollout undo "deployment/$deployment" --namespace "$namespace" || {
            log_error "Failed to rollback deployment: $deployment"
            exit 1
        }
        
        kubectl rollout status "deployment/$deployment" --namespace "$namespace" --timeout=600s || {
            log_error "Rollback failed for deployment: $deployment"
            exit 1
        }
        
        log_info "✅ Successfully rolled back: $deployment"
    done
}

# Clean up resources
cleanup_resources() {
    local namespace=${1:-$DEPLOY_NAMESPACE}
    
    if [ -z "$namespace" ]; then
        namespace="default"
    fi
    
    log_info "Cleaning up temporary resources in namespace: $namespace"
    
    # Delete health check pods
    kubectl delete pod health-check --namespace "$namespace" --ignore-not-found=true
    
    # Delete any completed jobs
    kubectl delete jobs --field-selector=status.successful=1 --namespace "$namespace" --ignore-not-found=true
    
    log_info "✅ Cleanup completed"
}

# Main deployment function
main() {
    local deployment_strategy="${DEPLOYMENT_STRATEGY:-rolling}"
    local namespace="${DEPLOY_NAMESPACE:-default}"
    
    log_info "Starting Kubernetes deployment"
    log_info "Environment: $DEPLOY_ENV"
    log_info "Namespace: $namespace"
    log_info "Image Tag: $IMAGE_TAG"
    log_info "Strategy: $deployment_strategy"
    
    # Validate environment and access
    validate_environment
    validate_kubernetes_access
    
    # Create namespace if needed
    create_namespace "$namespace"
    
    # Create secrets and config maps
    create_secrets "$namespace"
    create_config_maps "$namespace"
    
    # Validate manifests
    validate_manifests
    
    # Perform deployment based on strategy
    case "$deployment_strategy" in
        "rolling")
            if [ -f "Chart.yaml" ] || [ -d "charts" ]; then
                deploy_helm "$namespace" "rolling"
            else
                deploy_kubectl "$namespace" "rolling"
            fi
            ;;
        "blue-green")
            perform_blue_green_deployment "$namespace"
            ;;
        "canary")
            perform_canary_deployment "$namespace"
            ;;
        *)
            log_error "Unknown deployment strategy: $deployment_strategy"
            exit 1
            ;;
    esac
    
    # Perform health checks
    perform_health_checks "$namespace"
    
    # Clean up temporary resources
    cleanup_resources "$namespace"
    
    log_info "✅ Kubernetes deployment completed successfully"
    
    # Display deployment summary
    echo ""
    echo "📊 Deployment Summary"
    echo "===================="
    echo "Environment: $DEPLOY_ENV"
    echo "Namespace: $namespace"
    echo "Strategy: $deployment_strategy"
    echo "Status: ✅ Success"
    echo ""
    echo "Deployed Resources:"
    kubectl get all -n "$namespace" --show-labels
}

# Handle script execution
if [ "${1}" = "rollback" ]; then
    rollback_deployment "${2:-$DEPLOY_NAMESPACE}"
else
    main
fi