# ThingsBoard Extension Starter

A starter project for building custom business logic on top of ThingsBoard. Your extension runs as a standalone Spring Boot service — use it for rule chain callbacks, widget backends, scheduled jobs, or any custom integration.

Works great with [Claude Code](https://claude.com/claude-code) — describe what you want in plain language and Claude generates the controller code, POJOs, and rule chain wiring instructions.

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
  -d '{"temperature": 25.5, "humidity": 60}'
```

Response:
```json
{"status":"ok","keysReceived":2,"keys":["temperature","humidity"]}
```

Health check: `curl http://localhost:8090/api/health`

Swagger UI: `http://localhost:8090/swagger-ui.html`

## How It Works

The extension service runs alongside ThingsBoard and reacts to events in three ways:

```
Pattern 1: HTTP Callback (rule chain or widget)
┌───────────────────────────┐                ┌──────────────────────────────┐
│                           │                │                              │
│  Rule chain REST API Call │   POST + JSON  │  @RestController endpoint    │
│  or Dashboard Widget      ┼───────────────>│  ThingsboardClient resolved  │
│                           │<───────────────┤  from X-Authorization header │
│  X-Authorization:         │  JSON response │                              │
│    ApiKey <key>           │                │                              │
│    or Bearer <jwt>        │                │                              │
└───────────────────────────┘                └──────────────────────────────┘

Pattern 2: Scheduled Background Job (configured credentials)
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│  @Scheduled task runs on a timer — no HTTP request           │
│  ThingsboardClient injected at startup from application.yml  │
│  (TB_AUTH_API_KEY or username+password env vars)             │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

**The X-Authorization header carries authentication:**
1. For rule chain callbacks, you configure a REST API Call node to send `X-Authorization: ApiKey <key>`
2. For widget callbacks, the widget automatically includes the user's JWT when the URL starts with `/api` (relative path) — no manual header setup needed
3. Your extension reads the header and creates a `ThingsboardClient` authenticated as that caller
4. Different tenants send different credentials, so multi-tenancy works automatically

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
- **How it works:** ThingsBoard automatically includes the user's JWT when the widget makes requests to URLs starting with `/api` (relative path) — no manual token handling needed
- **Controller pattern:** identical to API key — declare `ThingsboardClient tb` as a method parameter. The provider detects the `Bearer ` prefix and uses JWT auth automatically

```java
@PostMapping("/current-stats")
public Map<String, Object> currentStats(@RequestBody JsonNode params,
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
@ConditionalOnBean(ThingsboardClient.class)
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

**Note:** If neither `TB_AUTH_API_KEY` nor `TB_AUTH_USERNAME` is set, the `ThingsboardClient` bean is not created. Components that use `@ConditionalOnBean(ThingsboardClient.class)` (like the example `DeviceHealthCheckTask`) are silently skipped — the app starts normally without them.

## Example 1: Usage Tracking on Telemetry

**Business need:** Count how many telemetry keys each message contains (for usage metering, logging, etc.).

This is the simplest pattern — no ThingsBoard API calls needed. Just omit the `ThingsboardClient` parameter.

See full code: [`UsageTrackingController.java`](src/main/java/org/thingsboard/extension/examples/UsageTrackingController.java)

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

### Testing

```bash
curl -X POST http://localhost:8090/api/extension/usage/on-telemetry \
  -H 'Content-Type: application/json' \
  -d '{"temperature": 25.5, "humidity": 60, "pressure": 1013.25}'
```

Response:
```json
{"status":"ok","keysReceived":3,"keys":["temperature","humidity","pressure"]}
```

## Example 2: Billing on Device Creation

**Business need:** When a new device is created, mark it as billing-active by saving a server-side attribute.

This pattern uses `ThingsboardClient` to call ThingsBoard APIs, authenticated via the `X-Authorization` header.

See full code: [`BillingController.java`](src/main/java/org/thingsboard/extension/examples/BillingController.java)

```java
@RestController
@RequestMapping("/api/extension/billing")
public class BillingController {

    @PostMapping("/on-device-created")
    public Map<String, Object> onDeviceCreated(@RequestBody JsonNode device,
                                               ThingsboardClient tb) throws Exception {
        String deviceId = device.get("id").get("id").asText();
        String deviceName = device.get("name").asText();
        String billingStartedAt = Instant.now().toString();

        tb.saveDeviceAttributes(deviceId, "SERVER_SCOPE",
                "{\"billingActive\": true, \"billingStartedAt\": \"%s\"}".formatted(billingStartedAt));

        return Map.of(
                "status", "ok",
                "deviceId", deviceId,
                "deviceName", deviceName,
                "billingStartedAt", billingStartedAt
        );
    }
}
```

**How it works:**
1. `ThingsboardClient tb` — auto-resolved from the `X-Authorization` header
2. `device.get("id").get("id").asText()` — extracts the device UUID from the ThingsBoard entity ID structure `{"entityType": "DEVICE", "id": "uuid"}`
3. `tb.saveDeviceAttributes(...)` — calls the ThingsBoard REST API to save server-side attributes
4. Returns a JSON response — 2xx goes to the Success route, non-2xx to the Failure route

## Example 3: Widget Data Endpoint

**Business need:** Serve tenant statistics to a ThingsBoard dashboard widget — for example, display the total device count in a custom card widget.

This pattern is identical to API key controllers except the widget sends a JWT instead. The `ThingsboardClientProvider` detects the `Bearer ` prefix automatically.

See full code: [`WidgetDataController.java`](src/main/java/org/thingsboard/extension/examples/WidgetDataController.java)

```java
@RestController
@RequestMapping("/api/extension/widget")
public class WidgetDataController {

    @PostMapping("/current-stats")
    public Map<String, Object> currentStats(@RequestBody JsonNode params,
                                            ThingsboardClient tb) throws Exception {
        PageDataDevice devices = tb.getTenantDevices(1, 0, null, null, null, null);
        return Map.of(
                "status", "ok",
                "totalDevices", devices.getTotalElements()
        );
    }
}
```

## Example 4: Scheduled Health Check

**Business need:** Periodically check all tenant devices and record a health check timestamp as a server attribute — without any incoming HTTP request.

See full code: [`DeviceHealthCheckTask.java`](src/main/java/org/thingsboard/extension/examples/DeviceHealthCheckTask.java)

```java
@ConditionalOnBean(ThingsboardClient.class)
@Component
public class DeviceHealthCheckTask {

    private final ThingsboardClient tb;

    public DeviceHealthCheckTask(ThingsboardClient tb) {
        this.tb = tb;
    }

    @Scheduled(fixedRate = 60, timeUnit = TimeUnit.SECONDS)
    public void run() throws Exception {
        PageDataDevice page = tb.getTenantDevices(100, 0, null, null, null, null);
        log.info("Health check: {} devices in tenant", page.getTotalElements());

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
- `@Scheduled(fixedRate = 60, timeUnit = TimeUnit.SECONDS)` triggers the method every 60 seconds
- The `ThingsboardClient` is configured in `application.yml`, not from an HTTP header
- `@ConditionalOnBean` makes this task silently skip when no credentials are configured
- Exceptions are handled by the global `SchedulingConfig` error handler — the task continues on next trigger

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
4. Provide setup and testing instructions

### Option B: Manual

**For an HTTP callback (rule chain or widget):**
1. Create a new `@RestController` class in `src/main/java/org/thingsboard/extension/`
2. Add a `@PostMapping` method that takes `@RequestBody JsonNode` (or a custom POJO)
3. If you need ThingsBoard APIs, add `ThingsboardClient tb` as a parameter
4. Return any object — Spring serializes it to JSON

**For a scheduled background job:**
1. Create a new `@Component` class in `src/main/java/org/thingsboard/extension/`
2. Inject `ThingsboardClient tb` via constructor
3. Add a method annotated with `@Scheduled`
4. Set `TB_AUTH_API_KEY` (or username+password) before starting the service

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

