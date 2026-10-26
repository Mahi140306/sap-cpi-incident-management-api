import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput

/**
 * Script 5: Exception Subprocess Handler
 * Description: Intercepts pipeline exceptions caught by Apache Camel (CamelExceptionCaught),
 * cleans technical stack traces, dynamically sets HTTP status code (400 vs 500),
 * and structures a clean, production-ready JSON error payload.
 */
def Message processData(Message message) {

    Exception ex = message.getProperty("CamelExceptionCaught")

    int errorCode = 500
    String errorMessage = "Internal Server Error"

    if (ex != null) {
        errorMessage = ex.getMessage()

        // Clean out standard Java runtime wrappers
        errorMessage = errorMessage.replace("java.lang.Exception: ", "")
        errorMessage = errorMessage.replace("java.lang.IllegalArgumentException: ", "")

        // Remove memory references/hash codes if present
        if (errorMessage.contains("@")) {
            errorMessage = errorMessage.substring(0, errorMessage.indexOf("@")).trim()
        }

        // Map validation failure to HTTP 400 Bad Request
        if (ex instanceof IllegalArgumentException || errorMessage.contains("Invalid Severity")) {
            errorCode = 400
        }
    }

    def response = [
        status    : "FAILED",
        errorCode : errorCode,
        message   : errorMessage
    ]

    // Pretty print error response body
    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(response)))

    // Set HTTP status code and header
    message.setHeader("CamelHttpResponseCode", errorCode)
    message.setHeader("Content-Type", "application/json")

    return message
}
