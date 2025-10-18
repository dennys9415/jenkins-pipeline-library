# Jenkins Pipeline Library

A comprehensive shared library for Jenkins pipelines providing reusable components, best practices, and standardized workflows for CI/CD.

## Features

- **Multi-language Support**: Java, Node.js, Python, .NET
- **Cloud Native**: Kubernetes, Docker, Cloud Foundry
- **Security First**: Integrated security scanning and compliance
- **Quality Gates**: Automated quality checks and reporting
- **Flexible Deployment**: Multiple deployment strategies
- **Extensible**: Easy to customize and extend

## Quick Start

### 1. Configure Jenkins

Add this library to your Jenkins configuration:

```groovy
// In Jenkins Configure System
library identifier: 'jenkins-pipeline-library@main', retriever: modernSCM(
  [$class: 'GitSCMSource',
   remote: 'https://github.com/your-org/jenkins-pipeline-library.git',
   credentialsId: 'github-credentials'])
```

2. Use in Your Jenkinsfile

```groovy
@Library('jenkins-pipeline-library')_

build(
    language: 'java',
    buildTool: 'maven',
    jdkVersion: '11',
    sonarQube: true
)
```

3. Advanced Usage

```groovy
@Library('jenkins-pipeline-library')_

def builder = new org.company.PipelineBuilder(this)

builder.forLanguage('java')
      .withBuildTool('maven')
      .withQualityGates(true)
      .withSecurityScan(true)
      .build()()
```

## Available Pipeline Steps

### Build Steps

* build() - Standard build process

* test() - Test execution with coverage

* securityScan() - Security vulnerability scanning

* deploy() - Deployment to various environments

### Quality Steps

* sonarQubeAnalysis() - Code quality analysis

* checkstyle() - Code style enforcement

* pmd() - Static code analysis

* jacoco() - Code coverage

### Deployment Steps

* kubernetesDeploy() - Kubernetes deployment

* dockerBuild() - Docker image building

* helmDeploy() - Helm chart deployment

## Examples

Check the examples/ directory for complete pipeline examples:

* Java Maven

* Node.js

* Python

* Kubernetes

## Configuration

### Environment Variables

```bash
# Java
export JAVA_HOME=/path/to/java

# Node.js
export NODE_HOME=/path/to/node

# Docker
export DOCKER_REGISTRY=your-registry

# Kubernetes
export KUBECONFIG=/path/to/kubeconfig
```

### Jenkins Credentials

* github-credentials - GitHub access token

* docker-credentials - Docker registry credentials

* kubeconfig - Kubernetes configuration

* sonar-token - SonarQube access token

* snyk-token - Snyk API token

## Development

### Running Tests

```bash
./scripts/run-tests.sh
```

### Linting

```bash
./scripts/run-linting.sh
```

### Validation

```bash
./scripts/validate-pipeline-syntax.sh
```

## Contributing

Please read CONTRIBUTING.md for details on our code of conduct and the process for submitting pull requests.

## License

This project is licensed under the MIT License - see the LICENSE file for details.