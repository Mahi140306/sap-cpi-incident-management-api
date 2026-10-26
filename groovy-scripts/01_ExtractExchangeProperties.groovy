import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper

/**
 * Script 1: Extract Exchange Properties
 * Description: Reads the incoming JSON request payload stream and extracts
 * essential fields (incidentId, customer, severity, description) into SAP CPI Exchange Properties.
 */
def Message processData(Message message) {

    // Read the incoming payload as a java.io.Reader (recommended for streaming performance in CPI)
    def reader = message.getBody(java.io.Reader)

    // Parse the JSON payload into a Groovy map object
    def json = new JsonSlurper().parse(reader)

    // Store attributes as Exchange Properties for routing & enrichment across the iFlow
    message.setProperty("incidentId", json.incidentId)
    message.setProperty("customer", json.customer)
    message.setProperty("severity", json.severity)
    message.setProperty("description", json.description)

    return message
}
