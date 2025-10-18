#!/usr/bin/groovy

/**
 * docker.groovy - Shared Docker pipeline steps
 */

def call(Map params = [:]) {
    def config = [
        action: params.action ?: 'build',
        registry: params.registry ?: 'docker.io',
        image: params.image ?: "${env.JOB_NAME}",
        tag: params.tag ?: "${env.BUILD_NUMBER}",
        dockerfile: params.dockerfile ?: 'Dockerfile',
        context: params.context ?: '.',
        buildArgs: params.buildArgs ?: [],
        push: params.push ?: true,
        credentials: params.credentials ?: 'docker-credentials'
    ]
    
    script {
        switch(config.action) {
            case 'build':
                dockerBuild(config)
                break
            case 'push':
                dockerPush(config)
                break
            case 'buildAndPush':
                dockerBuildAndPush(config)
                break
            case 'scan':
                dockerScan(config)
                break
            default:
                error "Unknown Docker action: ${config.action}"
        }
    }
}

def dockerBuild(Map config) {
    echo "Building Docker image: ${config.image}:${config.tag}"
    
    def buildArgs = config.buildArgs.collect { k, v -> "--build-arg ${k}=${v}" }.join(' ')
    
    docker.build("${config.image}:${config.tag}", """
        --file ${config.dockerfile}
        ${buildArgs}
        ${config.context}
    """.stripIndent().trim())
}

def dockerPush(Map config) {
    echo "Pushing Docker image to ${config.registry}"
    
    docker.withRegistry("https://${config.registry}", config.credentials) {
        docker.image("${config.image}:${config.tag}").push()
        
        if (env.BRANCH_NAME == 'main' || env.BRANCH_NAME == 'master') {
            docker.image("${config.image}:${config.tag}").push('latest')
        }
    }
}

def dockerBuildAndPush(Map config) {
    dockerBuild(config)
    
    if (config.push) {
        dockerPush(config)
    }
}

def dockerScan(Map config) {
    echo "Scanning Docker image for vulnerabilities"
    
    def imageName = "${config.image}:${config.tag}"
    
    sh """
        trivy image --format json --output reports/trivy-docker.json ${imageName} || true
        trivy image --severity HIGH,CRITICAL --exit-code 0 ${imageName}
    """
    
    sh """
        docker scout cves ${imageName} --format sarif --output reports/docker-scout.sarif || true
    """
}

// Helper methods
def dockerLogin(String registry, String credentialsId = 'docker-credentials') {
    docker.withRegistry("https://${registry}", credentialsId) {
        echo "Logged in to Docker registry: ${registry}"
    }
}

def dockerCleanup() {
    echo "Cleaning up Docker resources"
    
    sh '''
        docker system prune -f || true
        docker volume prune -f || true
    '''
}

def dockerCompose(Map params = [:]) {
    def config = [
        action: params.action ?: 'up',
        file: params.file ?: 'docker-compose.yml',
        services: params.services ?: [],
        build: params.build ?: false,
        detach: params.detach ?: true
    ]
    
    def command = "docker-compose -f ${config.file}"
    
    switch(config.action) {
        case 'up':
            command += " up"
            if (config.detach) command += " -d"
            if (config.build) command += " --build"
            if (config.services) command += " ${config.services.join(' ')}"
            break
        case 'down':
            command += " down"
            break
        case 'build':
            command += " build"
            if (config.services) command += " ${config.services.join(' ')}"
            break
        case 'logs':
            command += " logs"
            if (config.services) command += " ${config.services.join(' ')}"
            break
        default:
            error "Unknown docker-compose action: ${config.action}"
    }
    
    sh command
}

return this