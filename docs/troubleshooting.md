
#### `docs/troubleshooting.md`
```markdown
# Troubleshooting Guide

## Common Issues and Solutions

### 1. Library Loading Issues

**Problem:** Pipeline fails with library not found error

**Symptoms:**
Library jenkins-pipeline-library not found

text

**Solutions:**

1. **Check Global Library Configuration:**
   - Go to Jenkins > Manage Jenkins > Configure System
   - Verify library name matches exactly
   - Check repository URL and credentials

2. **Verify Library Version:**
   ```groovy
   @Library('jenkins-pipeline-library@main')_
Check Jenkinsfile Syntax:

groovy
// ✅ Correct
@Library('jenkins-pipeline-library')_

// ❌ Incorrect
library 'jenkins-pipeline-library'
2. Credential Issues
Problem: Pipeline fails with credential errors

Symptoms:

text
Credentials not found
Permission denied
Solutions:

Verify Credential IDs:

groovy
withCredentials([string(credentialsId: 'correct-id', variable: 'TOKEN')]) {
    // Use credential
}
Check Credential Types:

Username/Password → usernamePassword

Secret Text → string

File → file

Verify Permissions:

Ensure Jenkins user has access to credentials

Check folder-level credentials if using Folders plugin

3. Tool Configuration Issues
Problem: Build tools not found

Symptoms:

text
mvn: command not found
npm: command not found
Solutions:

Check Jenkins Global Tool Configuration:

Jenkins > Manage Jenkins > Global Tool Configuration

Verify JDK, Maven, Node.js installations

Use Correct Tool Names:

groovy
tools {
    jdk 'jdk11'        // Must match Jenkins configuration
    maven 'maven-3.8'
    nodejs 'nodejs-18'
}
Fallback to System Tools:

groovy
build(
    language: 'java',
    useSystemTools: true  // Use tools from PATH
)
4. Network and Proxy Issues
Problem: Network timeouts or connection refused

Symptoms:

text
Connection timed out
Connection refused
Solutions:

Configure Proxy Settings:

groovy
build(
    language: 'java',
    proxy: [
        http: 'http://proxy.company.com:8080',
        https: 'http://proxy.company.com:8080',
        noProxy: 'localhost,127.0.0.1,.company.com'
    ]
)
Increase Timeouts:

groovy
build(
    language: 'java',
    timeouts: [
        download: 300,
        connection: 60
    ]
)
Check Firewall Rules:

Verify outbound connections are allowed

Check DNS resolution

5. Resource Exhaustion
Problem: Build fails due to resource limits

Symptoms:

text
OutOfMemoryError
No space left on device
Solutions:

Increase Resource Limits:

groovy
build(
    language: 'java',
    resources: [
        memory: '4g',
        cpu: '2',
        disk: '10g'
    ]
)
Clean Workspace:

groovy
build(
    language: 'java',
    cleanup: [
        workspace: true,
        docker: true,
        tempFiles: true
    ]
)
Optimize Build Process:

groovy
build(
    language: 'java',
    optimization: [
        parallel: true,
        incremental: true,
        cache: true
    ]
)
Debugging Techniques
1. Enable Debug Logging
Add debug output to pipeline:

groovy
@Library('jenkins-pipeline-library')_

node {
    // Enable timestamps
    wrap([$class: 'TimestamperBuildWrapper']) {
        
        // Enable debug mode
        build(
            language: 'java',
            debug: true,
            logLevel: 'DEBUG'
        )
    }
}
2. Step-by-Step Execution
Execute pipeline step by step:

groovy
build(
    language: 'java',
    stages: [
        [
            name: 'Debug Setup',
            steps: {
                echo "Environment variables:"
                sh 'env | sort'
                
                echo "Java version:"
                sh 'java -version'
                
                echo "Maven version:"
                sh 'mvn --version'
            }
        ]
    ]
)
3. Check Jenkins System Logs
Access Jenkins system logs:

Go to Jenkins > Manage Jenkins > System Log

Check for errors and warnings

Look for library-related messages

4. Use Pipeline Syntax Generator
Generate correct pipeline syntax:

Go to your Jenkins pipeline job

Click "Pipeline Syntax"

Use the snippet generator for complex steps

Performance Issues
1. Slow Build Times
Symptoms: Builds taking longer than expected

Solutions:

Enable Caching:

groovy
build(
    language: 'java',
    cache: [
        maven: true,
        nodejs: true,
        docker: true
    ]
)
Use Parallel Execution:

groovy
build(
    language: 'java',
    parallel: [
        tests: true,
        analysis: true,
        builds: false
    ]
)
Optimize Dependencies:

groovy
build(
    language: 'java',
    dependencies: [
        incremental: true,
        offline: false,
        checksums: 'warn'
    ]
)
2. Memory Issues
Symptoms: OutOfMemory errors or high memory usage

Solutions:

Increase Heap Size:

groovy
build(
    language: 'java',
    jvm: [
        xmx: '2g',
        xms: '512m',
        maxMetaspace: '1g'
    ]
)
Use Lightweight Operations:

groovy
build(
    language: 'java',
    optimization: [
        skipTests: false,
        parallelTests: true,
        incrementalCompilation: true
    ]
)
Security Issues
1. Certificate Errors
Symptoms: SSL certificate validation failures

Solutions:

Add Certificates to Trust Store:

groovy
build(
    language: 'java',
    security: [
        certificates: [
            'internal-ca.crt',
            'corporate-ca.crt'
        ]
    ]
)
Temporarily Disable SSL Verification (Not Recommended):

groovy
build(
    language: 'java',
    security: [
        sslVerify: false  // Use only for testing
    ]
)
2. Permission Denied
Symptoms: File permission errors

Solutions:

Check File Permissions:

groovy
build(
    language: 'java',
    permissions: [
        workspace: '755',
        temp: '777',
        logs: '644'
    ]
)
Run as Specific User:

groovy
build(
    language: 'java',
    user: 'jenkins',
    group: 'jenkins'
)
Integration Issues
1. SonarQube Integration
Problem: SonarQube analysis fails

Solutions:

Verify SonarQube Configuration:

groovy
build(
    language: 'java',
    sonarQube: [
        server: 'sonar',
        token: credentials('sonar-token'),
        qualityGate: true
    ]
)
Check SonarQube Scanner:

Verify scanner installation in Jenkins

Check SonarQube server accessibility

2. Docker Integration
Problem: Docker build or push fails

Solutions:

Verify Docker Configuration:

groovy
build(
    language: 'java',
    docker: [
        registry: 'docker.io',
        credentials: 'docker-credentials',
        buildArgs: ['--no-cache']
    ]
)
Check Docker Daemon:

Ensure Docker daemon is running

Verify Jenkins user has Docker permissions

3. Kubernetes Integration
Problem: Kubernetes deployment fails

Solutions:

Verify Kubernetes Configuration:

groovy
deploy(
    environment: 'dev',
    platform: 'kubernetes',
    kubeconfig: 'kubeconfig',
    namespace: 'default'
)
Check Cluster Access:

Verify kubeconfig file is valid

Check cluster connectivity and permissions

Getting Help
1. Collect Debug Information
When asking for help, provide:

Jenkins Version:

groovy
echo "Jenkins version: ${Jenkins.instance.version}"
Pipeline Library Version:

groovy
echo "Library version: main"  // or your branch/version
Relevant Logs:

groovy
build(
    language: 'java',
    logging: [
        level: 'DEBUG',
        file: 'pipeline-debug.log'
    ]
)
2. Community Resources
GitHub Issues: Report bugs and feature requests

Documentation: Check this troubleshooting guide

Stack Overflow: Search for similar issues

Jenkins Community: Join Jenkins mailing lists and forums

3. Emergency Procedures
For critical production issues:

Immediate Rollback:

groovy
try {
    deploy(environment: 'prod')
} catch (Exception e) {
    rollback(environment: 'prod')
    notify(
        type: 'slack',
        channel: '#production-emergency',
        message: "Emergency rollback executed: ${e.message}"
    )
}
Disable Problematic Features:

groovy
build(
    language: 'java',
    securityScan: false,  // Temporarily disable if causing issues
    qualityGate: false
)
By following this troubleshooting guide, you'll be able to quickly identify and resolve common issues with the Jenkins Pipeline Library, ensuring smooth and reliable CI/CD operations.

