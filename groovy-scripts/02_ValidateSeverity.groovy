import com.sap.gateway.ip.core.customdev.util.Message

/**
 * Script 2: Validate Severity
 * Description: Validates whether the incoming severity property matches the allowed values.
 * Throws an exception if invalid, routing execution immediately to the Exception Subprocess.
 */
def Message processData(Message message) {

    def severity = message.getProperty("severity")

    // Validate against allowed business severity levels
    if (!(severity in ["HIGH", "MEDIUM", "LOW"])) {
        throw new Exception("Invalid Severity. Allowed values are HIGH, MEDIUM and LOW.")
    }

    return message
}
