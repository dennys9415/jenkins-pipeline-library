# Contributing to Jenkins Pipeline Library

We love your input! We want to make contributing to this project as easy and transparent as possible.

## Development Process

1. Fork the repo and create your branch from `main`.
2. Make your changes following our coding standards.
3. Add tests for new functionality.
4. Ensure all tests pass.
5. Submit a pull request.

## Code Standards

### Groovy Standards

- Use 2-space indentation
- Follow Groovy style conventions
- Use descriptive variable and method names
- Include documentation for public methods
- Follow Jenkins Pipeline best practices

### Pipeline Standards

- Use declarative syntax when possible
- Include proper error handling
- Implement timeouts for long-running operations
- Use credentials management for secrets
- Include proper notifications

### Testing Standards

- Write unit tests for all new functionality
- Test both success and failure scenarios
- Mock external dependencies
- Maintain high test coverage

## Pull Request Process

1. Update documentation for new features
2. Add tests covering new functionality
3. Ensure CI/CD pipelines pass
4. Get review from maintainers
5. Merge after approval

## Commit Messages

Use conventional commit format:
- `feat`: new feature
- `fix`: bug fix
- `docs`: documentation
- `style`: formatting
- `refactor`: code restructuring
- `test`: adding tests
- `chore`: maintenance

Example: `feat: add Kubernetes deployment support`

## Security Guidelines

- Never commit secrets or credentials
- Use Jenkins credentials for sensitive data
- Follow least privilege principle
- Regular security scanning and updates

## Setting Up Development Environment

### Prerequisites

- Jenkins 2.277+
- Java 11+
- Groovy 3.0+
- Git

### Local Development

1. Clone the repository:
   ```bash
   git clone https://github.com/your-org/jenkins-pipeline-library.git
   cd jenkins-pipeline-library
```

2. Install development dependencies:

```bash
./scripts/setup-environment.sh
```

3. Run tests:

```bash
./scripts/run-tests.sh
```

4. Run linting:

```bash
./scripts/run-linting.sh
```

## Testing Your Changes

1. Create a test Jenkinsfile to verify your changes

2. Test with different project types (Java, Node.js, Python)

3. Verify security scanning works correctly

4. Test deployment scenarios

## Release Process

1. Update version in documentation

2. Run full test suite

3. Update CHANGELOG.md

4. Create release tag

5. Publish release notes

## Getting Help

* GitHub Issues for bug reports and feature requests

* Documentation for usage questions

* Slack channel for quick questions

* Email maintainers for sensitive issues

## Code of Conduct

Please note that this project is released with a Contributor Code of Conduct. By participating in this project you agree to abide by its terms.

## License

By contributing, you agree that your contributions will be licensed under the same MIT License that covers the project.