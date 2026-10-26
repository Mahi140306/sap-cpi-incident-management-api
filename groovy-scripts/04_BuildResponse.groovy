import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput

/**
 * Script 4: Build Success Response
 * Description: Constructs the standard acknowledgment JSON response returned to the calling client.
 */
def Message processData(Message message) {

    def response = [
        status      : "SUCCESS",
        message     : "Incident Created Successfully",
        incidentId  : message.getProperty("incidentId"),
        priority    : message.getProperty("priority"),
        assignedTeam: message.getProperty("assignedTeam"),
        sla         : message.getProperty("sla")
    ]

    message.setBody(JsonOutput.toJson(response))
    message.setHeader("CamelHttpResponseCode", 200)
    message.setHeader("Content-Type", "application/json")

    return message
}
