# ThingsBoard Extension Starter

A starter project for building custom business logic on top of ThingsBoard. Your extension runs as a standalone Spring Boot service that receives HTTP callbacks from ThingsBoard rule chains and optionally calls ThingsBoard APIs back.

**The experience for vibe-coders:** open this project in [Claude Code](https://claude.com/claude-code), describe what you want in plain language, and Claude generates everything — controller code, POJOs, and rule chain wiring instructions.

## Prerequisites

One of:
- **Docker** (recommended) — no Java or Maven needed
- **Java 25+** — Maven is included via `./mvnw`

And:
- A running ThingsBoard instance (default: `http://localhost:8080`)
- A ThingsBoard API key (see [Creating an API Key](#creating-an-api-key) below)

## Quick Start

```bash
# 1. Clone or copy this project
cd thingsboard-extension-starter

# 2. (Optional) Set your ThingsBoard URL in src/main/resources/application.yml
#    Default: http://localhost:8080

# 3. Run with Java
./run.sh

# Or run with Docker
./run-docker.sh

# 4. Test it
curl -X POST http://localhost:8090/api/usage/on-telemetry \
  -H 'Content-Type: application/json' \
  -H 'X-TB-API-Key: YOUR_API_KEY' \
  -d '{"temperature": 25.5, "humidity": 60}'
```

Response:
```json
{"status":"ok","keysReceived":2,"keys":["temperature","humidity"]}
```

### Alternative ways to run

```bash
# With Maven directly (requires Java 25)
./mvnw spring-boot:run

# With Docker Compose
./mvnw package -DskipTests -q && docker compose up --build

# Health check
curl http://localhost:8090/api/health
```

## How It Works

```
ThingsBoard Rule Chain                       Extension Service (port 8090)
┌──────────────────────────┐                ┌──────────────────────────────┐
│                          │                │                              │
│ [Event] ──> [REST API    │   POST + JSON  │  @RestController endpoint    │
│              Call node]  ─┼───────────────>│  (optionally uses            │
│             + X-TB-API-Key│<───────────────┤   ThingsboardClient)        │
│                          │   JSON response │                              │
└──────────────────────────┘                └──────────────────────────────┘
```

**The API key travels with the request:**
1. You configure a ThingsBoard REST API Call node to send the `X-TB-API-Key` header
2. Your extension reads that header and creates a `ThingsboardClient` authenticated with that API key
3. The controller uses the client to call ThingsBoard APIs (get devices, save attributes, etc.)
4. Multi-tenancy for free — different tenants send different API keys

**Request/response is plain JSON:**
- **Input**: `msg.getData()` from the rule chain — whatever JSON the triggering event carries
- **Output**: any JSON you return — becomes the outgoing message in the rule chain
- **2xx response** = Success route in rule chain
- **non-2xx response** = Failure route in rule chain

## Example 1: Billing on Device Creation

**Business need:** When a new device is created, mark it as billing-active by saving a server-side attribute.

### Controller code

`src/main/java/org/thingsboard/extension/examples/BillingController.java`:

```java
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    @PostMapping("/on-device-created")
    public Map<String, Object> onDeviceCreated(@RequestBody JsonNode device,
                                               ThingsboardClient tb) throws Exception {
        String deviceId = device.get("id").get("id").asText();
        String deviceName = device.get("name").asText();

        // Save a server-side attribute marking billing activation
        String billingJson = """
                {"billingActive": true, "billingStartedAt": "%s"}
                """.formatted(Instant.now().toString());

        tb.saveDeviceAttributes(deviceId, "SERVER_SCOPE", billingJson);

        return Map.of(
                "status", "ok",
                "deviceId", deviceId,
                "deviceName", deviceName,
                "billingStartedAt", Instant.now().toString()
        );
    }
}
```

**How it works line by line:**

1. `@RequestBody JsonNode device` — Spring deserializes the incoming JSON (the device data from the rule chain) into a Jackson `JsonNode`.
2. `ThingsboardClient tb` — auto-resolved from the `X-TB-API-Key` header. The `ThingsboardClientProvider` reads the header, creates (or returns a cached) client authenticated with that API key.
3. `device.get("id").get("id").asText()` — extracts the device UUID from the ThingsBoard entity ID structure `{"entityType": "DEVICE", "id": "uuid"}`.
4. `tb.saveDeviceAttributes(...)` — calls the ThingsBoard REST API to save server-side attributes on the device.
5. Returns a JSON response — this becomes the outgoing message on the **Success** route of the REST API Call node.

### Rule chain setup

1. Open **ThingsBoard UI → Rule Chains** → edit your rule chain (or create a new one)
2. In the left panel, find **Action → REST API Call** and drag it onto the canvas
3. Configure the node:
   - **Name**: `Billing: on device created`
   - **Method**: `POST`
   - **URL**: `http://localhost:8090/api/billing/on-device-created`
   - **Headers**:
     - `Content-Type`: `application/json`
     - `X-TB-API-Key`: `YOUR_API_KEY`
   - **Credentials**: `Anonymous`
4. Connect the **Device Created** message type to this node:
   - From the root **Message Type Switch** node, draw a connection labeled `Entity Created` to your REST API Call node
5. Save the rule chain

### Testing

1. Start the extension: `./mvnw spring-boot:run`
2. Create a device in ThingsBoard (UI or API)
3. Open the device → **Attributes** tab → **Server attributes**
4. Verify `billingActive: true` and `billingStartedAt` appear

## Example 2: Usage Tracking on Telemetry

**Business need:** Count how many telemetry keys each message contains (for usage metering, logging, etc.).

### Controller code

`src/main/java/org/thingsboard/extension/examples/UsageTrackingController.java`:

```java
@RestController
@RequestMapping("/api/usage")
public class UsageTrackingController {

    @PostMapping("/on-telemetry")
    public Map<String, Object> onTelemetry(@RequestBody JsonNode telemetry) {
        int keyCount = telemetry.size();

        return Map.of(
                "status", "ok",
                "keysReceived", keyCount,
                "keys", iterableToList(telemetry.fieldNames())
        );
    }
}
```

**Note:** This controller does NOT declare a `ThingsboardClient` parameter — it simply processes the incoming JSON without calling ThingsBoard APIs. Just omit the parameter when you don't need it.

### Rule chain setup

1. In your rule chain, add a **REST API Call** node:
   - **Method**: `POST`
   - **URL**: `http://localhost:8090/api/usage/on-telemetry`
   - **Headers**: `Content-Type: application/json`, `X-TB-API-Key: YOUR_API_KEY`
   - **Credentials**: `Anonymous`
2. From the **Message Type Switch** node, connect **Post telemetry** to this node
3. Save the rule chain

### Testing

```bash
# Simulate what the rule chain sends
curl -X POST http://localhost:8090/api/usage/on-telemetry \
  -H 'Content-Type: application/json' \
  -H 'X-TB-API-Key: any-key-here' \
  -d '{"temperature": 25.5, "humidity": 60, "pressure": 1013.25}'
```

Response:
```json
{"status":"ok","keysReceived":3,"keys":["temperature","humidity","pressure"]}
```

## Creating Your Own Extension

### Option A: Use Claude Code (recommended)

Open this project in Claude Code and describe what you want:

> "I want to send a Slack notification when a critical alarm is created"

Claude will:
1. Ask clarifying questions (or you provide details upfront)
2. Generate the controller class
3. Add any needed dependencies to `pom.xml`
4. Tell you exactly how to wire the rule chain

### Option B: Manual

1. Create a new `@RestController` class in `src/main/java/org/thingsboard/extension/`
2. Add a `@PostMapping` method that takes `@RequestBody JsonNode` (or a custom POJO)
3. If you need ThingsBoard APIs, add `ThingsboardClient tb` as a parameter
4. Return any object — Spring serializes it to JSON
5. Wire a REST API Call node in ThingsBoard pointing to your endpoint

### Hot Reload (development)

The project includes `spring-boot-devtools`. When running with `./mvnw spring-boot:run`:
1. Make your code changes
2. Run `./mvnw compile -q` in a separate terminal
3. The service auto-restarts in ~2 seconds

## Configuration Reference

### `application.yml`

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8090` | Port for the extension service |
| `thingsboard.url` | `http://localhost:8080` | ThingsBoard base URL |
| `thingsboard.client.cache-ttl` | `60` | Client cache TTL in minutes |
| `thingsboard.client.cache-max-size` | `100` | Max cached ThingsBoard clients |

Request/response logging is controlled by the logback level for `org.thingsboard.extension` (DEBUG = on, INFO = off). See `src/main/resources/logback.xml`.

### Environment variables (Docker)

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8090` | Port mapping |
| `THINGSBOARD_URL` | `http://host.docker.internal:8080` | ThingsBoard URL from container |
| `JAVA_OPTS` | _(empty)_ | JVM options |

### Headers

| Header | Required | Description |
|--------|----------|-------------|
| `Content-Type` | Yes | Must be `application/json` |
| `X-TB-API-Key` | Yes* | ThingsBoard API key. *Required if controller declares `ThingsboardClient` parameter. |

### Creating an API Key

1. Log in to ThingsBoard as **Tenant Administrator**
2. Go to **API Keys** section in the left menu
3. Click the **+** button to create a new API key
4. Copy the key value — this is what goes in the `X-TB-API-Key` header

## Rule Chain Patterns

### Common event types and how to wire them

| Event | Message Type in Rule Chain | Typical Use Case |
|-------|---------------------------|------------------|
| Device created | `Entity Created` (from Message Type Switch) | Provisioning, billing |
| Device deleted | `Entity Deleted` | Cleanup, deprovisioning |
| Telemetry received | `Post telemetry` | Usage tracking, enrichment, forwarding |
| Attributes updated | `Attributes Updated` | Config sync, notifications |
| Alarm created/updated | `Alarm` (from Message Type Switch) | Notifications, escalation |
| Device activity | `Activity Event` | Monitoring, status tracking |
| Device inactivity | `Inactivity Event` | Alerting, health checks |

### URL templates

The REST API Call node supports templates in the URL:

- `${metadataKey}` — replaced with a value from message metadata
- `$[dataKey]` — replaced with a value from message data

Example: `http://localhost:8090/api/billing/on-device-created?type=${deviceType}&name=$[name]`

### Including metadata in the request body

By default, the REST API Call node sends only `msg.getData()`. To include metadata fields:

1. Add a **Script** node before the REST API Call
2. In the script, merge metadata into the data:
   ```javascript
   var data = JSON.parse(msg);
   data.deviceName = metadata.deviceName;
   data.deviceType = metadata.deviceType;
   return {msg: JSON.stringify(data), metadata: metadata, msgType: msgType};
   ```
3. Connect the Script node's output to the REST API Call node
