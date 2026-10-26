# SAP CPI Integration Flow (iFlow) Design

This document details the configuration and design of the **Incident Management API** in SAP Cloud Integration (SAP Integration Suite).

---

## 1. iFlow Components & Parameters

### Sender Channel (HTTPS)
* **Adapter Type:** HTTPS
* **Address:** `/incident/create`
* **Authorization:** User Role / Client Certificate (depending on BTP service key setup)
* **CSRF Protection:** Optional (disable for quick testing or fetch token before request)

---

## 2. Main Process Steps

### Step 1: Content Modifier / Groovy Script 1 (`01_ExtractExchangeProperties.groovy`)
* **Role:** Parses incoming JSON message body stream.
* **Outputs:** Sets Exchange Properties:
  * `incidentId = json.incidentId`
  * `customer = json.customer`
  * `severity = json.severity`
  * `description = json.description`

### Step 2: Severity Validation (`02_ValidateSeverity.groovy`)
* **Role:** Enforces API schema contract rules.
* **Validation:** Checks if `severity` is one of `['HIGH', 'MEDIUM', 'LOW']`.
* **Action:** Throws an Exception if invalid, causing the execution to jump straight into the **Exception Subprocess**.

### Step 3: Router
* **Expression Type:** Non-XML / Camel Simple Expression

| Branch | Routing Condition | Target Step |
|---|---|---|
| **HIGH** | `${property.severity} = 'HIGH'` | Content Modifier - High |
| **MEDIUM** | `${property.severity} = 'MEDIUM'` | Content Modifier - Medium |
| **LOW (Default)** | Default Route checked | Content Modifier - Low |

### Step 4: Content Modifiers on Router Branches

#### High Branch:
* Property `priority` = `P1`
* Property `assignedTeam` = `L1 Support`
* Property `sla` = `4 Hours`

#### Medium Branch:
* Property `priority` = `P2`
* Property `assignedTeam` = `L2 Support`
* Property `sla` = `8 Hours`

#### Low Branch:
* Property `priority` = `P3`
* Property `assignedTeam` = `Service Desk`
* Property `sla` = `24 Hours`

### Step 5: Payload Enrichment (`03_EnrichIncidentPayload.groovy`)
* Reads back properties: `${property.priority}`, `${property.assignedTeam}`, `${property.sla}`.
* Adds timestamp `processedAt` and sets status `OPEN`.

### Step 6: Response Builder (`04_BuildResponse.groovy`)
* Formulates confirmation JSON response with status `SUCCESS`.
* Sets header `CamelHttpResponseCode` to `200` and `Content-Type` to `application/json`.

---

## 3. Exception Subprocess Design

* **Trigger:** Intercepts any uncaught exceptions thrown during main processing.
* **Component:** Exception Subprocess containing an **Error Start Event**, followed by **Groovy Script 5** (`05_ExceptionSubprocessHandler.groovy`), and terminating at an **End Message Event**.
* **Logic:**
  1. Retrieves `${property.CamelExceptionCaught}`.
  2. Parses exception message and cleans Java stack prefixes.
  3. Detects validation vs system faults:
     * Validation Error -> Status `400 Bad Request`.
     * System / Technical Error -> Status `500 Internal Server Error`.
  4. Returns standardized error JSON to the client.
