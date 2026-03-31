# CLAUDE.md

## What This Is

A Spring Boot starter template for building custom ThingsBoard extensions. Write `@RestController` endpoints or `@Scheduled` background jobs — the template handles ThingsBoard API authentication, client management, and common boilerplate.

## Important: Know When to Ask vs. When to Figure It Out

The user of this project is most likely **not a developer** — they understand their ThingsBoard setup and business needs, but not Java, Spring, or programming internals.

- **Business logic questions → always ask the user** (using `AskUserQuestion`). Never guess what the user wants. If you're unsure about the intended behavior, what data to use, which entities are involved, how something should be triggered, or what the output should look like — ask. Examples: "Should deleted devices decrement the count or reset it?", "Which asset should store this data?", "Should this run on every telemetry message or only when a threshold is exceeded?"

- **Technical/implementation questions → figure it out yourself.** Do not ask the user about Java imports, Spring annotations, method signatures, build errors, or ThingsBoard client API usage. Use the API docs in `extension/target/api-docs/` (controller APIs *and* model class docs), the examples in `extension/target/api-docs/tb-examples.md`, and `./mvnw compile` to resolve technical issues on your own. **Never search `~/.m2` or decompile JARs** — all API and model documentation is in `extension/target/api-docs/`.

## How to Create a New Extension

**⚠️ MANDATORY — do NOT skip any of these steps. Do NOT jump ahead to writing code.**

When a user asks to create an extension, you MUST follow every step below **in order**. Do not generate any code until steps 1 and 2 are fully complete.

### 1. Ask the edition (BLOCKING — must happen first)

Before doing ANYTHING else, ask the user which ThingsBoard edition they're targeting. Use `AskUserQuestion` with these options:

- **CE** — Community Edition (open-source)
- **PE** — Professional Edition (licensed, extra features)
- **PaaS** — ThingsBoard Cloud (managed SaaS)

Do NOT mention ThingsBoard Cloud domain URLs — just use the label above.

Then update the `thingsboard-client.artifactId` property in `pom.xml`:

| Edition | Property value |
|---------|---------------|
| CE | `thingsboard-ce-client` |
| PE | `thingsboard-pe-client` |
| PaaS | `thingsboard-paas-client` |

This single property controls both the dependency and the API docs extraction.

### 2. Set up API docs (BLOCKING — must happen before writing any code)

After setting the edition in `pom.xml`, run `./mvnw generate-resources -pl extension -q` — this unpacks API docs from the client JAR into `extension/target/api-docs/`. It always overwrites, so it's safe to re-run after switching editions.

**Do NOT read any files from `extension/target/api-docs/` until AFTER running `./mvnw generate-resources -pl extension`.** Always regenerate first, then read.

**Do NOT skip this step.** Even if `extension/target/api-docs/` already exists, it may contain docs for the wrong edition.

### 3. Understand the business need

Ask these questions (the user can skip by providing a detailed prompt upfront):

- **What event triggers it?** (device created, telemetry posted, alarm created, etc.) — then **read `docs/tb-message-types.md`** to find the exact message type (e.g., `ENTITY_CREATED`) and understand the JSON payload structure. You will need both when generating code and when writing rule chain setup instructions.
- **Does it need to call ThingsBoard APIs?** (save attributes, look up devices, create alarms, etc.) — see the API docs in `extension/target/api-docs/` (run `./mvnw generate-resources -pl extension` first if the folder doesn't exist). Each `*Api.md` file lists all available methods for that controller with parameters and return types.
- **Does it need external services?** (Slack, email, database, HTTP API) — if so, add the dependency to `pom.xml`.
- **Will it be called from a dashboard widget?** If yes — is the ThingsBoard instance on-premise (same origin) or cloud (cross-origin)? Cross-origin (cloud) requires `CORS_ALLOWED_ORIGINS` to be set on the extension. See `deploy/cloud/.env.example`.
- **What should it return?** The response JSON becomes the outgoing message in the rule chain (2xx = Success route, non-2xx = Failure route).

### 4. Generate the code

Create a new `@RestController` class in `extension/src/main/java/org/thingsboard/extension/`. Every Java file must start with this exact license header:

```java
/**
 * Copyright © 2026-2026 ThingsBoard, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.extension;
```

Controller template:

```java
@RestController
@RequestMapping("/api/extension/your-feature")
public class YourFeatureController {

    @PostMapping("/on-some-event")
    public Map<String, Object> onSomeEvent(@RequestBody JsonNode data,
                                           ThingsboardClient tb) throws Exception {
        // Your logic here
        // Use tb.someMethod() to call ThingsBoard APIs
        return Map.of("status", "ok");
    }
}
```

- All extension endpoints MUST start with `/api/extension/`. The `/api/health` endpoint is reserved for infrastructure.
- If ThingsBoard API calls are needed: declare `ThingsboardClient tb` as a parameter -- it's auto-resolved from the `X-Authorization` header.
- If no ThingsBoard API calls are needed: simply omit the `ThingsboardClient` parameter.
- If new dependencies are needed: add them to `pom.xml`.
- If the input/output JSON is complex: create POJO classes next to the controller.

Scheduled task template (for background jobs that run on a timer):

```java
@ConditionalOnBean(ThingsboardClient.class)
@Component
public class YourScheduledTask {
    private final ThingsboardClient tb;

    public YourScheduledTask(ThingsboardClient tb) {
        this.tb = tb;
    }

    @Scheduled(fixedRate = 60, timeUnit = TimeUnit.SECONDS)
    public void run() {
        // use tb to call ThingsBoard APIs on a schedule
    }
}
```

- The `ThingsboardClient` bean uses credentials from `application.yml` (`thingsboard.auth.*`).
- Use `@ConditionalOnBean(ThingsboardClient.class)` so the task is silently skipped when no credentials are configured. Without this annotation, the app fails at startup with a Spring dependency injection error when no credentials are set.
- Do NOT wrap `@Scheduled` methods in try-catch -- the global `ErrorHandler` in `SchedulingConfig` handles exceptions.

### 5. Verify the code compiles

Run `./mvnw compile -q` after generating the code. If it fails, read the error output and fix the issues before proceeding. This catches wrong imports, missing types, and API mismatches immediately.

### 6. Provide setup instructions

**For controller extensions (HTTP callbacks):** Tell the user how to wire it in ThingsBoard. Use the exact message type names from `docs/tb-message-types.md` (e.g., `ENTITY_CREATED`, `POST_TELEMETRY_REQUEST`) -- do not guess or paraphrase them.

1. Open ThingsBoard -> Rule Chains -> your rule chain
2. Add a **REST API Call** node with:
   - Method: `POST`
   - URL: `http://localhost:8090/api/extension/your-feature/on-some-event`
   - Headers: `Content-Type: application/json` and `X-Authorization: ApiKey YOUR_API_KEY`
   - Credentials: Anonymous
3. Connect the triggering node to this REST API Call node (specify the exact message type to filter on)
4. The response JSON goes to the **Success** route (2xx) or **Failure** route (non-2xx)

**For widget callback extensions (called from dashboard widgets):** Tell the user how to add the JS snippet to their widget. The setup differs by deployment mode:

- **On-premise:** Point the user to `examples/widgets/on-premise-button.js`. The snippet uses `self.ctx.http.post()` with a relative URL — ThingsBoard automatically adds the user's JWT. Tell the user to replace the URL path with their extension's endpoint. No CORS configuration needed.
- **Cloud:** Point the user to `examples/widgets/cloud-button.js`. The snippet uses `fetch()` with a full URL and reads the JWT from `localStorage`. Tell the user to: (1) replace the URL with their extension's public address, and (2) set `CORS_ALLOWED_ORIGINS` on the extension to their ThingsBoard Cloud origin.

Setup steps: Widget -> Settings -> Actions -> "On click" -> Custom action (JS) -> paste the snippet.

**For scheduled tasks:** No rule chain wiring needed. Ensure the user has configured credentials in `application.yml` (or via environment variables `TB_AUTH_API_KEY` or `TB_AUTH_USERNAME` + `TB_AUTH_PASSWORD`). The task runs automatically on the configured schedule.

## Project Conventions

### Authentication flows

Three ways to get a `ThingsboardClient`:

- **Request-based (API key)**: Rule chain sends `X-Authorization: ApiKey <key>` header. `ThingsboardClientProvider` resolves a cached client. Declare `ThingsboardClient tb` as a controller method parameter.
- **Request-based (JWT)**: Widget sends `X-Authorization: Bearer <jwt>` header. `ThingsboardClientProvider` resolves a cached client. Same parameter injection as API key.
- **Configured (background jobs)**: Optional credentials in `application.yml` (`thingsboard.auth.*`). Inject via constructor: `ThingsboardClient tb`. Used for scheduled tasks and startup logic -- no HTTP request needed.

For request-based flows, missing or invalid `X-Authorization` header returns 401 Unauthorized.

### Security and Authorization

The extension uses Spring Security for method-level authorization via `@PreAuthorize`. The security context is populated lazily — `ThingsboardClient.getUser()` is only called when `@PreAuthorize` or explicit `SecurityContextHolder` access needs it.

Available authorities (from ThingsBoard `Authority` enum):
- `SYS_ADMIN`
- `TENANT_ADMIN`
- `CUSTOMER_USER`

Usage:
```java
@PreAuthorize("hasAuthority('TENANT_ADMIN')")
@PostMapping("/admin-only")
public Map<String, Object> adminOnly(@RequestBody JsonNode data, ThingsboardClient tb) throws Exception {
    // Only TENANT_ADMIN users can access this
}
```

Combine authorities: `@PreAuthorize("hasAuthority('TENANT_ADMIN') or hasAuthority('CUSTOMER_USER')")`

Access the security user in controller code:
```java
// Option 1: static helper
TbSecurityUser user = TbSecurity.getCurrentUser();
UUID tenantId = user.getTenantId();
Authority authority = user.getAuthority();

// Option 2: @AuthenticationPrincipal annotation
@PostMapping("/my-endpoint")
public Map<String, Object> myEndpoint(@AuthenticationPrincipal TbSecurityUser user,
                                       ThingsboardClient tb) throws Exception {
    UUID tenantId = user.getTenantId();
}
```

`TbSecurityUser` returns typed values: `getAuthority()` returns `Authority` enum, ID getters return `UUID`.

Import: `org.thingsboard.extension.config.TbSecurity`, `org.thingsboard.extension.config.TbSecurityUser`

### File structure

**Project root:**
```
├── build-docker-image.sh                 # Build Docker image (self-contained usage docs inside)
├── publish-docker-image.sh               # Push image to a container registry
├── run.sh                                # Run with Maven (requires Java 25)
├── run-docker.sh                         # Run with Docker Compose
├── Dockerfile                            # Docker image (requires JAR built by build-docker-image.sh)
├── deploy/
│   ├── on-premise/
│   │   ├── docker-compose.yml            # Extension container config
│   │   ├── .env.example                  # Environment variable template
│   │   └── haproxy-extension.cfg.snippet # HAProxy routing for /api/extension/*
│   └── cloud/
│       ├── docker-compose.yml            # Extension container config (no extra_hosts)
│       └── .env.example                  # Env template (CORS_ALLOWED_ORIGINS required)
├── examples/widgets/
│   ├── on-premise-button.js              # Widget JS snippet (self.ctx.http, relative URL)
│   └── cloud-button.js                   # Widget JS snippet (fetch, full URL, manual JWT)
├── docs/
│   └── tb-message-types.md               # ThingsBoard message types and JSON payload structures
├── examples/                             # Maven module — example controllers (can delete the whole module)
│   └── src/main/java/org/thingsboard/extension/examples/
│       ├── BillingController.java            # API key auth pattern (rule chain callback)
│       ├── DeviceHealthCheckTask.java        # Scheduled background job
│       ├── TelemetryUnitConversionController.java  # No-auth pattern (no TB client needed)
│       └── TenantReportController.java       # JWT auth pattern (widget callback)
└── extension/                            # Maven module — Spring Boot app
    ├── src/main/java/org/thingsboard/extension/
    │   ├── ThingsboardExtensionApplication.java  # Spring Boot entry point + @EnableScheduling
    │   └── config/
    │       ├── GlobalExceptionHandler.java       # Structured JSON error responses
    │       ├── HealthController.java             # GET /api/health
    │       ├── OpenApiConfig.java                # Swagger UI with dual auth schemes
    │       ├── RequestLoggingFilter.java         # Request/response logging
    │       ├── SchedulingConfig.java             # Scheduler error handling
    │       ├── SecurityConfig.java               # Spring Security filter chain + CORS
    │       ├── TbAuthentication.java             # Spring Security Authentication (lazy authorities)
    │       ├── TbSecurity.java                    # Static helper: TbSecurity.getCurrentUser()
    │       ├── TbSecurityFilter.java             # Populates SecurityContext from X-Authorization
    │       ├── TbSecurityUser.java               # Lazily-loaded ThingsBoard user wrapper
    │       ├── ThingsboardAuthConfig.java        # Optional TB client bean for background jobs
    │       ├── ThingsboardClientProvider.java    # Client cache + argument resolver
    │       └── WebConfig.java                    # Registers the argument resolver
    └── target/api-docs/                  # Generated — ThingsboardClient API docs (run mvnw generate-resources -pl extension)
```

To remove the examples, delete the `examples/` directory and remove the `<module>examples</module>` line and the `thingsboard-extension-examples` dependency from the POM files.

New extensions go directly in `extension/src/main/java/org/thingsboard/extension/` or in a sub-package.

### Request/response contract
- **Input**: `msg.getData()` from the rule chain — whatever JSON the triggering event carries
- **Output**: any JSON — becomes the outgoing message in the rule chain
- **2xx** = Success route in rule chain
- **non-2xx** = Failure route in rule chain

## ThingsboardClient Bean

To call ThingsBoard APIs outside HTTP request context (e.g., from `@Scheduled` tasks or startup logic), inject the `ThingsboardClient` bean via constructor:

```java
@ConditionalOnBean(ThingsboardClient.class)
@Component
public class MyTask {
    private final ThingsboardClient tb;

    public MyTask(ThingsboardClient tb) {
        this.tb = tb;
    }
}
```

This bean is created only when authentication credentials are configured in `application.yml` (see `thingsboard.auth.*`). Use `@ConditionalOnBean(ThingsboardClient.class)` on components that depend on it — they'll be silently skipped when no credentials are set. Exceptions in scheduled tasks are logged by `SchedulingConfig`'s ErrorHandler — tasks continue on next trigger.

## API Reference

The full ThingsboardClient API docs are packaged inside the client JAR and extracted to `extension/target/api-docs/` during build. Run `./mvnw generate-resources -pl extension` if the folder doesn't exist.

**⚠️ NEVER search `~/.m2/repository`, decompile JARs, or use `find`/`jar`/`javap` commands to inspect client library internals. Everything you need is in `extension/target/api-docs/`.**

`extension/target/api-docs/` contains three kinds of documentation:

1. **Controller API docs** (`*ControllerApi.md`) — e.g., `DeviceControllerApi.md`, `TelemetryControllerApi.md`. Each lists all available methods with parameters and return types.
2. **Model class docs** (e.g., `EntitySubtype.md`, `Device.md`, `Alarm.md`) — each lists the model's properties, types, and getter/setter conventions. **When a method returns a type you're unfamiliar with, read that type's `.md` file in `extension/target/api-docs/`** to learn its properties and available getters.
3. **ThingsboardClient source** (`ThingsboardClient.java`) — the actual client class source code. Read this when you need to understand method signatures, overloads, or client behavior that isn't covered by the controller API docs. `ThingsboardClient` extends `ThingsboardApi` (the generated 98K-line class with all API methods) — the controller API docs already cover those methods, so you do NOT need the `ThingsboardApi` source.

**Important:** When you need to call ThingsBoard APIs, always **read the full method table** at the top of the relevant `*ControllerApi.md` file (it's typically under 20 lines). Do not grep for guessed method names — the actual method names may differ from what you'd expect (e.g., `getTenantAssetByName` not `getAssetsByName`).

For code examples showing how to call these methods, see `extension/target/api-docs/tb-examples.md`.

### Imports

All model classes live in `org.thingsboard.client.model`. The client itself is in `org.thingsboard.client`.

```java
import org.thingsboard.client.ThingsboardClient;
import org.thingsboard.client.ApiException;
import org.thingsboard.client.model.*;  // Device, Asset, Customer, Alarm, AttributeData, etc.
```

### Entity IDs

All entity IDs follow the same pattern: `entity.getId().getId().toString()`.

```java
Asset asset = tb.getTenantAssetByName("Building A");
String assetId = asset.getId().getId().toString();
```

### PageData fields

All `PageData*` types share the same structure:

```java
PageDataDevice page = tb.getTenantDevices(100, 0, null, null, null, null);
List<Device> items = page.getData();         // entities on this page
long totalElements = page.getTotalElements(); // total entity count
boolean hasNext    = page.getHasNext();      // more pages available?
```

### AttributeData.getValue() runtime types

| JSON value type | Java runtime type |
|----------------|-------------------|
| integer        | `Long`            |
| decimal        | `Double`          |
| string         | `String`          |
| boolean        | `Boolean`         |
| object / array | `Map` / `List`    |

Cast safely via `Number` for numeric values: `((Number) attr.getValue()).longValue()`.

### Quick reference

- **Entity type strings**: `"DEVICE"`, `"ASSET"`, `"CUSTOMER"`, `"TENANT"`, `"DASHBOARD"`, `"ALARM"`, `"USER"`, `"EDGE"`, `"ENTITY_VIEW"`, `"RULE_CHAIN"`, `"ENTITY_GROUP"`, `"ROLE"`, `"GROUP_PERMISSION"`
- **Attribute scopes**: `"SERVER_SCOPE"`, `"SHARED_SCOPE"`, `"CLIENT_SCOPE"`
- **Pagination**: Most list methods take `(pageSize, page, ...)`. Page is 0-indexed.
- **JSON body parameters**: Methods like `saveDeviceAttributes` take a `String body` — pass a JSON string.
- **All IDs are strings**: The Java client methods accept and return String IDs.
- **Error handling**: All methods throw `ApiException` with `getCode()` (HTTP status) and `getMessage()`.
- **Lookup by name**: Use `getTenantDeviceByName`, `getTenantAssetByName`, `getTenantCustomer` — no need to paginate and filter.
- **Entity group type strings** (PE/PaaS): `"DEVICE"`, `"ASSET"`, `"CUSTOMER"`, `"USER"`, `"DASHBOARD"`, `"ENTITY_VIEW"` — used with `getAllEntityGroupsByType` and `getEntityGroupByOwnerAndNameAndType`.

## How to Run

Guide users to the simplest option:

1. **`./run.sh`** — runs with Maven directly (requires Java 25).
2. **`./run-docker.sh`** — builds the JAR, then runs with Docker Compose.

Health check: `curl http://localhost:8090/api/health`

## Restarting After Code Changes

- **With devtools (default `./mvnw -pl extension -am spring-boot:run`):** Run `./mvnw compile -q` in a separate terminal — the service auto-restarts in ~2 seconds.
- **With Docker:** Run `./run-docker.sh`.

## Post-Generation Checklist

After generating extension code, verify:

1. `./mvnw compile -q` succeeds
2. Endpoint URL starts with `/api/extension/` and doesn't conflict with existing controllers (check `/api/extension/billing/*`, `/api/extension/transform/*`, `/api/extension/report/*`)
3. License header is present at the top of every new Java file
4. Provide a curl test command the user can run immediately
5. Provide setup instructions: rule chain wiring (with exact message type names from `docs/tb-message-types.md`), or widget JS snippet setup, or scheduled task env vars — whichever applies per step 6
6. Propose deployment next steps — users won't read the README carefully, so surface these directly:
   - Point to `./build-docker-image.sh` and `./publish-docker-image.sh` for building and publishing a Docker image
   - For on-premise: mention `deploy/on-premise/docker-compose.yml` and the HAProxy snippet at `deploy/on-premise/haproxy-extension.cfg.snippet` (must go **before** the existing ThingsBoard ACL)
   - For cloud: mention `deploy/cloud/docker-compose.yml` and emphasize that `CORS_ALLOWED_ORIGINS` must be set
