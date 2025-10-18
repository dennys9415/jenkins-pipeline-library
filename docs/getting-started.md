# Getting Started with Jenkins Pipeline Library

## Overview

The Jenkins Pipeline Library provides reusable components and best practices for building CI/CD pipelines. It supports multiple languages, deployment platforms, and integrates security scanning and quality gates.

## Prerequisites

- Jenkins 2.277+ with Pipeline plugin
- Git for source control
- Required tools based on your stack (Java, Node.js, Python, Docker, etc.)

## Installation

### 1. Configure Jenkins Global Library

1. Go to **Jenkins > Manage Jenkins > Configure System**
2. Find **Global Pipeline Libraries**
3. Add a new library with:
   - Name: `jenkins-pipeline-library`
   - Default version: `main`
   - Retrieval method: **Modern SCM**
   - Source Code Management: **Git**
   - Project Repository: `https://github.com/your-org/jenkins-pipeline-library.git`

### 2. Configure Jenkins Credentials

Add the following credentials in **Jenkins > Manage Jenkins > Manage Credentials**:

- `github-credentials` - GitHub personal access token
- `docker-credentials` - Docker registry credentials  
- `kubeconfig` - Kubernetes configuration file
- `slack-token` - Slack API token
- `snyk-token` - Snyk API token

### 3. Install Required Plugins

Ensure these Jenkins plugins are installed:

- Pipeline
- Git
- Docker Pipeline
- Kubernetes
- SonarQube Scanner
- OWASP Dependency Check
- Slack Notification
- Email Extension
- JUnit
- JaCoCo
- Checkstyle
- PMD
- SpotBugs

## Quick Start

### Basic Usage

Create a `Jenkinsfile` in your project:

```groovy
@Library('jenkins-pipeline-library')_

build(
    language: 'java',
    buildTool: 'maven',
    jdkVersion: '11',
    sonarQube: true
)
```

Advanced Usage
groovy
@Library('jenkins-pipeline-library')_

def builder = new org.company.PipelineBuilder(this)

builder.forLanguage('java')
      .withBuildTool('maven')
      .withQualityGates(true)
      .withSecurityScan(true)
      .withNotifications(true)
      .build()()
Pipeline Components
Available Steps
build() - Build your application

test() - Run tests with coverage

securityScan() - Security vulnerability scanning

deploy() - Deployment to various environments

notify() - Send notifications

Supported Languages
Java (Maven, Gradle)

Node.js (npm, yarn)

Python (pip, venv)

.NET (dotnet)

Deployment Platforms
Kubernetes

Docker

OpenShift

Cloud Foundry

Configuration
Environment Variables
Set these environment variables in your Jenkins:

bash
# Java
JAVA_HOME=/path/to/java

# Node.js  
NODE_HOME=/path/to/node

# Docker
DOCKER_REGISTRY=your-registry

# Kubernetes
KUBECONFIG=/path/to/kubeconfig

# SonarQube
SONAR_HOST_URL=https://sonar.example.com
SONAR_AUTH_TOKEN=your-token

# Slack
SLACK_TEAM_DOMAIN=your-team
SLACK_CHANNEL=#builds
Quality Gates
Configure quality gates in your Jenkinsfile:

groovy
build(
    language: 'java',
    qualityGate: true,
    sonarQube: true,
    coverageThreshold: 80,
    duplicationThreshold: 5
)
Examples
Check the examples directory for complete pipeline examples:

Java Maven

Node.js

Python

Kubernetes

Troubleshooting
Common Issues
Library not found: Check global library configuration

Credentials not found: Verify credential IDs match

Tool not installed: Ensure required tools are installed on Jenkins agents

Permission denied: Check file permissions and service accounts

Debugging
Enable debug logging by adding to your Jenkinsfile:

groovy
@Library('jenkins-pipeline-library')_

node {
    // Enable debug logging
    wrap([$class: 'TimestamperBuildWrapper']) {
        // Your pipeline here
    }
}
Next Steps
Read the Pipeline Library Guide

Learn about Best Practices

Review Security Guidelines

Check Troubleshooting for common issues