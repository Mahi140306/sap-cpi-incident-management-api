# Real-Time Incident Management API | SAP CPI / SAP Integration Suite

[![SAP CPI](https://img.shields.io/badge/SAP-Cloud%20Integration-0070F2?logo=sap&logoColor=white)](#)
[![Groovy](https://img.shields.io/badge/Language-Groovy-4298B8?logo=apachegroovy&logoColor=white)](#)
[![REST API](https://img.shields.io/badge/API-REST%20%2F%20JSON-brightgreen)](#)

An enterprise-ready **Real-Time Incident Management REST API** built on **SAP Cloud Integration (CPI) / SAP Integration Suite** (using SAP BTP Trial). 

This project simulates a production-grade enterprise scenario where incident tickets submitted through an external portal are ingested, validated, prioritized, enriched, and routed dynamically, complete with robust **Exception Subprocess** and custom REST error handling.

---

## 📌 Table of Contents
- [Architecture & Workflow](#-architecture--workflow)
- [Business Scenario](#-business-scenario)
- [Key Features](#-key-features)
- [Repository Structure](#-repository-structure)
- [Integration Flow (iFlow) Design](#-integration-flow-iflow-design)
- [Groovy Scripts Overview](#-groovy-scripts-overview)
- [Sample Payloads & HTTP Codes](#-sample-payloads--http-codes)
- [Postman Testing Guide](#-postman-testing-guide)
- [Setup & Deployment](#-setup--deployment)

---

## 🏗 Architecture & Workflow

```
[Postman / Web Portal]
          │
          │ HTTP POST (JSON Payload)
          ▼
 [HTTPS Sender Adapter] (/http/incident/create)
          │
          ▼
[01_ExtractExchangeProperties.groovy] ───► Extract fields into Exchange Properties
          │
          ▼
[02_ValidateSeverity.groovy] ────────────► Validate (HIGH | MEDIUM | LOW)
          │
          ├─── [Validation Fails] ───────┐
          │                              ▼
          ▼ [Valid]           [Exception Subprocess]
     [Router]                            │
    /    │   \                           ▼
 HIGH  MEDIUM LOW            [05_ExceptionSubprocessHandler.groovy]
  │      │     │                         │
[CM1]  [CM2] [CM3]                       │ Format clean JSON error &
(P1)   (P2)  (P3)                        │ set HTTP 400 / 500 status code
  \      │     /                         ▼
          ▼                     [Return Error Response]
[03_EnrichIncidentPayload.groovy]
          │
          ▼
[04_BuildResponse.groovy]
          │
          ▼
[Return HTTP 200 Success Response]
```

---

## 💼 Business Scenario

In enterprise environments, users and downstream systems report technical, operational, or infrastructure defects through an **Incident Management Portal**:
* **Examples of Incidents:** Order Processing Failed, SAP S/4HANA Login Latency, Machine Breakdown, Payment Gateway Timeout, Network Drop.
* The portal posts the incident ticket payload as JSON to SAP CPI.
* **SAP CPI** acts as the middleware router:
  1. Validates incoming payload rules.
  2. Dynamically calculates SLA, Priority, and Assigned Support Team based on severity.
  3. Enriches the incident record with audit timestamps and status `OPEN`.
  4. Returns an instant confirmation payload with an HTTP 200 status code.
  5. If validation fails or system exceptions occur, catches errors in an **Exception Subprocess** and returns standard REST error responses (e.g., HTTP 400 Bad Request).

---

## ✨ Key Features

- **Decoupled Architecture:** Clean separation of validation, business routing, payload transformation, and exception handling.
- **Dynamic Routing:** Router conditions evaluate `${property.severity}` using Apache Camel Simple expression language.
- **Streaming JSON Slurper:** High-performance JSON reading using `java.io.Reader` streams to prevent high memory consumption in production runtime.
- **Robust Exception Subprocess:** Intercepts runtime exceptions using `CamelExceptionCaught`, sanitizes Java stack traces, and prevents technical leaking.
- **Standard REST Status Codes:** Uses `CamelHttpResponseCode` to dynamically set HTTP 200, 400, or 500 status codes.

---

## 📂 Repository Structure

```
.
├── docs/
│   └── iflow-design.md             # Detailed canvas configuration and step settings
├── groovy-scripts/
│   ├── 01_ExtractExchangeProperties.groovy  # JSON parsing & property extraction
│   ├── 02_ValidateSeverity.groovy          # Business rule validation & exception throwing
│   ├── 03_EnrichIncidentPayload.groovy      # Adds SLA, Priority, Team, Timestamp
│   ├── 04_BuildResponse.groovy             # Success response builder
│   └── 05_ExceptionSubprocessHandler.groovy # Centralized exception error handler
├── postman/
│   └── SAP_CPI_Incident_Management_API.postman_collection.json # Ready-to-import test suite
├── sample-payloads/
│   ├── request-valid-high.json             # Valid HIGH severity test payload
│   ├── request-valid-medium.json           # Valid MEDIUM severity test payload
│   ├── request-valid-low.json              # Valid LOW severity test payload
│   ├── request-invalid-severity.json       # Negative test payload (CRITICAL)
│   ├── response-success.json               # Expected HTTP 200 JSON
│   ├── response-error-400.json             # Expected HTTP 400 JSON
│   └── response-error-500.json             # Expected HTTP 500 JSON
├── .gitignore
└── README.md
```

---

## ⚙ Integration Flow (iFlow) Design

### Router Decision Matrix

| Branch | Expression Condition | Priority | SLA | Assigned Team |
|---|---|---|---|---|
| **HIGH** | `${property.severity} = 'HIGH'` | `P1` | `4 Hours` | `L1 Support` |
| **MEDIUM** | `${property.severity} = 'MEDIUM'` | `P2` | `8 Hours` | `L2 Support` |
| **LOW** | *(Default Route)* | `P3` | `24 Hours` | `Service Desk` |

---

## 📜 Groovy Scripts Overview

### 1. Extract Exchange Properties (`01_ExtractExchangeProperties.groovy`)
Parses the incoming JSON stream and saves fields as Exchange Properties:
```groovy
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper

def Message processData(Message message) {
    def reader = message.getBody(java.io.Reader)
    def json = new JsonSlurper().parse(reader)

    message.setProperty("incidentId", json.incidentId)
    message.setProperty("customer", json.customer)
    message.setProperty("severity", json.severity)
    message.setProperty("description", json.description)

    return message
}
```

### 2. Validation (`02_ValidateSeverity.groovy`)
Guards the business logic against invalid severities:
```groovy
import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
    def severity = message.getProperty("severity")

    if (!(severity in ["HIGH", "MEDIUM", "LOW"])) {
        throw new Exception("Invalid Severity. Allowed values are HIGH, MEDIUM and LOW.")
    }
    return message
}
```

### 3. Exception Subprocess Handler (`05_ExceptionSubprocessHandler.groovy`)
Captures `CamelExceptionCaught`, maps status codes, and formats standard REST errors:
```groovy
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput

def Message processData(Message message) {
    Exception ex = message.getProperty("CamelExceptionCaught")
    int errorCode = 500
    String errorMessage = "Internal Server Error"

    if (ex != null) {
        errorMessage = ex.getMessage()
        errorMessage = errorMessage.replace("java.lang.Exception: ", "")

        if (ex instanceof IllegalArgumentException || errorMessage.contains("Invalid Severity")) {
            errorCode = 400
        }
    }

    def response = [status: "FAILED", errorCode: errorCode, message: errorMessage]
    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(response)))
    message.setHeader("CamelHttpResponseCode", errorCode)
    message.setHeader("Content-Type", "application/json")

    return message
}
```

---

## 📬 Sample Payloads & HTTP Codes

### 🟢 Success Request (HTTP POST)
```json
{
   "incidentId": "INC1001",
   "customer": "ABC Corp",
   "severity": "HIGH",
   "description": "Order Processing Failed"
}
```
**Response (HTTP 200 OK):**
```json
{
   "status": "SUCCESS",
   "message": "Incident Created Successfully",
   "incidentId": "INC1001",
   "priority": "P1",
   "assignedTeam": "L1 Support",
   "sla": "4 Hours"
}
```

### 🔴 Failure Request (Invalid Severity)
```json
{
   "incidentId": "INC1004",
   "customer": "Demo Corp",
   "severity": "CRITICAL",
   "description": "Invalid severity input test"
}
```
**Response (HTTP 400 Bad Request):**
```json
{
  "status": "FAILED",
  "errorCode": 400,
  "message": "Invalid Severity. Allowed values are HIGH, MEDIUM and LOW."
}
```

---

## 🧪 Postman Testing Guide

1. Open Postman and click **Import**.
2. Select `postman/SAP_CPI_Incident_Management_API.postman_collection.json`.
3. Configure the collection variables:
   * `cpi_base_url`: Your SAP BTP runtime URL (`https://<tenant-rt>.cfapps.<region>.hana.ondemand.com`).
   * `endpoint_path`: `/http/incident/create`
4. Set Authorization to **Basic Auth** or **OAuth 2.0** using your BTP Process Integration runtime service key.
5. Run the requests to verify both happy paths and negative tests.

---

## 🚀 Setup & Deployment in SAP Integration Suite

1. **Log in to SAP BTP Cockpit** and launch **SAP Integration Suite**.
2. Navigate to **Design > Integrations and APIs**.
3. Create a new Integration Package or open an existing package.
4. Add a new **Integration Flow** named `Incident_Management_API`.
5. Recreate the flow components following [docs/iflow-design.md](docs/iflow-design.md).
6. Upload the scripts from the `groovy-scripts/` directory to the iFlow's **Resources** tab.
7. Save, Deploy, and Monitor execution via **Operations and Monitoring > Manage Message Processing**.
