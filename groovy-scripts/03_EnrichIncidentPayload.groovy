import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

/**
 * Script 3: Enrich Incident Payload
 * Description: Merges routing decisions (priority, assignedTeam, sla) from Exchange Properties
 * into the incident data, sets initial status to OPEN, timestamps the processing, and formats JSON output.
 */
def Message processData(Message message) {

    // Read the incoming JSON payload
    def reader = message.getBody(java.io.Reader)
    def json = new JsonSlurper().parse(reader)

    // Enrich payload with routing decisions stored in Exchange Properties
    json.priority = message.getProperty("priority")
    json.status = "OPEN"
    json.assignedTeam = message.getProperty("assignedTeam")
    json.sla = message.getProperty("sla")
    json.processedAt = new Date().format("yyyy-MM-dd HH:mm:ss")

    // Set updated pretty-printed JSON body and content headers
    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(json)))
    message.setHeader("Content-Type", "application/json")

    return message
}
