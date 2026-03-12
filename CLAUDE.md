# CLAUDE.md

## What This Is

A Spring Boot service that extends ThingsBoard with custom logic. It supports two execution models:

1. **HTTP callbacks** (rule chains, widgets) -- each request carries an `X-Authorization` header (`Bearer <jwt>` or `ApiKey <key>`), and the service resolves a `ThingsboardClient` per caller. Each extension is a `@RestController` endpoint.
2. **Scheduled background jobs** -- preconfigured credentials in `application.yml` provide a long-lived `ThingsboardClient` singleton for tasks that run on a schedule without any incoming HTTP request.

## Important: Know When to Ask vs. When to Figure It Out

The user of this project is most likely **not a developer** — they understand their ThingsBoard setup and business needs, but not Java, Spring, or programming internals.

- **Business logic questions → always ask the user** (using `AskUserQuestion`). Never guess what the user wants. If you're unsure about the intended behavior, what data to use, which entities are involved, how something should be triggered, or what the output should look like — ask. Examples: "Should deleted devices decrement the count or reset it?", "Which asset should store this data?", "Should this run on every telemetry message or only when a threshold is exceeded?"

- **Technical/implementation questions → figure it out yourself.** Do not ask the user about Java imports, Spring annotations, method signatures, build errors, or ThingsBoard client API usage. Use the API docs in `target/api-docs/`, the examples in `target/api-docs/tb-examples.md`, and `./mvnw compile` to resolve technical issues on your own.

## How to Create a New Extension

**⚠️ MANDATORY — do NOT skip any of these steps. Do NOT jump ahead to writing code.**

When a user asks to create an extension, you MUST follow every step below **in order**. Do not generate any code until steps 1 and 2 are fully complete.

### 1. Ask the edition (BLOCKING — must happen first)

Before doing ANYTHING else, ask the user which ThingsBoard edition they're targeting. Use `AskUserQuestion` with these options:

- **CE** — Community Edition (open-source)
- **PE** — Professional Edition (licensed, extra features)
- **PaaS** — ThingsBoard Cloud

Then update the `thingsboard-client.artifactId` property in `pom.xml`:

| Edition | Property value |
|---------|---------------|
| CE | `thingsboard-ce-client` |
| PE | `thingsboard-pe-client` |
| PaaS | `thingsboard-paas-client` |

This single property controls both the dependency and the API docs extraction.

### 2. Set up API docs (BLOCKING — must happen before writing any code)

After setting the edition in `pom.xml`, run `./mvnw generate-resources -q` — this unpacks API docs from the client JAR into `target/api-docs/`. It always overwrites, so it's safe to re-run after switching editions.

**Do NOT read any files from `target/api-docs/` until AFTER running `./mvnw generate-resources`.** Always regenerate first, then read.

**Do NOT skip this step.** Even if `target/api-docs/` already exists, it may contain docs for the wrong edition.

### 3. Understand the business need

Ask these questions (the user can skip by providing a detailed prompt upfront):

- **What event triggers it?** (device created, telemetry posted, alarm created, etc.) — then **read `docs/tb-message-types.md`** to find the exact message type (e.g., `ENTITY_CREATED`) and understand the JSON payload structure. You will need both when generating code and when writing rule chain setup instructions.
- **Does it need to call ThingsBoard APIs?** (save attributes, look up devices, create alarms, etc.) — see the API docs in `target/api-docs/` (run `./mvnw generate-resources` first if the folder doesn't exist). Each `*Api.md` file lists all available methods for that controller with parameters and return types.
- **Does it need external services?** (Slack, email, database, HTTP API) — if so, add the dependency to `pom.xml`.
- **What should it return?** The response JSON becomes the outgoing message in the rule chain (2xx = Success route, non-2xx = Failure route).

### 4. Generate the code

Create a new `@RestController` class in `src/main/java/org/thingsboard/extension/`. Every Java file must start with this exact license header:

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
@RequestMapping("/api/your-feature")
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

- If ThingsBoard API calls are needed: declare `ThingsboardClient tb` as a parameter -- it's auto-resolved from the `X-Authorization` header.
- If no ThingsBoard API calls are needed: simply omit the `ThingsboardClient` parameter.
- If new dependencies are needed: add them to `pom.xml`.
- If the input/output JSON is complex: create POJO classes next to the controller.

Scheduled task template (for background jobs that run on a timer):

```java
@Component
public class YourScheduledTask {
    private final ThingsboardClient tb;

    public YourScheduledTask(@Qualifier("preconfiguredTbClient") ThingsboardClient tb) {
        this.tb = tb;
    }

    @Scheduled(fixedRate = 60, timeUnit = TimeUnit.SECONDS)
    public void run() {
        // use tb to call ThingsBoard APIs on a schedule
    }
}
```

- The preconfigured client is injected via `@Qualifier("preconfiguredTbClient")` -- it uses credentials from `application.yml`.
- Do NOT wrap `@Scheduled` methods in try-catch -- the global `ErrorHandler` in `SchedulingConfig` handles exceptions.

### 5. Verify the code compiles

Run `./mvnw compile -q` after generating the code. If it fails, read the error output and fix the issues before proceeding. This catches wrong imports, missing types, and API mismatches immediately.

### 6. Provide setup instructions

**For controller extensions (HTTP callbacks):** Tell the user how to wire it in ThingsBoard. Use the exact message type names from `docs/tb-message-types.md` (e.g., `ENTITY_CREATED`, `POST_TELEMETRY_REQUEST`) -- do not guess or paraphrase them.

1. Open ThingsBoard -> Rule Chains -> your rule chain
2. Add a **REST API Call** node with:
   - Method: `POST`
   - URL: `http://localhost:8090/api/your-feature/on-some-event`
   - Headers: `Content-Type: application/json` and `X-Authorization: ApiKey YOUR_API_KEY`
   - Credentials: Anonymous
3. Connect the triggering node to this REST API Call node (specify the exact message type to filter on)
4. The response JSON goes to the **Success** route (2xx) or **Failure** route (non-2xx)

**For scheduled tasks:** No rule chain wiring needed. Instead, ensure the user has configured the preconfigured credentials in `application.yml` (or via environment variables `TB_PRECONFIGURED_API_KEY` or `TB_PRECONFIGURED_USERNAME` + `TB_PRECONFIGURED_PASSWORD`). The task runs automatically on the configured schedule.

## Project Conventions

### Authentication flows

Three ways to get a `ThingsboardClient`:

- **Request-based (API key)**: Rule chain sends `X-Authorization: ApiKey <key>` header. `ThingsboardClientProvider` resolves a cached client. Declare `ThingsboardClient tb` as a controller method parameter.
- **Request-based (JWT)**: Widget sends `X-Authorization: Bearer <jwt>` header. `ThingsboardClientProvider` resolves a cached client. Same parameter injection as API key.
- **Preconfigured (background jobs)**: Credentials in `application.yml` (`thingsboard.preconfigured.*`). Inject via constructor: `@Qualifier("preconfiguredTbClient") ThingsboardClient tb`. Used for scheduled tasks and startup logic -- no HTTP request needed.

For request-based flows, missing or invalid `X-Authorization` header returns 401 Unauthorized.

### File structure
```
src/main/java/org/thingsboard/extension/
├── ThingsboardExtensionApplication.java  # Spring Boot entry point + @EnableScheduling
├── config/
│   ├── GlobalExceptionHandler.java       # Structured JSON error responses
│   ├── HealthController.java             # GET /api/health
│   ├── OpenApiConfig.java                # Swagger UI with dual auth schemes
│   ├── PreconfiguredClientConfig.java    # Preconfigured TB client bean
│   ├── RequestLoggingFilter.java         # Request/response logging
│   ├── SchedulingConfig.java             # Scheduler error handling
│   ├── ThingsboardClientProvider.java    # Client cache + argument resolver
│   └── WebConfig.java                    # Registers the argument resolver
└── examples/                             # Example controllers (can be deleted)
    ├── BillingController.java
    └── UsageTrackingController.java
```

New extensions go directly in `src/main/java/org/thingsboard/extension/` or in a sub-package.

### Request/response contract
- **Input**: `msg.getData()` from the rule chain — whatever JSON the triggering event carries
- **Output**: any JSON — becomes the outgoing message in the rule chain
- **2xx** = Success route in rule chain
- **non-2xx** = Failure route in rule chain

## Scheduled Tasks

### How scheduling works

`@EnableScheduling` on the application class activates `@Scheduled` annotation processing. The project uses virtual threads (`spring.threads.virtual.enabled=true`), so Spring Boot auto-configures `SimpleAsyncTaskScheduler` -- each scheduled task runs on its own virtual thread.

### Error handling

A custom `ErrorHandler` in `SchedulingConfig` logs exceptions at ERROR level with the full stack trace. Tasks continue running on the next trigger after a failure. Do NOT add try-catch in `@Scheduled` methods unless you need custom recovery logic (e.g., retry with backoff, compensating action).

### Scheduling patterns

```java
// Fixed rate: runs every 60 seconds regardless of previous execution time
@Scheduled(fixedRate = 60, timeUnit = TimeUnit.SECONDS)
public void everyMinute() { /* ... */ }

// Cron: runs at 2:00 AM daily
@Scheduled(cron = "0 0 2 * * *")
public void dailyAt2am() { /* ... */ }

// Initial delay + fixed rate: wait 30s after startup, then every 5 minutes
@Scheduled(initialDelay = 30, fixedRate = 300, timeUnit = TimeUnit.SECONDS)
public void withDelay() { /* ... */ }
```

### Injecting the preconfigured client

Scheduled tasks run outside HTTP request context, so they cannot use the argument resolver. Instead, inject the preconfigured `ThingsboardClient` bean via constructor:

```java
@Component
public class MyScheduledTask {
    private final ThingsboardClient tb;

    public MyScheduledTask(@Qualifier("preconfiguredTbClient") ThingsboardClient tb) {
        this.tb = tb;
    }

    @Scheduled(fixedRate = 60, timeUnit = TimeUnit.SECONDS)
    public void run() {
        // use tb to call ThingsBoard APIs
    }
}
```

### Important notes

- Use `fixedRate` or `cron` for independent tasks. Avoid `fixedDelay` unless sequential execution is intentional -- `fixedDelay` tasks share a single scheduler thread and block each other.
- The preconfigured client is a singleton -- do NOT create `ThingsboardClient` inside `@Scheduled` methods. That wastes a login round-trip every invocation.
- Preconfigured client = one fixed identity (the configured tenant). For per-request auth, use the argument resolver (declare `ThingsboardClient` as a controller method parameter).

## API Reference

The full ThingsboardClient API docs are packaged inside the client JAR and extracted to `target/api-docs/` during build. Run `./mvnw generate-resources` if the folder doesn't exist.

Each `*Api.md` file (e.g., `DeviceControllerApi.md`, `TelemetryControllerApi.md`) lists all available methods with parameters and return types. **Always consult these docs when generating extension code** to ensure you use methods that actually exist.

**Important:** When you need to call ThingsBoard APIs, always **read the full method table** at the top of the relevant `*ControllerApi.md` file (it's typically under 20 lines). Do not grep for guessed method names — the actual method names may differ from what you'd expect (e.g., `getTenantAssetByName` not `getAssetsByName`).

For code examples showing how to call these methods, see `target/api-docs/tb-examples.md`.

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

- **With devtools (default `./mvnw spring-boot:run`):** Run `./mvnw compile -q` in a separate terminal — the service auto-restarts in ~2 seconds.
- **With Docker:** Run `./run-docker.sh`.

## Post-Generation Checklist

After generating extension code, verify:

1. `./mvnw compile -q` succeeds
2. Endpoint URL doesn't conflict with existing controllers (check `/api/health`, `/api/billing/*`, `/api/usage/*`)
3. License header is present at the top of every new Java file
4. Provide a curl test command the user can run immediately
5. Provide rule chain wiring instructions with exact message type names from `docs/tb-message-types.md`
6. If creating a scheduled task, verify `TB_PRECONFIGURED_*` env vars are documented in setup instructions
