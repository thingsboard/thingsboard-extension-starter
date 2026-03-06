# CLAUDE.md

## What This Is

A Spring Boot service that receives HTTP callbacks from ThingsBoard rule chains and optionally calls ThingsBoard APIs back. Each extension is a `@RestController` endpoint. The ThingsBoard API key travels with each request in the `X-TB-API-Key` header, so the service is stateless and multi-tenant.

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

Then ask these questions (the user can skip by providing a detailed prompt upfront):

- **What event triggers it?** (device created, telemetry posted, alarm created, etc.) — see `docs/tb-message-types.md` for the full list of ThingsBoard message types and their JSON payloads.
- **Does it need to call ThingsBoard APIs?** (save attributes, look up devices, create alarms, etc.) — see the API docs in `target/api-docs/` (run `mvn generate-resources` first if the folder doesn't exist). Each `*Api.md` file lists all available methods for that controller with parameters and return types.
- **Does it need external services?** (Slack, email, database, HTTP API) — if so, add the dependency to `pom.xml`.
- **What should it return?** The response JSON becomes the outgoing message in the rule chain (2xx = Success route, non-2xx = Failure route).

### 2. Generate the code

Create a new `@RestController` class in `src/main/java/org/thingsboard/extension/`:

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

### 3. Provide rule chain setup instructions

After generating the code, tell the user exactly how to wire it in ThingsBoard:

1. Open ThingsBoard → Rule Chains → your rule chain
2. Add a **REST API Call** node with:
   - Method: `POST`
   - URL: `http://localhost:8090/api/your-feature/on-some-event`
   - Headers: `Content-Type: application/json` and `X-TB-API-Key: YOUR_API_KEY`
   - Credentials: Anonymous
3. Connect the triggering node to this REST API Call node
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

For common usage patterns and code examples, see `docs/tb-client-api.md`.
