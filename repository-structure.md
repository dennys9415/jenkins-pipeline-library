# Repository Structure

```
jenkins-pipeline-library/
├── .github/ # GitHub Actions workflows
│       └── workflows/
│               ├── ci-validation.yml # CI validation pipeline
│               ├── security-scan.yml # Security scanning
│               └── release.yml # Release automation
├── vars/ # Shared pipeline steps
│    ├── build.groovy # Build steps for all languages
│    ├── test.groovy # Testing and coverage
│    ├── deploy.groovy # Deployment orchestration
│    ├── securityScan.groovy # Security vulnerability scanning
│    ├── notify.groovy # Notification management
│    ├── kubernetes.groovy # Kubernetes operations
│    ├── docker.groovy # Docker build and push
│    └── sonarQube.groovy # SonarQube integration
├── src/ # Groovy source classes
│   └── org/
│        └── company/
│               ├── PipelineBuilder.groovy # Fluent pipeline API
│               ├── QualityGate.groovy # Quality gate management
│               ├── SecurityScanner.groovy # Security scanning logic
│               ├── DeploymentManager.groovy # Deployment orchestration
│               └── NotificationManager.groovy # Unified notifications
├── resources/ # Resource files and templates
│       ├── pipeline-templates/ # Ready-to-use pipeline templates
│       │       ├── maven-pipeline.jenkinsfile
│       │       ├── nodejs-pipeline.jenkinsfile
│       │       ├── python-pipeline.jenkinsfile
│       │       ├── dotnet-pipeline.jenkinsfile
│       │       ├── multibranch-pipeline.jenkinsfile
│       │       └── kubernetes-pipeline.jenkinsfile
│       ├── scripts/ # Utility scripts
│       │       ├── setup-environment.sh
│       │       ├── code-quality.sh
│       │       ├── security-scan.sh
│       │       └── deploy-kubernetes.sh
│       └── config/ # Configuration templates
│              ├── checkstyle-config.xml
│              ├── pmd-ruleset.xml
│              └── sonar-project.properties
├── test/ # Test suites
│   ├── vars/ # Tests for shared steps
│   │   ├── BuildTest.groovy
│   │   ├── TestTest.groovy
│   │   └── DeployTest.groovy
│   └── src/ # Tests for source classes
│        └── org/
│             └── company/
│                 ├── PipelineBuilderTest.groovy
│                 ├── QualityGateTest.groovy
│                 └── SecurityScannerTest.groovy
├── examples/ # Complete pipeline examples
│       ├── java-maven/
│       │       └── Jenkinsfile
│       ├── nodejs/
│       │       └── Jenkinsfile
│       ├── python/
│       │       └── Jenkinsfile
│       └── kubernetes/
│               └── Jenkinsfile
├── docs/ # Documentation
│     ├── getting-started.md
│     ├── pipeline-library-guide.md
│     ├── best-practices.md
│     ├── security-guide.md
│     └── troubleshooting.md
├── scripts/ # Development scripts
│       ├── run-tests.sh
│       ├── run-linting.sh
│       ├── validate-pipeline-syntax.sh
│       └── validate-structure.sh
├── .gitignore
├── Jenkinsfile # CI/CD for the library itself
├── README.md
├── CONTRIBUTING.md
├── LICENSE
└── repository-structure.md
```

## Key Components

### Shared Steps (`vars/`)

- Reusable pipeline steps that can be called from Jenkinsfiles
- Support multiple languages and platforms
- Follow Jenkins Pipeline best practices

### Source Classes (`src/`)

- Business logic and utilities
- Fluent APIs for complex operations
- Object-oriented design for maintainability

### Templates (`resources/`)

- Ready-to-use pipeline templates
- Configuration files for various tools
- Utility scripts for common tasks

### Tests (`test/`)

- Comprehensive test coverage
- Unit tests for all components
- Integration tests for complex scenarios

### Examples (`examples/`)

- Complete working examples
- Different languages and platforms
- Best practice implementations

### Documentation (`docs/`)

- Comprehensive user guide
- Best practices and patterns
- Troubleshooting and reference