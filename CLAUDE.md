# CLAUDE.md

## What This Is

A Spring Boot service that receives HTTP callbacks from ThingsBoard rule chains and optionally calls ThingsBoard APIs back. Each extension is a `@RestController` endpoint. The ThingsBoard API key travels with each request in the `X-TB-API-Key` header, so the service is stateless and multi-tenant.

## Important: Know When to Ask vs. When to Figure It Out

The user of this project is most likely **not a developer** — they understand their ThingsBoard setup and business needs, but not Java, Spring, or programming internals.

- **Business logic questions → always ask the user** (using `AskUserQuestion`). Never guess what the user wants. If you're unsure about the intended behavior, what data to use, which entities are involved, how something should be triggered, or what the output should look like — ask. Examples: "Should deleted devices decrement the count or reset it?", "Which asset should store this data?", "Should this run on every telemetry message or only when a threshold is exceeded?"

- **Technical/implementation questions → figure it out yourself.** Do not ask the user about Java imports, Spring annotations, method signatures, build errors, or ThingsBoard client API usage. Use the API docs in `target/api-docs/`, the examples in `docs/tb-examples.md`, and `mvn compile` to resolve technical issues on your own.

## How to Create a New Extension

When a user asks to create an extension, follow this process:

### 1. Understand the business need

First, ask the user which ThingsBoard edition they're targeting. Use `AskUserQuestion` with these options:

- **CE** — Community Edition (open-source)
- **PE** — Professional Edition (licensed, extra features)
- **PaaS** — ThingsBoard Cloud

Then update the `thingsboard-client.artifactId` property in `pom.xml`:

| Edition | Property value |
|---------|---------------|
| CE | `thingsboard-ce-client` |
| PE | `thingsboard-pe-client` |
| PaaS | `thingsboard-paas-client` |

This single property controls both the dependency and the API docs extraction. After changing it, run `mvn generate-resources` to unpack the correct edition's API docs into `target/api-docs/`.

**Important:** Do NOT read any files from `target/api-docs/` until AFTER you have set the correct edition and run `mvn generate-resources`. The folder may already exist with docs from a different edition. Always set the edition first, regenerate, then read.

Then ask these questions (the user can skip by providing a detailed prompt upfront):

- **What event triggers it?** (device created, telemetry posted, alarm created, etc.) — then **read `docs/tb-message-types.md`** to find the exact message type (e.g., `ENTITY_CREATED`) and understand the JSON payload structure. You will need both when generating code and when writing rule chain setup instructions.
- **Does it need to call ThingsBoard APIs?** (save attributes, look up devices, create alarms, etc.) — see the API docs in `target/api-docs/` (run `mvn generate-resources` first if the folder doesn't exist). Each `*Api.md` file lists all available methods for that controller with parameters and return types.
- **Does it need external services?** (Slack, email, database, HTTP API) — if so, add the dependency to `pom.xml`.
- **What should it return?** The response JSON becomes the outgoing message in the rule chain (2xx = Success route, non-2xx = Failure route).

### 2. Generate the code

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

- If ThingsBoard API calls are needed: declare `ThingsboardClient tb` as a parameter — it's auto-resolved from the `X-TB-API-Key` header.
- If no ThingsBoard API calls are needed: simply omit the `ThingsboardClient` parameter.
- If new dependencies are needed: add them to `pom.xml`.
- If the input/output JSON is complex: create POJO classes next to the controller.

### 3. Verify the code compiles

Run `mvn compile -q` after generating the code. If it fails, read the error output and fix the issues before proceeding. This catches wrong imports, missing types, and API mismatches immediately.

### 4. Provide rule chain setup instructions

After generating the code, tell the user exactly how to wire it in ThingsBoard. Use the exact message type names from `docs/tb-message-types.md` (e.g., `ENTITY_CREATED`, `POST_TELEMETRY_REQUEST`) — do not guess or paraphrase them.

1. Open ThingsBoard → Rule Chains → your rule chain
2. Add a **REST API Call** node with:
   - Method: `POST`
   - URL: `http://localhost:8090/api/your-feature/on-some-event`
   - Headers: `Content-Type: application/json` and `X-TB-API-Key: YOUR_API_KEY`
   - Credentials: Anonymous
3. Connect the triggering node to this REST API Call node (specify the exact message type to filter on)
4. The response JSON goes to the **Success** route (2xx) or **Failure** route (non-2xx)

## Project Conventions

### API key flow
- ThingsBoard REST API Call node sends `X-TB-API-Key` header with every request
- `ThingsboardClientProvider` reads the header and creates/caches a `ThingsboardClient` per API key
- Controller methods that declare a `ThingsboardClient` parameter get it auto-injected
- Missing header → 401 Unauthorized

### File structure
```
src/main/java/org/thingsboard/extension/
├── ExtensionApplication.java          # Spring Boot entry point
├── config/
│   ├── ThingsboardClientProvider.java  # Client cache + argument resolver
│   └── WebConfig.java                 # Registers the argument resolver
└── examples/                          # Example controllers (can be deleted)
    ├── BillingController.java
    └── UsageTrackingController.java
```

New extensions go directly in `src/main/java/org/thingsboard/extension/` or in a sub-package.

### Request/response contract
- **Input**: `msg.getData()` from the rule chain — whatever JSON the triggering event carries
- **Output**: any JSON — becomes the outgoing message in the rule chain
- **2xx** = Success route in rule chain
- **non-2xx** = Failure route in rule chain

## API Reference

The full ThingsboardClient API docs are packaged inside the client JAR and extracted to `target/api-docs/` during build. Run `mvn generate-resources` if the folder doesn't exist.

Each `*Api.md` file (e.g., `DeviceControllerApi.md`, `TelemetryControllerApi.md`) lists all available methods with parameters and return types. **Always consult these docs when generating extension code** to ensure you use methods that actually exist.

**Important:** When you need to call ThingsBoard APIs, always **read the full method table** at the top of the relevant `*ControllerApi.md` file (it's typically under 20 lines). Do not grep for guessed method names — the actual method names may differ from what you'd expect (e.g., `getTenantAssetByName` not `getAssetsByName`).

For code examples showing how to call these methods, see `docs/tb-examples.md`.

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

- **Entity type strings**: `"DEVICE"`, `"ASSET"`, `"CUSTOMER"`, `"TENANT"`, `"DASHBOARD"`, `"ALARM"`, `"USER"`, `"EDGE"`, `"ENTITY_VIEW"`, `"RULE_CHAIN"`
- **Attribute scopes**: `"SERVER_SCOPE"`, `"SHARED_SCOPE"`, `"CLIENT_SCOPE"`
- **Pagination**: Most list methods take `(pageSize, page, ...)`. Page is 0-indexed.
- **JSON body parameters**: Methods like `saveDeviceAttributes` take a `String body` — pass a JSON string.
- **All IDs are strings**: The Java client methods accept and return String IDs.
- **Error handling**: All methods throw `ApiException` with `getCode()` (HTTP status) and `getMessage()`.
- **Lookup by name**: Use `getTenantDeviceByName`, `getTenantAssetByName`, `getTenantCustomer` — no need to paginate and filter.
