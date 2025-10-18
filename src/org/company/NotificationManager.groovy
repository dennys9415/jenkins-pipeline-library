package org.company

/**
 * NotificationManager - Unified notification management
 */
class NotificationManager implements Serializable {
    private final steps
    
    NotificationManager(steps) {
        this.steps = steps
    }
    
    def notifyBuildStart(Map params = [:]) {
        def config = [
            jobName: params.jobName ?: "${env.JOB_NAME}",
            buildNumber: params.buildNumber ?: "${env.BUILD_NUMBER}",
            branch: params.branch ?: "${env.BRANCH_NAME}",
            commit: params.commit ?: "${env.GIT_COMMIT}",
            triggeredBy: params.triggeredBy ?: "${env.BUILD_USER_ID}",
            channels: params.channels ?: ['slack', 'email']
        ]
        
        def message = """
🚀 Build Started
Job: ${config.jobName}
Build: #${config.buildNumber}
Branch: ${config.branch}
Commit: ${config.commit.take(8)}
Triggered by: ${config.triggeredBy}
        """.stripIndent().trim()
        
        sendNotifications(config.channels, message, 'started')
    }
    
    def notifyBuildSuccess(Map params = [:]) {
        def config = [
            jobName: params.jobName ?: "${env.JOB_NAME}",
            buildNumber: params.buildNumber ?: "${env.BUILD_NUMBER}",
            buildUrl: params.buildUrl ?: "${env.BUILD_URL}",
            duration: params.duration ?: currentBuild.durationString,
            channels: params.channels ?: ['slack', 'email']
        ]
        
        def message = """
✅ Build Successful
Job: ${config.jobName}
Build: #${config.buildNumber}
Duration: ${config.duration}
Details: ${config.buildUrl}
        """.stripIndent().trim()
        
        sendNotifications(config.channels, message, 'success')
    }
    
    def notifyBuildFailure(Map params = [:]) {
        def config = [
            jobName: params.jobName ?: "${env.JOB_NAME}",
            buildNumber: params.buildNumber ?: "${env.BUILD_NUMBER}",
            buildUrl: params.buildUrl ?: "${env.BUILD_URL}",
            error: params.error ?: 'Unknown error',
            channels: params.channels ?: ['slack', 'email']
        ]
        
        def message = """
❌ Build Failed
Job: ${config.jobName}
Build: #${config.buildNumber}
Error: ${config.error}
Details: ${config.buildUrl}
        """.stripIndent().trim()
        
        sendNotifications(config.channels, message, 'failure')
    }
    
    def notifyDeployment(Map params = [:]) {
        def config = [
            environment: params.environment ?: 'unknown',
            jobName: params.jobName ?: "${env.JOB_NAME}",
            buildNumber: params.buildNumber ?: "${env.BUILD_NUMBER}",
            version: params.version ?: "${env.BUILD_NUMBER}",
            status: params.status ?: 'unknown',
            channels: params.channels ?: ['slack', 'email']
        ]
        
        def emoji = getStatusEmoji(config.status)
        
        def message = """
${emoji} Deployment ${config.status.toUpperCase()}
Environment: ${config.environment}
Application: ${config.jobName}
Version: ${config.version}
Build: #${config.buildNumber}
        """.stripIndent().trim()
        
        sendNotifications(config.channels, message, config.status)
    }
    
    def notifyQualityGate(Map params = [:]) {
        def config = [
            status: params.status ?: 'unknown',
            project: params.project ?: "${env.JOB_NAME}",
            url: params.url ?: '',
            metrics: params.metrics ?: [:],
            channels: params.channels ?: ['slack']
        ]
        
        def emoji = getStatusEmoji(config.status)
        
        def message = """
${emoji} Quality Gate ${config.status.toUpperCase()}
Project: ${config.project}
Status: ${config.status}
Details: ${config.url}
        """.stripIndent().trim()
        
        // Add metrics if available
        if (config.metrics) {
            message += "\nMetrics:"
            config.metrics.each { key, value ->
                message += "\n- ${key}: ${value}"
            }
        }
        
        sendNotifications(config.channels, message, config.status)
    }
    
    def sendNotifications(List channels, String message, String status) {
        channels.each { channel ->
            try {
                switch(channel) {
                    case 'slack':
                        sendSlackNotification(message, status)
                        break
                    case 'email':
                        sendEmailNotification(message, status)
                        break
                    case 'teams':
                        sendTeamsNotification(message, status)
                        break
                    case 'webhook':
                        sendWebhookNotification(message, status)
                        break
                    default:
                        steps.echo "Unknown notification channel: ${channel}"
                }
            } catch (Exception e) {
                steps.echo "Failed to send notification via ${channel}: ${e.message}"
            }
        }
    }
    
    def sendSlackNotification(String message, String status) {
        def color = getStatusColor(status)
        
        steps.slackSend(
            channel: "${env.SLACK_CHANNEL ?: '#general'}",
            color: color,
            message: message,
            teamDomain: "${env.SLACK_TEAM_DOMAIN}",
            tokenCredentialId: 'slack-token'
        )
    }
    
    def sendEmailNotification(String message, String status) {
        def subject = "[${status.toUpperCase()}] ${env.JOB_NAME} Build #${env.BUILD_NUMBER}"
        
        steps.emailext (
            subject: subject,
            body: """
                <html>
                <body>
                    <h2>${subject}</h2>
                    <pre>${message}</pre>
                    <p>Build URL: <a href="${env.BUILD_URL}">${env.BUILD_URL}</a></p>
                    <p>Timestamp: ${new Date()}</p>
                </body>
                </html>
            """,
            to: "${env.DEFAULT_RECIPIENTS}",
            recipientProviders: [[$class: 'DevelopersRecipientProvider']]
        )
    }
    
    def sendTeamsNotification(String message, String status) {
        steps.office365ConnectorSend(
            message: message,
            status: status,
            webhookUrl: "${env.TEAMS_WEBHOOK_URL}",
            color: getStatusColor(status)
        )
    }
    
    def sendWebhookNotification(String message, String status) {
        def payload = [
            job_name: env.JOB_NAME,
            build_number: env.BUILD_NUMBER,
            build_url: env.BUILD_URL,
            status: status,
            message: message,
            timestamp: new Date().toString()
        ]
        
        steps.sh """
            curl -X POST \
              -H 'Content-Type: application/json' \
              -d '${steps.writeJSON returnText: true, json: payload}' \
              ${env.WEBHOOK_URL} || true
        """
    }
    
    def getStatusColor(String status) {
        switch(status.toLowerCase()) {
            case 'success':
            case 'completed':
                return 'good'
            case 'failure':
            case 'failed':
                return 'danger'
            case 'started':
            case 'running':
                return '#439FE0'
            case 'unstable':
            case 'warning':
                return 'warning'
            default:
                return '#439FE0'
        }
    }
    
    def getStatusEmoji(String status) {
        switch(status.toLowerCase()) {
            case 'success':
            case 'completed':
                return '✅'
            case 'failure':
            case 'failed':
                return '❌'
            case 'started':
            case 'running':
                return '🚀'
            case 'unstable':
            case 'warning':
                return '⚠️'
            default:
                return 'ℹ️'
        }
    }
}