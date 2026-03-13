# ThingsBoard Extension Starter

A starter project for building custom business logic on top of ThingsBoard. Your extension runs as a standalone Spring Boot service — use it for rule chain callbacks, widget backends, scheduled jobs, or any custom integration.

**The experience for vibe-coders:** open this project in [Claude Code](https://claude.com/claude-code), describe what you want in plain language, and Claude generates everything — controller code, POJOs, and rule chain wiring instructions.

## Prerequisites

One of:
- **Docker** (recommended) — no Java or Maven needed
- **Java 25+** — Maven is included via `./mvnw`

And:
- A running ThingsBoard instance (default: `http://localhost:8080`)
- A ThingsBoard API key (for rule chain callbacks) or JWT token (for widget callbacks) or configured credentials (for scheduled tasks)

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
curl -X POST http://localhost:8090/api/extension/usage/on-telemetry \
  -H 'Content-Type: application/json' \
  -H 'X-Authorization: ApiKey YOUR_API_KEY' \
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

The extension service runs alongside ThingsBoard and reacts to events in three ways:

```
Pattern 1: Rule Chain Callback (API key)
┌───────────────────────────┐                ┌──────────────────────────────┐
│                           │                │                              │
│ [Event] ──> [REST API     │   POST + JSON  │  @RestController endpoint    │
│              Call node]   ┼───────────────>│  ThingsboardClient resolved  │
│      X-Authorization:     │<───────────────┤  from X-Authorization header │
│        ApiKey <key>       │  JSON response │                              │
└───────────────────────────┘                └──────────────────────────────┘

Pattern 2: Widget Callback (JWT)
┌───────────────────────────┐                ┌──────────────────────────────┐
│                           │                │                              │
│  Dashboard Widget         │   POST + JSON  │  @RestController endpoint    │
│  (HTTP datasource or      ┼───────────────>│  ThingsboardClient resolved  │
│   custom JS fetch)        │<───────────────┤  from X-Authorization header │
│  X-Authorization:         │  JSON response │                              │
│    Bearer ${tbAuthToken}  │                │                              │
└───────────────────────────┘                └──────────────────────────────┘

Pattern 3: Scheduled Background Job (configured credentials)
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│  @Scheduled task runs on a timer — no HTTP request          │
│  ThingsboardClient injected at startup from application.yml  │
│  (TB_AUTH_API_KEY or username+password env vars)             │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

**The X-Authorization header carries authentication:**
1. For rule chain callbacks, you configure a REST API Call node to send `X-Authorization: ApiKey <key>`
2. For widget callbacks, the widget sends `X-Authorization: Bearer <jwt>` using the `${tbAuthToken}` variable
3. Your extension reads the header and creates a `ThingsboardClient` authenticated as that caller
4. Multi-tenancy for free — different tenants send different credentials

**Request/response is plain JSON:**
- **Input**: `msg.getData()` from the rule chain — whatever JSON the triggering event carries
- **Output**: any JSON you return — becomes the outgoing message in the rule chain
- **2xx response** = Success route in rule chain
- **non-2xx response** = Failure route in rule chain

## Authentication Modes

### API Key (rule chain callbacks)

Used when ThingsBoard rule chains call your extension via a **REST API Call** node.

- **Header format:** `X-Authorization: ApiKey <key>`
- **How to get a key:** ThingsBoard UI → API Keys → create key as Tenant Admin
- **Controller pattern:** declare `ThingsboardClient tb` as a method parameter — the provider resolves and caches the client automatically

```java
@PostMapping("/on-device-created")
public Map<String, Object> onDeviceCreated(@RequestBody JsonNode device,
                                           ThingsboardClient tb) throws Exception {
    // tb is authenticated with the API key from the X-Authorization header
}
```

### JWT Token (widget callbacks)

Used when ThingsBoard dashboard widgets call your extension directly.

- **Header format:** `X-Authorization: Bearer <jwt>`
- **How to get the token:** ThingsBoard injects `${tbAuthToken}` automatically in widget datasource configurations — you do not need to manage the JWT yourself
- **Controller pattern:** identical to API key — declare `ThingsboardClient tb` as a method parameter. The provider detects the `Bearer ` prefix and uses JWT auth automatically

```java
@PostMapping("/current-stats")
public Map<String, Object> getCurrentStats(@RequestBody JsonNode params,
                                           ThingsboardClient tb) throws Exception {
    // tb is authenticated with the user's JWT — API calls respect tenant/permissions
}
```

**Note:** The value must be `Bearer <token>` including the `Bearer ` prefix and space. The token without the prefix returns 401.

### Configured Credentials (scheduled tasks)

Used for background jobs that run on a schedule, with no incoming HTTP request.

- **No header needed** — credentials are set in `application.yml` or via environment variables
- **How to set up:** configure `TB_AUTH_API_KEY` (recommended) or `TB_AUTH_USERNAME` + `TB_AUTH_PASSWORD` environment variables
- **Component pattern:** `@Component` class with constructor injection of `ThingsboardClient`

```java
@Component
public class MyScheduledTask {
    private final ThingsboardClient tb;

    public MyScheduledTask(ThingsboardClient tb) {
        this.tb = tb;
    }

    @Scheduled(fixedRate = 60, timeUnit = TimeUnit.SECONDS)
    public void run() throws Exception {
        // tb is authenticated with the configured credentials
    }
}
```

**Note:** If neither `TB_AUTH_API_KEY` nor `TB_AUTH_USERNAME` is set, the `ThingsboardClient` bean is not created and the application will fail to start if a scheduled task tries to inject it.

## Example 1: Billing on Device Creation

**Business need:** When a new device is created, mark it as billing-active by saving a server-side attribute.

### Controller code

`src/main/java/org/thingsboard/extension/examples/BillingController.java`:

```java
@RestController
@RequestMapping("/api/extension/billing")
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
2. `ThingsboardClient tb` — auto-resolved from the `X-Authorization` header. The `ThingsboardClientProvider` reads the header, creates (or returns a cached) client authenticated with that API key.
3. `device.get("id").get("id").asText()` — extracts the device UUID from the ThingsBoard entity ID structure `{"entityType": "DEVICE", "id": "uuid"}`.
4. `tb.saveDeviceAttributes(...)` — calls the ThingsBoard REST API to save server-side attributes on the device.
5. Returns a JSON response — this becomes the outgoing message on the **Success** route of the REST API Call node.

### Rule chain setup

1. Open **ThingsBoard UI → Rule Chains** → edit your rule chain (or create a new one)
2. In the left panel, find **Action → REST API Call** and drag it onto the canvas
3. Configure the node:
   - **Name**: `Billing: on device created`
   - **Method**: `POST`
   - **URL**: `http://localhost:8090/api/extension/billing/on-device-created`
   - **Headers**:
     - `Content-Type`: `application/json`
     - `X-Authorization`: `ApiKey YOUR_API_KEY`
   - **Credentials**: `Anonymous`
4. Connect the **Entity Created** message type to this node:
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
@RequestMapping("/api/extension/usage")
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
   - **URL**: `http://localhost:8090/api/extension/usage/on-telemetry`
   - **Headers**: `Content-Type: application/json`, `X-Authorization: ApiKey YOUR_API_KEY`
   - **Credentials**: `Anonymous`
2. From the **Message Type Switch** node, connect **Post telemetry** to this node
3. Save the rule chain

### Testing

```bash
# Simulate what the rule chain sends
curl -X POST http://localhost:8090/api/extension/usage/on-telemetry \
  -H 'Content-Type: application/json' \
  -H 'X-Authorization: ApiKey any-key-here' \
  -d '{"temperature": 25.5, "humidity": 60, "pressure": 1013.25}'
```

Response:
```json
{"status":"ok","keysReceived":3,"keys":["temperature","humidity","pressure"]}
```

## Example 3: Widget Data Endpoint

**Business need:** Serve tenant statistics to a ThingsBoard dashboard widget — for example, display the total device count in a custom card widget.

### Controller code

`src/main/java/org/thingsboard/extension/examples/WidgetDataController.java`:

```java
@RestController
@RequestMapping("/api/extension/widget")
public class WidgetDataController {

    @PostMapping("/current-stats")
    public Map<String, Object> getCurrentStats(@RequestBody JsonNode params,
                                               ThingsboardClient tb) throws Exception {
        PageDataDevice devices = tb.getTenantDevices(1, 0, null, null, null, null);
        return Map.of(
            "status", "ok",
            "totalDevices", devices.getTotalElements()
        );
    }
}
```

**How it works:** The method signature is identical to API key controllers — only the header value differs. The `ThingsboardClientProvider` detects the `Bearer ` prefix automatically and uses JWT authentication. The client is authenticated as the widget user, so API calls respect their tenant and permissions.

### Widget wiring

**Primary method: HTTP Datasource**

In your ThingsBoard widget, configure an HTTP Datasource:
- **Method**: `POST`
- **URL**: `http://localhost:8090/api/extension/widget/current-stats`
- **Headers**:
  - `Content-Type`: `application/json`
  - `X-Authorization`: `Bearer ${tbAuthToken}`

`${tbAuthToken}` is a ThingsBoard platform variable that is injected automatically when the widget runs in a dashboard context — it contains the current user's session JWT. You do not manage this token yourself.

**Alternative method: Custom widget JavaScript**

```javascript
fetch('http://localhost:8090/api/extension/widget/current-stats', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'X-Authorization': 'Bearer ' + ctx.authService.getJwtToken()
    },
    body: JSON.stringify({})
})
.then(r => r.json())
.then(data => {
    // use data.totalDevices in your widget
});
```

### Testing

```bash
# Test using a JWT token from ThingsBoard
curl -X POST http://localhost:8090/api/extension/widget/current-stats \
  -H 'Content-Type: application/json' \
  -H 'X-Authorization: Bearer YOUR_JWT_TOKEN' \
  -d '{}'
```

To get a JWT for testing, log in to ThingsBoard via the REST API:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"tenant@example.com","password":"your-password"}'
# Use the "token" field from the response as YOUR_JWT_TOKEN
```

## Example 4: Scheduled Health Check

**Business need:** Periodically check all tenant devices and record a health check timestamp as a server attribute — without any incoming HTTP request.

### Component code

`src/main/java/org/thingsboard/extension/examples/DeviceHealthCheckTask.java`:

```java
@Component
public class DeviceHealthCheckTask {

    private static final Logger log = LoggerFactory.getLogger(DeviceHealthCheckTask.class);

    private final ThingsboardClient tb;

    public DeviceHealthCheckTask(ThingsboardClient tb) {
        this.tb = tb;
    }

    @Scheduled(fixedRate = 60, timeUnit = TimeUnit.SECONDS)
    public void run() throws Exception {
        PageDataDevice page = tb.getTenantDevices(100, 0, null, null, null, null);
        long total = page.getTotalElements();
        log.info("Health check: {} devices in tenant", total);

        String ts = String.valueOf(System.currentTimeMillis());
        for (var device : page.getData()) {
            String deviceId = device.getId().getId().toString();
            tb.saveDeviceAttributes(deviceId, "SERVER_SCOPE",
                "{\"lastHealthCheckTs\": " + ts + "}");
        }
    }
}
```

**How it works:**
- The `ThingsboardClient` is the background task client configured in `application.yml`
- `@Scheduled(fixedRate = 60, timeUnit = TimeUnit.SECONDS)` triggers the method every 60 seconds
- The method logs how many devices exist and saves a `lastHealthCheckTs` attribute to each one
- No rule chain wiring needed — the task starts automatically when the service starts
- If the task throws an exception, the global `SchedulingConfig` error handler logs it at ERROR level and the task continues running on the next trigger

### Setup

No rule chain wiring needed. Set the authentication credentials before starting the service:

```bash
# Option 1: API key (recommended)
export TB_AUTH_API_KEY=your-api-key-here

# Option 2: Username and password
export TB_AUTH_USERNAME=tenant@example.com
export TB_AUTH_PASSWORD=your-password

# Then start the service
./run.sh
```

Or in `docker-compose.yml`:
```yaml
environment:
  - TB_AUTH_API_KEY=your-api-key-here
```

**Verification:** After the service starts, check the logs for the INFO message:
```
Health check: 5 devices in tenant
```
Then open a device in ThingsBoard → **Attributes** → **Server attributes** and verify `lastHealthCheckTs` appears.

## Creating Your Own Extension

### Option A: Use Claude Code (recommended)

Open this project in Claude Code and describe what you want:

> "I want to send a Slack notification when a critical alarm is created"

Claude will:
1. Ask clarifying questions (or you provide details upfront)
2. Generate the controller class (or scheduled task)
3. Add any needed dependencies to `pom.xml`
4. Tell you exactly how to wire the rule chain (or configure credentials for scheduled tasks)

### Option B: Manual

**For a rule chain or widget callback:**
1. Create a new `@RestController` class in `src/main/java/org/thingsboard/extension/`
2. Add a `@PostMapping` method that takes `@RequestBody JsonNode` (or a custom POJO)
3. If you need ThingsBoard APIs, add `ThingsboardClient tb` as a parameter
4. Return any object — Spring serializes it to JSON
5. Wire a REST API Call node in ThingsBoard pointing to your endpoint

**For a scheduled background job:**
1. Create a new `@Component` class in `src/main/java/org/thingsboard/extension/`
2. Inject `ThingsboardClient tb` via constructor
3. Add a method annotated with `@Scheduled`
4. Set `TB_AUTH_API_KEY` (or username+password) before starting the service
5. No rule chain wiring needed — the task runs automatically

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
| `thingsboard.client.cache-ttl` | `60` | Client cache TTL in minutes. For JWT auth, set this lower than the ThingsBoard JWT TTL (default 2.5 hours) to avoid serving expired cached clients. |
| `thingsboard.client.cache-max-size` | `100` | Max cached ThingsBoard clients |
| `thingsboard.auth.api-key` | _(empty)_ | Optional API key for the shared ThingsboardClient bean. Takes precedence over username+password if both are set. |
| `thingsboard.auth.username` | _(empty)_ | Optional username for the shared ThingsboardClient bean. |
| `thingsboard.auth.password` | _(empty)_ | Optional password for the shared ThingsboardClient bean. |

Request/response logging is controlled by the logback level for `org.thingsboard.extension` (DEBUG = on, INFO = off). See `src/main/resources/logback.xml`.

### Environment variables (Docker)

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8090` | Port mapping |
| `THINGSBOARD_URL` | `http://host.docker.internal:8080` | ThingsBoard URL from container |
| `TB_AUTH_API_KEY` | _(empty)_ | API key for the shared ThingsboardClient bean (takes precedence over username+password) |
| `TB_AUTH_USERNAME` | _(empty)_ | Username for the shared ThingsboardClient bean |
| `TB_AUTH_PASSWORD` | _(empty)_ | Password for the shared ThingsboardClient bean |
| `JAVA_OPTS` | _(empty)_ | JVM options |

### Headers

| Header | Required | Description |
|--------|----------|-------------|
| `Content-Type` | Yes | Must be `application/json` |
| `X-Authorization` | Yes* | Authentication header. Two schemes: `ApiKey <key>` for rule chain callbacks, `Bearer <jwt>` for widget callbacks. *Required when the controller declares a `ThingsboardClient` parameter. Missing or invalid header returns 401. |

### Creating an API Key

1. Log in to ThingsBoard as **Tenant Administrator**
2. Go to **API Keys** section in the left menu
3. Click the **+** button to create a new API key
4. Copy the key value — this is what goes in the `X-Authorization: ApiKey <key>` header

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

The exact message type constants are `ENTITY_CREATED`, `POST_TELEMETRY_REQUEST`, `ATTRIBUTES_UPDATED`, `INACTIVITY_EVENT`, and `ALARM`. Use these when writing code or rule chain scripts that check `msgType`.

### URL templates

The REST API Call node supports templates in the URL:

- `${metadataKey}` — replaced with a value from message metadata
- `$[dataKey]` — replaced with a value from message data

Example: `http://localhost:8090/api/extension/billing/on-device-created?type=${deviceType}&name=$[name]`

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
