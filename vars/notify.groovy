#!/usr/bin/groovy

/**
 * notify.groovy - Shared notification pipeline steps
 */

def call(Map params = [:]) {
    def config = [
        type: params.type ?: 'default',
        channel: params.channel ?: '#general',
        recipients: params.recipients ?: '',
        message: params.message ?: '',
        status: params.status ?: 'unknown',
        buildUrl: params.buildUrl ?: "${env.BUILD_URL}",
        buildNumber: params.buildNumber ?: "${env.BUILD_NUMBER}",
        jobName: params.jobName ?: "${env.JOB_NAME}"
    ]
    
    script {
        switch(config.type) {
            case 'slack':
                sendSlackNotification(config)
                break
            case 'email':
                sendEmailNotification(config)
                break
            case 'teams':
                sendTeamsNotification(config)
                break
            case 'webhook':
                sendWebhookNotification(config)
                break
            default:
                sendDefaultNotification(config)
        }
    }
}

def sendSlackNotification(Map config) {
    echo "Sending Slack notification to ${config.channel}"
    
    def color = getStatusColor(config.status)
    def message = config.message ?: getDefaultMessage(config)
    
    slackSend(
        channel: config.channel,
        color: color,
        message: message,
        teamDomain: "${env.SLACK_TEAM_DOMAIN}",
        tokenCredentialId: 'slack-token'
    )
}

def sendEmailNotification(Map config) {
    echo "Sending email notification to ${config.recipients}"
    
    def subject = getEmailSubject(config)
    def body = getEmailBody(config)
    
    emailext (
        subject: subject,
        body: body,
        to: config.recipients ?: "${env.DEFAULT_RECIPIENTS}",
        recipientProviders: [[$class: 'DevelopersRecipientProvider']]
    )
}

def sendTeamsNotification(Map config) {
    echo "Sending Microsoft Teams notification"
    
    def message = config.message ?: getDefaultMessage(config)
    def color = getStatusColor(config.status)
    
    office365ConnectorSend(
        message: message,
        status: config.status,
        webhookUrl: "${env.TEAMS_WEBHOOK_URL}",
        color: color
    )
}

def sendWebhookNotification(Map config) {
    echo "Sending webhook notification"
    
    def payload = [
        job_name: config.jobName,
        build_number: config.buildNumber,
        build_url: config.buildUrl,
        status: config.status,
        message: config.message
    ]
    
    sh """
        curl -X POST \
          -H 'Content-Type: application/json' \
          -d '${writeJSON returnText: true, json: payload}' \
          ${env.WEBHOOK_URL}
    """
}

def sendDefaultNotification(Map config) {
    echo "Sending default notification"
    
    def message = config.message ?: getDefaultMessage(config)
    
    echo "NOTIFICATION: ${message}"
}

def getStatusColor(String status) {
    switch(status.toLowerCase()) {
        case 'success':
            return 'good'
        case 'failure':
            return 'danger'
        case 'unstable':
            return 'warning'
        case 'aborted':
            return 'warning'
        default:
            return '#439FE0'
    }
}

def getDefaultMessage(Map config) {
    def statusEmoji = getStatusEmoji(config.status)
    
    return """
        ${statusEmoji} Build ${config.status}: ${config.jobName} #${config.buildNumber}
        📊 Build URL: ${config.buildUrl}
        🕐 Timestamp: ${new Date()}
    """.stripIndent().trim()
}

def getStatusEmoji(String status) {
    switch(status.toLowerCase()) {
        case 'success':
            return '✅'
        case 'failure':
            return '❌'
        case 'unstable':
            return '⚠️'
        case 'aborted':
            return '🛑'
        default:
            return 'ℹ️'
    }
}

def getEmailSubject(Map config) {
    return "${config.status.toUpperCase()}: ${config.jobName} Build #${config.buildNumber}"
}

def getEmailBody(Map config) {
    def message = config.message ?: "Build ${config.status} for ${config.jobName}"
    
    return """
        <html>
        <body>
            <h2>Build ${config.status}</h2>
            <p><strong>Job:</strong> ${config.jobName}</p>
            <p><strong>Build Number:</strong> ${config.buildNumber}</p>
            <p><strong>Status:</strong> ${config.status}</p>
            <p><strong>Message:</strong> ${message}</p>
            <p><strong>Build URL:</strong> <a href="${config.buildUrl}">${config.buildUrl}</a></p>
            <p><strong>Timestamp:</strong> ${new Date()}</p>
        </body>
        </html>
    """
}

// Convenience methods for common notification types
def success(Map params = [:]) {
    params.status = 'success'
    call(params)
}

def failure(Map params = [:]) {
    params.status = 'failure'
    call(params)
}

def unstable(Map params = [:]) {
    params.status = 'unstable'
    call(params)
}

def started(Map params = [:]) {
    params.status = 'started'
    call(params)
}

return this