#!/bin/bash

# Setup Environment Script
set -e

echo "🔧 Setting up build environment..."

# Colors for output
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

# Detect operating system
detect_os() {
    case "$(uname -s)" in
        Darwin)
            echo "macos"
            ;;
        Linux)
            if [ -f /etc/os-release ]; then
                . /etc/os-release
                echo "$ID"
            else
                echo "linux"
            fi
            ;;
        *)
            echo "unknown"
            ;;
    esac
}

# Install system dependencies
install_system_deps() {
    local os=$(detect_os)
    
    case "$os" in
        ubuntu|debian)
            log_info "Installing system dependencies on Ubuntu/Debian"
            sudo apt-get update
            sudo apt-get install -y \
                curl wget git unzip \
                build-essential \
                python3 python3-pip python3-venv \
                default-jdk maven gradle \
                nodejs npm
            ;;
        centos|rhel|fedora)
            log_info "Installing system dependencies on CentOS/RHEL/Fedora"
            sudo yum update -y
            sudo yum install -y \
                curl wget git unzip \
                python3 python3-pip \
                java-11-openjdk-devel maven gradle \
                nodejs npm
            ;;
        macos)
            log_info "Installing system dependencies on macOS"
            if ! command -v brew &> /dev/null; then
                log_error "Homebrew not installed. Please install Homebrew first."
                exit 1
            fi
            brew update
            brew install \
                curl wget git \
                python3 \
                openjdk maven gradle \
                node
            ;;
        *)
            log_warn "Unsupported OS: $os. Please install dependencies manually."
            ;;
    esac
}

# Setup Java environment
setup_java() {
    log_info "Setting up Java environment"
    
    if command -v java &> /dev/null; then
        export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
        export PATH=$JAVA_HOME/bin:$PATH
        log_info "Java version: $(java -version 2>&1 | head -n1)"
    else
        log_warn "Java not installed"
    fi
    
    if command -v mvn &> /dev/null; then
        log_info "Maven version: $(mvn --version 2>&1 | head -n1)"
    else
        log_warn "Maven not installed"
    fi
    
    if command -v gradle &> /dev/null; then
        log_info "Gradle version: $(gradle --version 2>&1 | head -n1)"
    else
        log_warn "Gradle not installed"
    fi
}

# Setup Node.js environment
setup_nodejs() {
    log_info "Setting up Node.js environment"
    
    if command -v node &> /dev/null; then
        export NODE_HOME=$(dirname $(dirname $(which node)))
        export PATH=$NODE_HOME/bin:$PATH
        log_info "Node.js version: $(node --version)"
        log_info "npm version: $(npm --version)"
        
        # Setup npm registry
        npm config set registry https://registry.npmjs.org/
        npm config set strict-ssl true
        
        # Install global packages
        npm install -g yarn typescript @angular/cli react-scripts || true
    else
        log_warn "Node.js not installed"
    fi
}

# Setup Python environment
setup_python() {
    log_info "Setting up Python environment"
    
    if command -v python3 &> /dev/null; then
        export PYTHON_HOME=$(dirname $(dirname $(which python3)))
        export PATH=$PYTHON_HOME/bin:$PATH
        log_info "Python version: $(python3 --version)"
        
        # Create virtual environment
        python3 -m venv venv
        source venv/bin/activate
        
        # Upgrade pip
        pip install --upgrade pip
        
        # Install common development tools
        pip install black flake8 mypy pylint pytest coverage bandit safety || true
    else
        log_warn "Python3 not installed"
    fi
}

# Setup Docker
setup_docker() {
    log_info "Setting up Docker environment"
    
    if command -v docker &> /dev/null; then
        log_info "Docker version: $(docker --version)"
        
        # Test docker access
        if docker info &> /dev/null; then
            log_info "Docker daemon is accessible"
        else
            log_warn "Docker daemon is not accessible"
        fi
    else
        log_warn "Docker not installed"
    fi
    
    # Install Docker Compose if not present
    if ! command -v docker-compose &> /dev/null; then
        log_info "Installing Docker Compose"
        sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
        sudo chmod +x /usr/local/bin/docker-compose
    fi
}

# Setup Kubernetes tools
setup_kubernetes() {
    log_info "Setting up Kubernetes tools"
    
    # kubectl
    if ! command -v kubectl &> /dev/null; then
        log_info "Installing kubectl"
        curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
        sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
        rm kubectl
    fi
    log_info "kubectl version: $(kubectl version --client --short 2>/dev/null || echo 'unknown')"
    
    # helm
    if ! command -v helm &> /dev/null; then
        log_info "Installing Helm"
        curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
    fi
    log_info "helm version: $(helm version --short 2>/dev/null || echo 'unknown')"
    
    # k9s (optional)
    if ! command -v k9s &> /dev/null; then
        log_info "Installing k9s (optional)"
        wget https://github.com/derailed/k9s/releases/latest/download/k9s_Linux_amd64.tar.gz
        tar -xzf k9s_Linux_amd64.tar.gz
        sudo mv k9s /usr/local/bin/
        rm k9s_Linux_amd64.tar.gz
    fi
}

# Setup security tools
setup_security_tools() {
    log_info "Setting up security tools"
    
    # trivy
    if ! command -v trivy &> /dev/null; then
        log_info "Installing Trivy"
        curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin
    fi
    log_info "trivy version: $(trivy --version | head -n1)"
    
    # snyk
    if ! command -v snyk &> /dev/null; then
        log_info "Installing Snyk"
        npm install -g snyk || true
    fi
    
    # grype
    if ! command -v grype &> /dev/null; then
        log_info "Installing Grype"
        curl -sSfL https://raw.githubusercontent.com/anchore/grype/main/install.sh | sh -s -- -b /usr/local/bin
    fi
    
    # OWASP Dependency Check
    if ! command -v dependency-check.sh &> /dev/null; then
        log_info "Installing OWASP Dependency Check"
        wget https://github.com/jeremylong/DependencyCheck/releases/latest/download/dependency-check-8.2.1-release.zip
        unzip dependency-check-8.2.1-release.zip -d /opt/
        ln -s /opt/dependency-check/bin/dependency-check.sh /usr/local/bin/dependency-check.sh
        rm dependency-check-8.2.1-release.zip
    fi
}

# Setup development tools
setup_dev_tools() {
    log_info "Setting up development tools"
    
    # git configuration
    git config --global init.defaultBranch main
    git config --global user.name "Jenkins Pipeline"
    git config --global user.email "jenkins@company.com"
    
    # Install additional tools
    local os=$(detect_os)
    case "$os" in
        ubuntu|debian)
            sudo apt-get install -y jq yamllint shellcheck
            ;;
        centos|rhel|fedora)
            sudo yum install -y jq yamllint ShellCheck
            ;;
        macos)
            brew install jq yamllint shellcheck
            ;;
    esac
}

# Setup Jenkins specific configurations
setup_jenkins_config() {
    log_info "Setting up Jenkins configurations"
    
    # Create Jenkins workspace directory
    mkdir -p /tmp/jenkins-workspace
    
    # Set up Maven settings
    mkdir -p ~/.m2
    cat > ~/.m2/settings.xml << EOF
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
    <localRepository>/tmp/jenkins-workspace/.m2/repository</localRepository>
    <interactiveMode>false</interactiveMode>
    <offline>false</offline>
</settings>
EOF
    
    # Set up npm cache
    npm config set cache /tmp/jenkins-workspace/.npm --global
}

# Validate setup
validate_setup() {
    log_info "Validating environment setup"
    
    local tools=("java" "mvn" "node" "npm" "python3" "docker" "kubectl" "helm" "trivy")
    local missing_tools=()
    
    for tool in "${tools[@]}"; do
        if command -v "$tool" &> /dev/null; then
            log_info "✅ $tool is installed"
        else
            log_warn "⚠️  $tool is not installed"
            missing_tools+=("$tool")
        fi
    done
    
    if [ ${#missing_tools[@]} -ne 0 ]; then
        log_warn "Missing tools: ${missing_tools[*]}"
        log_warn "Some pipeline features may not work correctly"
    else
        log_info "✅ All required tools are installed"
    fi
}

# Main setup function
main() {
    log_info "Starting environment setup..."
    
    # Install system dependencies
    install_system_deps
    
    # Setup language environments
    setup_java
    setup_nodejs
    setup_python
    
    # Setup container and orchestration tools
    setup_docker
    setup_kubernetes
    
    # Setup security tools
    setup_security_tools
    
    # Setup development tools
    setup_dev_tools
    
    # Setup Jenkins configurations
    setup_jenkins_config
    
    # Validate setup
    validate_setup
    
    log_info "✅ Environment setup completed successfully"
    
    # Print summary
    echo ""
    echo "Environment Summary:"
    echo "===================="
    command -v java &> /dev/null && echo "Java: $(java -version 2>&1 | head -n1)" || echo "Java: Not installed"
    command -v node &> /dev/null && echo "Node.js: $(node --version)" || echo "Node.js: Not installed"
    command -v python3 &> /dev/null && echo "Python: $(python3 --version)" || echo "Python: Not installed"
    command -v docker &> /dev/null && echo "Docker: $(docker --version)" || echo "Docker: Not installed"
    command -v kubectl &> /dev/null && echo "kubectl: Installed" || echo "kubectl: Not installed"
    command -v trivy &> /dev/null && echo "trivy: Installed" || echo "trivy: Not installed"
    echo ""
    echo "Next steps:"
    echo "1. Configure cloud credentials"
    echo "2. Set up Jenkins plugins"
    echo "3. Configure pipeline libraries"
    echo "4. Test pipeline execution"
}

# Run main function
main "$@"