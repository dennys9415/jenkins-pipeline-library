@Library('jenkins-pipeline-library')_

// CI/CD Pipeline for Jenkins Pipeline Library itself
def builder = new org.company.PipelineBuilder(this)

builder.forLanguage('groovy')
      .withQualityGates(true)
      .withSecurityScan(true)
      .withNotifications(true)
      .withParameters([
          RUN_TESTS: [
              type: 'boolean',
              defaultValue: true,
              description: 'Run tests'
          ],
          SKIP_LINT: [
              type: 'boolean',
              defaultValue: false,
              description: 'Skip linting'
          ]
      ])
      .addStage('Validate Structure') {
          echo "Validating library structure"
          sh './scripts/validate-structure.sh'
      }
      .addStage('Lint Code') {
          when {
              expression { !params.SKIP_LINT }
          }
          steps {
              echo "Linting Groovy code"
              sh './scripts/run-linting.sh'
          }
      }
      .addStage('Run Tests') {
          when {
              expression { params.RUN_TESTS }
          }
          steps {
              echo "Running tests"
              sh './scripts/run-tests.sh'
          }
      }
      .addStage('Security Scan') {
          echo "Running security scan"
          securityScan(
              language: 'groovy',
              scanType: 'dependency',
              failOnVulnerabilities: true
          )
      }
      .addStage('Package Library') {
          echo "Packaging library for distribution"
          sh '''
            zip -r jenkins-pipeline-library.zip \
                vars/ \
                src/ \
                resources/ \
                docs/ \
                README.md \
                LICENSE
          '''
          archiveArtifacts artifacts: 'jenkins-pipeline-library.zip'
      }
      .build()()