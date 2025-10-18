# Pipeline Library Guide

## Architecture

The Jenkins Pipeline Library follows a modular architecture with these components:

- **Shared Steps** (`vars/`) - Reusable pipeline steps
- **Core Classes** (`src/`) - Business logic and utilities  
- **Templates** (`resources/`) - Pipeline templates and configurations
- **Examples** (`examples/`) - Complete pipeline examples

## Shared Steps Reference

### build.groovy

Builds applications for different languages and build tools.

**Parameters:**
- `language` (String) - Project language (java, nodejs, python, dotnet)
- `buildTool` (String) - Build tool (maven, gradle, npm, pip)
- `jdkVersion` (String) - JDK version (default: 11)
- `nodeVersion` (String) - Node.js version (default: 18)
- `sonarQube` (Boolean) - Enable SonarQube analysis (default: false)
- `qualityGate` (Boolean) - Enable quality gates (default: true)

**Example:**
```groovy
build(
    language: 'java',
    buildTool: 'maven', 
    jdkVersion: '11',
    sonarQube: true
)
```

### test.groovy

Runs tests and generates coverage reports.

**Parameters:**

- `language` (String) - Project language
- `testType` (String) - Type of tests (unit, integration, e2e, all)
- `coverage` (Boolean) - Generate coverage reports (default: true)
- `parallel` (Boolean) - Run tests in parallel (default: false)

**Example:**

```groovy
test(
    language: 'java',
    testType: 'all',
    coverage: true,
    parallel: true
)
```

### deploy.groovy

Deploys applications to various platforms.

**Parameters:**

- `environment` (String) - Target environment (dev, staging, prod)
- `platform` (String) - Deployment platform (kubernetes, docker, openshift)
- `deploymentStrategy` (String) - Deployment strategy (rolling, blue-green, canary)
- `healthCheck` (Boolean) - Perform health checks (default: true)

**Example:**

```groovy
deploy(
    environment: 'staging',
    platform: 'kubernetes',
    deploymentStrategy: 'rolling'
)
```

### securityScan.groovy

Performs security vulnerability scanning.

**Parameters:**

- `language` (String) - Project language
- `scanType` (String) - Type of scan (sast, dast, dependency, container, all)
- `failOnVulnerabilities` (Boolean) - Fail build on vulnerabilities (default: true)
- `severityThreshold` (String) - Minimum severity to fail (HIGH, CRITICAL)

**Example:**

```groovy
securityScan(
    language: 'java',
    scanType: 'all',
    failOnVulnerabilities: true
)
```

## Core Classes Reference

### PipelineBuilder

Fluent API for building complex pipelines.

Methods:

- forLanguage(String language) - Set project language
- withBuildTool(String buildTool) - Set build tool
- withQualityGates(boolean enabled) - Enable quality gates
- withSecurityScan(boolean enabled) - Enable security scanning
- addStage(String name, Closure stage) - Add custom stage
- build() - Build the pipeline

**Example:**

```groovy
def builder = new org.company.PipelineBuilder(this)

builder.forLanguage('java')
      .withBuildTool('maven')
      .withQualityGates(true)
      .addStage('Custom Stage') {
          echo "Running custom logic"
      }
      .build()()
```

### QualityGate

Manages quality gates and validation.

Methods:

- validateCodeQuality(Map config) - Validate code quality metrics
- enforceStandards() - Enforce coding standards

**Example:**

```groovy
def qualityGate = new org.company.QualityGate(this)

qualityGate.validateCodeQuality(
    language: 'java',
    coverageThreshold: 80,
    duplicationThreshold: 5
)
```

### SecurityScanner

Performs security scanning and vulnerability management.

Methods:

- scanDependencies(Map config) - Scan dependencies for vulnerabilities
- scanContainer(String imageName) - Scan container images
- generateSecurityReport(List vulnerabilities) - Generate security reports

**Example:**

```groovy
def securityScanner = new org.company.SecurityScanner(this)

def vulnerabilities = securityScanner.scanDependencies(
    language: 'java',
    failOnVulnerabilities: true
)
```

## Customizing Pipelines

### Adding Custom Stages

```groovy
build(
    language: 'java',
    stages: [
        [
            name: 'Custom Analysis',
            steps: {
                echo "Running custom analysis"
                sh './custom-script.sh'
            }
        ],
        [
            name: 'Performance Tests',
            steps: {
                echo "Running performance tests"
                sh 'mvn gatling:test'
            }
        ]
    ]
)
```

### Environment-Specific Configuration

```groovy
def environment = env.BRANCH_NAME == 'main' ? 'prod' : 'dev'

build(
    language: 'java',
    environment: environment,
    deploy: environment == 'prod'
)
```

### Conditional Execution

```groovy
build(
    language: 'java',
    stages: [
        [
            name: 'Security Scan',
            when: {
                return env.BRANCH_NAME == 'main'
            },
            steps: {
                securityScan(language: 'java')
            }
        ]
    ]
)
```

## Best Practices

### 1. Use PipelineBuilder for Complex Pipelines

```groovy
// ✅ Recommended
def builder = new org.company.PipelineBuilder(this)
builder.forLanguage('java')
      .withQualityGates(true)
      .build()()

// ❌ Avoid
build(language: 'java', qualityGate: true)
```

### 2. Configure Quality Gates

```groovy
build(
    language: 'java',
    qualityGate: true,
    coverageThreshold: 80,
    duplicationThreshold: 5,
    securityIssuesThreshold: 0
)
```

### 3. Implement Proper Error Handling

```groovy
try {
    build(language: 'java')
} catch (Exception e) {
    notify(
        type: 'slack',
        status: 'failure',
        message: "Build failed: ${e.message}"
    )
    error "Build failed: ${e.message}"
}
```

### 4. Use Environment-Specific Configurations

```groovy
def config = [
    language: 'java',
    environment: env.BRANCH_NAME
]

if (env.BRANCH_NAME == 'main') {
    config.securityScan = true
    config.qualityGate = true
    config.deploy = true
}

build(config)
```

## Advanced Topics

### Multi-Branch Pipelines

```groovy
def isMainBranch = env.BRANCH_NAME == 'main'
def isFeatureBranch = env.BRANCH_NAME.startsWith('feature/')

def config = [
    language: 'java',
    qualityGate: true
]

if (isMainBranch) {
    config.securityScan = true
    config.deploy = true
} else if (isFeatureBranch) {
    config.securityScan = false
}

build(config)
```

### Parallel Execution

```groovy
build(
    language: 'java',
    stages: [
        [
            name: 'Parallel Tests',
            parallel: [
                'Unit Tests': {
                    sh 'mvn test -Dtest=**/*Test'
                },
                'Integration Tests': {
                    sh 'mvn verify -Dtest=**/*IT'
                }
            ]
        ]
    ]
)
```

### Custom Notifications

```groovy
build(
    language: 'java',
    notifications: [
        [
            type: 'slack',
            channel: '#builds',
            events: ['started', 'success', 'failure']
        ],
        [
            type: 'email',
            recipients: 'team@example.com',
            events: ['failure']
        ]
    ]
)
```

## Extending the Library

### Adding Custom Steps

* Create a new file in vars/ directory
* Implement your custom step
* Add tests in test/vars/

Example: vars/customStep.groovy:

```groovy
def call(Map params = [:]) {
    echo "Running custom step"
    // Your logic here
}
```

### Adding Utility Classes

* Create a new class in src/org/company/
* Implement your business logic
* Add tests in test/src/org/company/

Example: src/org/company/CustomUtil.groovy:

```groovy
package org.company

class CustomUtil implements Serializable {
    private final steps
    
    CustomUtil(steps) {
        this.steps = steps
    }
    
    def customMethod() {
        steps.echo "Custom method executed"
    }
}
```