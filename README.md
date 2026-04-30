# ThingsBoard Extension Starter

A starter template for building custom ThingsBoard extensions as a standalone Spring Boot service. Use it for rule chain callbacks, dashboard widget backends, scheduled background jobs, or any custom integration that needs to talk to the ThingsBoard API.

Works great with [Claude Code](https://claude.com/claude-code) — describe what you want in plain language and Claude generates the controller code, POJOs, and setup instructions.

## Architecture

The extension service runs alongside ThingsBoard and reacts to events in three ways.

### Rule Chain Callback

ThingsBoard rule engine sends events to the extension via a REST API Call node. The extension processes the event and returns a JSON response that routes back into the rule chain.

```
ThingsBoard Rule Engine               Extension Service (port 8090)
+-----------------------+             +--------------------------+
|  Message Type Switch  |             |  @RestController         |
|  -------------------  |  POST JSON  |  /api/extension/...      |
|  "Entity Created"    -+------------>|                          |
|  "Post telemetry"     |             |  ThingsboardClient       |
|  "Alarm"              |  JSON resp  |  from X-Authorization    |
|                      <+-------------|  header                  |
|  Success / Failure    |             |                          |
+-----------------------+             +--------------------------+
```

### Widget Callback

A dashboard widget button calls the extension. The user's JWT authenticates the request, so API calls respect the user's tenant and permissions.

```
ThingsBoard Dashboard                 Extension Service (port 8090)
+-----------------------+             +--------------------------+
|  Widget Button        |             |  @RestController         |
|  "On click" action    |  POST JSON  |  /api/extension/...      |
|  -------------------  +------------>|                          |
|  JWT from user        |             |  ThingsboardClient       |
|  session              |  JSON resp  |  from Bearer JWT         |
|                      <+-------------|                          |
+-----------------------+             +--------------------------+

On-premise: HAProxy routes /api/extension/* to the extension service.
Cloud: widget calls the extension directly at its public URL.
```

### Scheduled Background Job

A background task runs on a timer using credentials configured at startup. No HTTP request needed.

```
Extension Service (port 8090)
+------------------------------------------------------+
|  @Scheduled task runs on a timer                     |
|  ThingsboardClient from application.yml credentials  |
|  (TB_AUTH_API_KEY or username+password)              |
+------------------------------------------------------+
```

### Request/Response Contract

For rule chain callbacks and widget callbacks:

- **Input**: `msg.getData()` JSON from the rule chain — whatever JSON the triggering event carries
- **Output** (optional): any JSON you return — becomes the outgoing message in the rule chain. If you don't need to pass data back, return an empty response or a simple status.
- **2xx response** = Success route in rule chain
- **non-2xx response** = Failure route in rule chain

### Authentication Overview

| Mode | Source | Header | Use case |
|------|--------|--------|----------|
| API Key | ThingsBoard API Keys page | `X-Authorization: ApiKey <key>` | Rule chain callbacks |
| JWT | User session (auto or manual) | `X-Authorization: Bearer <jwt>` | Widget callbacks |
| Configured | `application.yml` or env vars | _(none — injected at startup)_ | Scheduled tasks, background jobs |

## Prerequisites

One of:
- **Docker** (recommended) — no Java or Maven needed
- **Java 17+** — Maven is included via `./mvnw`

And:
- A running ThingsBoard instance (default: `http://localhost:8080`)
- A ThingsBoard API key (for rule chain callbacks) or JWT token (for widget callbacks) or configured credentials (for scheduled tasks)

## Quick Start

```bash
# 1. Clone or copy this project
cd thingsboard-extension-starter

# 2. (Optional) Set your ThingsBoard URL in extension/src/main/resources/application.yml
#    Default: http://localhost:8080

# 3. Run with Docker (recommended)
./run-docker.sh

# Or run with Java (requires Java 17)
./run.sh

# 4. Test it
curl -X POST http://localhost:8090/api/extension/transform/telemetry \
  -H 'Content-Type: application/json' \
  -d '{"temperature_f": 77.0, "pressure_psi": 14.7}'
```

Response:
```json
{"temperature_c":25.0,"pressure_bar":1.01}
```

Health check: `curl http://localhost:8090/api/health`

Swagger UI: `http://localhost:8090/swagger-ui.html`

## Editions (CE / PE / PaaS)

The project supports all three ThingsBoard editions. Set the `thingsboard-client.artifactId` property in the **root** `pom.xml`:

| Edition | Property value |
|---------|---------------|
| CE (Community Edition) | `thingsboard-ce-client` |
| PE (Professional Edition) | `thingsboard-pe-client` |
| PaaS (ThingsBoard Cloud) | `thingsboard-paas-client` |

After switching editions, regenerate the API docs used by Claude Code:

```bash
./mvnw generate-resources -pl extension -q
```

## Integration Guides

### Connecting to ThingsBoard Rule Chains

Use this when you want the extension to react to ThingsBoard events — device telemetry, entity creation, alarms, etc.

**Step-by-step:**

1. Open ThingsBoard UI -> **Rule Chains** -> your rule chain
2. Add a **REST API Call** node
3. Configure it:
   - **Method:** `POST`
   - **URL:** `http://localhost:8090/api/extension/your-feature/endpoint`
   - **Headers:**
     - `Content-Type: application/json`
     - `X-Authorization: ApiKey YOUR_API_KEY`
   - **Credentials:** Anonymous (the API key in the header handles auth)
4. Connect the triggering node (e.g., Message Type Switch -> "Entity Created" output) to the REST API Call node
5. The response JSON goes to the **Success** route (2xx) or **Failure** route (non-2xx)

**On-premise deployment:** If ThingsBoard and the extension run on the same host, the REST API Call node can reach the extension at `http://localhost:8090`. If they run on separate hosts, adjust the URL accordingly. For widget callbacks, HAProxy routing is also needed — see [On-Premise Setup](#on-premise-setup) below.

**Common message types** (full reference in `docs/tb-message-types.md`):

| Message type | Trigger |
|-------------|---------|
| `POST_TELEMETRY_REQUEST` | Device sends telemetry |
| `ENTITY_CREATED` | Entity created via UI, API, or provisioning |
| `ENTITY_UPDATED` | Entity updated |
| `ENTITY_DELETED` | Entity deleted |
| `ALARM` | Alarm created, updated, severity changed, or cleared by rule engine |
| `ATTRIBUTES_UPDATED` | Server-side or shared attributes updated |

### Connecting to Dashboard Widgets

Use this when you want a dashboard button or widget to call the extension.

#### On-Premise Setup

When ThingsBoard and the extension run on the same network, HAProxy routes `/api/extension/*` requests to the extension service.

**1. Add HAProxy routing**

Add the contents of `deploy/on-premise/haproxy-extension.cfg.snippet` to your HAProxy `frontend` section **before** the existing ThingsBoard backend ACL. HAProxy evaluates rules in order — the first match wins.

```
# Frontend ACL (add inside your existing 'frontend' block, BEFORE the ThingsBoard ACL)
acl is_extension path_beg /api/extension/
use_backend thingsboard_extension if is_extension

# Backend block (add as a new block, alongside existing backend blocks)
backend thingsboard_extension
    server extension 127.0.0.1:8090 check
```

**2. Add the widget JS snippet**

Use `widgetContext.http.post(url, body).subscribe(...)` with a relative URL. ThingsBoard automatically adds the user's JWT as `X-Authorization: Bearer <jwt>` — no manual auth needed. Note: `widgetContext.http` is Angular's `HttpClient`, so it returns an Observable (use `.subscribe()`, not `.then()`).

Setup: Widget -> Settings -> Actions -> "On click" -> Custom action (JS). Paste the snippet from `examples/widgets/on-premise-button.js` and change the URL to your extension's endpoint.

#### Cloud Setup

When the extension runs on a separate host from ThingsBoard Cloud, the widget makes cross-origin requests.

**1. Set CORS on the extension**

Set the `CORS_ALLOWED_ORIGINS` environment variable to your ThingsBoard Cloud origin (e.g., `https://thingsboard.cloud`). Without this, browser widget calls are blocked by CORS.

**2. Add the widget JS snippet**

Use `fetch()` with a full URL and read the JWT manually from `localStorage.getItem('jwt_token')`. The snippet includes an explicit `X-Authorization: Bearer <jwt>` header.

Setup: same widget action config as on-premise. Paste the snippet from `examples/widgets/cloud-button.js` and change the URL to your extension's public address.

### Scheduled Background Jobs

No rule chain wiring needed. Set authentication credentials before starting the service:

```bash
# Option 1: API key (recommended)
export TB_AUTH_API_KEY=your-api-key-here

# Option 2: Username and password
export TB_AUTH_USERNAME=tenant@example.com
export TB_AUTH_PASSWORD=your-password
```

Or in `docker-compose.yml`:
```yaml
environment:
  - TB_AUTH_API_KEY=your-api-key-here
```

The `@Scheduled` method runs automatically on the configured interval. Use `@ConditionalOnBean(ThingsboardClient.class)` on the component so it is silently skipped when no credentials are set.

## Authentication Modes

### API Key (Rule Chain Callbacks)

- **Header:** `X-Authorization: ApiKey <key>`
- **How to get a key:** see [API Keys docs](https://thingsboard.io/docs/pe/user-guide/security/api-keys/) (introduced in ThingsBoard 4.3)
- **Controller pattern:** declare `ThingsboardClient tb` as a method parameter

```java
@PostMapping("/on-device-created")
public Map<String, Object> onDeviceCreated(@RequestBody JsonNode device,
                                           ThingsboardClient tb) throws Exception {
    // tb is authenticated with the API key from the X-Authorization header
}
```

### JWT Token (Widget Callbacks)

- **Header:** `X-Authorization: Bearer <jwt>`
- **How it works:** on-premise widgets auto-include the JWT for relative `/api` URLs; cloud widgets read it from `localStorage`
- **Controller pattern:** identical to API key — the provider detects the `Bearer ` prefix automatically

```java
@PostMapping("/current-stats")
public Map<String, Object> currentStats(@RequestBody JsonNode params,
                                        ThingsboardClient tb) throws Exception {
    // tb is authenticated with the user's JWT
}
```

### Configured Credentials (Scheduled Tasks)

- **No header needed** — credentials set in `application.yml` or via environment variables
- **Setup:** set `TB_AUTH_API_KEY` (recommended) or `TB_AUTH_USERNAME` + `TB_AUTH_PASSWORD`
- **Component pattern:** constructor injection of `ThingsboardClient`

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

If no credentials are configured, the `ThingsboardClient` bean is not created and components with `@ConditionalOnBean` are silently skipped.

## Security and Authorization

Restrict endpoints to specific user roles using `@PreAuthorize` with `hasAuthority()`.

Available authorities: `SYS_ADMIN`, `TENANT_ADMIN`, `CUSTOMER_USER`.

```java
@PreAuthorize("hasAuthority('TENANT_ADMIN')")
@PostMapping("/admin-only")
public Map<String, Object> adminOnly(@RequestBody JsonNode data,
                                     ThingsboardClient tb) throws Exception {
    // Only tenant admins can access this endpoint
}
```

Combine with logical operators:

```java
@PreAuthorize("hasAuthority('TENANT_ADMIN') or hasAuthority('CUSTOMER_USER')")
@PostMapping("/tenant-or-customer")
public Map<String, Object> tenantOrCustomer(@RequestBody JsonNode data,
                                            ThingsboardClient tb) throws Exception {
    // Accessible to both tenant admins and customer users
}
```

Access the authenticated user inside a controller method:

```java
import org.thingsboard.extension.config.TbSecurity;
import org.thingsboard.extension.config.TbSecurityUser;

// Option 1: static helper
TbSecurityUser user = TbSecurity.getCurrentUser();
UUID tenantId = user.getTenantId();
Authority authority = user.getAuthority();

// Option 2: Spring's @AuthenticationPrincipal annotation
@PostMapping("/my-endpoint")
public Map<String, Object> myEndpoint(@AuthenticationPrincipal TbSecurityUser user,
                                       ThingsboardClient tb) throws Exception {
    UUID tenantId = user.getTenantId();
    // ...
}
```

The user lookup (`getUser()`) is lazy — it is only called when `@PreAuthorize` is present or when you access `TbSecurityUser` fields. Endpoints without `@PreAuthorize` have zero authorization overhead.

## Examples

The project includes four example extensions that demonstrate different patterns. These are intentionally simple — they exist to show the integration patterns, not to solve real problems.

1. **Telemetry Unit Conversion** — no-auth pattern. Converts telemetry values (F to C, psi to bar) without calling the ThingsBoard API. See [`TelemetryUnitConversionController.java`](examples/src/main/java/org/thingsboard/extension/examples/TelemetryUnitConversionController.java).

2. **Billing on Device Creation** — API key auth pattern. Saves a `billingActive` server-side attribute when a device is created. See [`BillingController.java`](examples/src/main/java/org/thingsboard/extension/examples/BillingController.java).

3. **Tenant Report (Widget Button)** — JWT auth pattern. Counts all devices, assets, and users in the tenant. See [`TenantReportController.java`](examples/src/main/java/org/thingsboard/extension/examples/TenantReportController.java).

4. **Scheduled Health Check** — configured credentials pattern. Runs every 60 seconds and writes a `lastHealthCheckTs` attribute to all devices. See [`DeviceHealthCheckTask.java`](examples/src/main/java/org/thingsboard/extension/examples/DeviceHealthCheckTask.java).

Delete the `examples/` module when you are ready to write your own code. See [Removing Examples](#removing-examples).

## Creating Your Own Extension

### Option A: Use Claude Code (recommended)

Open this project in Claude Code and describe what you want:

> "I need reusable user profiles — like 'Manager', 'Technician', 'Viewer'. Each profile defines a default dashboard, sharing role, and custom roles. I want CRUD endpoints to manage profiles (stored as attributes on a tenant asset), an endpoint to assign a profile to a user (sets default dashboard, creates a user group, shares dashboards, assigns roles), and an endpoint to unassign it (reverses everything)."

Claude will ask clarifying questions, generate the controller class (or scheduled task), add any needed dependencies to `pom.xml`, and provide setup and testing instructions.

### Option B: Manual

**For an HTTP callback (rule chain or widget):**
1. Create a new `@RestController` class in `extension/src/main/java/org/thingsboard/extension/`
2. Add a `@PostMapping` method that takes `@RequestBody JsonNode` (or a custom POJO)
3. If you need ThingsBoard APIs, add `ThingsboardClient tb` as a method parameter
4. Return any object — Spring serializes it to JSON
5. All extension endpoints must start with `/api/extension/`

**For a scheduled background job:**
1. Create a new `@Component` class in `extension/src/main/java/org/thingsboard/extension/`
2. Inject `ThingsboardClient tb` via constructor
3. Add a method annotated with `@Scheduled`
4. Add `@ConditionalOnBean(ThingsboardClient.class)` to the class
5. Set `TB_AUTH_API_KEY` (or username+password) before starting the service

**Need extra libraries?** (Slack SDK, email, database driver, etc.) Add a `<dependency>` block to `pom.xml` inside the `<dependencies>` section.

## Hot Reload

The project includes `spring-boot-devtools`. When running with `./mvnw spring-boot:run`:

1. Make your code changes
2. Run `./mvnw compile -pl extension -q` in a separate terminal
3. The service auto-restarts in ~2 seconds

## Deployment

### Build the Docker Image

```bash
./build-docker-image.sh
```

This builds the JAR and creates a Docker image tagged `thingsboard-extension:<version>` and `thingsboard-extension:latest`. The version comes from the latest git tag, or the short git SHA if no tag exists.

To use a custom image name:

```bash
IMAGE_NAME=myorg/thingsboard-extension ./build-docker-image.sh
```

### Publish to a Registry

```bash
# Docker Hub
IMAGE_NAME=myuser/thingsboard-extension ./publish-docker-image.sh

# Private registry
REGISTRY=registry.example.com ./publish-docker-image.sh
```

### On-Premise

Deploy the extension alongside your existing ThingsBoard installation.

**1. Configure environment variables**

```bash
cd deploy/on-premise
cp .env.example .env
# Edit .env — set IMAGE_NAME if you published to a registry
```

**2. Start the extension**

```bash
docker compose up -d
```

The extension connects to ThingsBoard at `http://host.docker.internal:8080` by default. Change `THINGSBOARD_URL` in `.env` if your ThingsBoard runs on a different host or port.

**3. Add HAProxy routing**

Open your HAProxy configuration and add the contents of `deploy/on-premise/haproxy-extension.cfg.snippet` to your `frontend` section. Insert it **before** your existing ThingsBoard backend ACL — HAProxy evaluates rules in order and the first match wins.

```
# Add BEFORE the existing ThingsBoard ACL
acl is_extension path_beg /api/extension/
use_backend thingsboard_extension if is_extension
```

Add the backend block alongside your existing backends:

```
backend thingsboard_extension
    server extension 127.0.0.1:8090 check
```

Reload HAProxy to apply changes.

**4. Verify**

```bash
curl http://localhost:8090/api/health
```

Expected response: `{"status":"UP"}`

> **Tip:** The default log level is DEBUG, which is verbose. For production, place a custom `logback.xml` in the `config/` directory with `<logger name="org.thingsboard.extension" level="INFO"/>`.

### Cloud

Deploy the extension on a VPS or any server with a public IP, connecting to ThingsBoard Cloud.

**1. Build and push the image to a registry**

The cloud server needs to pull your image from a registry:

```bash
./build-docker-image.sh
REGISTRY=registry.example.com ./publish-docker-image.sh
```

**2. Configure environment variables**

On the cloud server:

```bash
cd deploy/cloud
cp .env.example .env
```

Edit `.env` and set:
- `IMAGE_NAME` — the full image name including registry prefix (e.g., `registry.example.com/thingsboard-extension`)
- `THINGSBOARD_URL` — your ThingsBoard Cloud URL (default: `https://thingsboard.cloud`)
- `CORS_ALLOWED_ORIGINS` — **required** — the origin of your ThingsBoard Cloud instance (e.g., `https://thingsboard.cloud`). Without this, browser widget calls are blocked by CORS.

**3. Start the extension**

```bash
docker compose up -d
```

**4. Verify CORS**

Test that preflight requests succeed (run this from any machine):

```bash
curl -s -o /dev/null -w "%{http_code}" \
  -X OPTIONS https://your-extension-host:8090/api/health \
  -H "Origin: https://thingsboard.cloud" \
  -H "Access-Control-Request-Method: POST"
```

Expected: `200`

**5. Verify the health endpoint**

```bash
curl https://your-extension-host:8090/api/health
```

Expected response: `{"status":"UP"}`

> **Tip:** The default log level is DEBUG, which is verbose. For production, place a custom `logback.xml` in the `config/` directory with `<logger name="org.thingsboard.extension" level="INFO"/>`.

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

Request/response logging is controlled by the logback level for `org.thingsboard.extension` (DEBUG = on, INFO = off). See `extension/src/main/resources/logback.xml`.

### Environment Variables (Docker)

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8090` | Port mapping |
| `THINGSBOARD_URL` | `http://host.docker.internal:8080` | ThingsBoard URL from container |
| `TB_AUTH_API_KEY` | _(empty)_ | API key for the shared ThingsboardClient bean (takes precedence over username+password) |
| `TB_AUTH_USERNAME` | _(empty)_ | Username for the shared ThingsboardClient bean |
| `TB_AUTH_PASSWORD` | _(empty)_ | Password for the shared ThingsboardClient bean |
| `CORS_ALLOWED_ORIGINS` | _(empty)_ | Comma-separated allowed origins for CORS (required for cloud deployments) |
| `JAVA_OPTS` | _(empty)_ | JVM options |

### Headers

| Header | Required | Description |
|--------|----------|-------------|
| `Content-Type` | Yes | Must be `application/json` |
| `X-Authorization` | Yes* | Authentication header. Two schemes: `ApiKey <key>` for rule chain callbacks, `Bearer <jwt>` for widget callbacks. *Required when the controller declares a `ThingsboardClient` parameter. Missing or invalid header returns 401. |

## Removing Examples

When you are ready to write your own extensions, remove the example code:

1. Delete the `examples/` directory
2. Remove `<module>examples</module>` from the root `pom.xml`
3. Remove the `thingsboard-extension-examples` dependency from `extension/pom.xml`
4. Remove example-specific test methods from [`ApplicationIntegrationTest.java`](extension/src/test/java/org/thingsboard/extension/ApplicationIntegrationTest.java):
   - `telemetryConversionWorksWithoutAuth`
   - `billingEndpointReturns401WithoutAuth`
   - `reportEndpointReturns401WithoutAuth`
