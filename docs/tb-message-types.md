# ThingsBoard Message Types Reference

## Introduction

When something happens in ThingsBoard (a device sends telemetry, an entity is created, an alarm fires, etc.), the platform creates a **rule engine message** and pushes it into the rule chain. Each message has four parts:

| Part | Description |
|------|-------------|
| **Originator** | The entity ID that caused the event (e.g., the device that sent telemetry, the entity that was created) |
| **Message Type** | A string constant from `TbMsgType` that identifies the event kind |
| **Data** | JSON payload (the POST body if you use a REST API Call node) |
| **Metadata** | Key-value string pairs with contextual information |

By the time your extension receives the message, the underlying action (create, update, delete, etc.) has already been committed to the database.

---

## Common Metadata Fields

### Entity Action Events (UI / API operations)

These metadata keys are present on all entity lifecycle and assignment events:

| Key | Description | Always present? |
|-----|-------------|-----------------|
| `userId` | UUID of the user who performed the action | Yes (if user context exists) |
| `userName` | Login name (email) of the user | Yes (if user context exists) |
| `userEmail` | Email of the user | Yes (if user context exists) |
| `userFirstName` | First name of the user | Only if set on user |
| `userLastName` | Last name of the user | Only if set on user |
| `entityName` | Name of the entity | Yes (if entity is not null) |
| `entityType` | Entity type string, e.g. `DEVICE`, `ASSET` | Yes (if entity is not null) |
| `customerId` | UUID of the customer, if the entity belongs to one | Only if non-null |

### Device Transport Events (connectivity / state)

These metadata keys are present on connect, disconnect, activity, and inactivity events:

| Key | Description |
|-----|-------------|
| `deviceName` | Device name |
| `deviceLabel` | Device label |
| `deviceType` | Device type string |
| `scope` | `SERVER_SCOPE` (present when state is persisted to attributes, not telemetry) |

---

## Device Telemetry & Attributes

### `POST_TELEMETRY_REQUEST`

**When:** Device sends telemetry data via transport (MQTT, HTTP, CoAP, etc.) or integration.
**Originator:** Device ID.

**Data:**
```json
{
  "temperature": 25.5,
  "humidity": 60
}
```

**Metadata:**

| Key | Description |
|-----|-------------|
| `deviceName` | Device name |
| `deviceType` | Device type |
| `ts` | Timestamp (ms) assigned by the platform or provided by the device |

---

### `POST_ATTRIBUTES_REQUEST`

**When:** Device sends client-side attributes via transport (MQTT, HTTP, CoAP, etc.) or integration.
**Originator:** Device ID.

**Data:**
```json
{
  "firmware_version": "1.2.3",
  "serial_number": "SN-001"
}
```

**Metadata:**

| Key | Description |
|-----|-------------|
| `deviceName` | Device name |
| `deviceType` | Device type |

---

### `ATTRIBUTES_UPDATED`

**When:** Server-side or shared attributes are updated via REST API or rule chain.
**Originator:** Entity ID (usually a device).

**Data:**
```json
{
  "targetFirmwareVersion": "2.0.0",
  "configParam": 42
}
```

**Metadata (entity action keys plus):**

| Key | Description |
|-----|-------------|
| `scope` | `SERVER_SCOPE` or `SHARED_SCOPE` |

---

### `ATTRIBUTES_DELETED`

**When:** Attributes are deleted via REST API.
**Originator:** Entity ID.

**Data:**
```json
{
  "attributes": ["attribute1", "attribute2"]
}
```

**Metadata (entity action keys plus):**

| Key | Description |
|-----|-------------|
| `scope` | `SERVER_SCOPE` or `SHARED_SCOPE` |

---

### `TIMESERIES_UPDATED`

**When:** Timeseries data is saved via REST API (not from device transport -- transport uses `POST_TELEMETRY_REQUEST`).
**Originator:** Entity ID.

**Data** (entries grouped by timestamp):
```json
{
  "timeseries": [
    {
      "ts": 1609459200000,
      "values": {
        "temperature": 25.5,
        "humidity": 60
      }
    },
    {
      "ts": 1609459260000,
      "values": {
        "temperature": 26.0
      }
    }
  ]
}
```

---

### `TIMESERIES_DELETED`

**When:** Timeseries data is deleted via REST API.
**Originator:** Entity ID.

**Data:**
```json
{
  "timeseries": ["temperature", "humidity"],
  "startTs": 1609459200000,
  "endTs": 1609545600000
}
```

---

## Device Connectivity Events

The originator is the **Device ID**. Metadata contains `deviceName`, `deviceLabel`, `deviceType`, and optionally `scope`.

### `CONNECT_EVENT`

**When:** Device connects to the platform (transport session established).

**Data** (DeviceState JSON, **without** `active` field):
```json
{
  "lastConnectTime": 1609459200000,
  "lastActivityTime": 1609459100000,
  "lastDisconnectTime": 1609458000000,
  "lastInactivityAlarmTime": 0,
  "inactivityTimeout": 600000
}
```

### `DISCONNECT_EVENT`

**When:** Device disconnects from the platform (transport session closed).

**Data** (full DeviceState JSON):
```json
{
  "active": true,
  "lastConnectTime": 1609459200000,
  "lastActivityTime": 1609459200000,
  "lastDisconnectTime": 1609459500000,
  "lastInactivityAlarmTime": 0,
  "inactivityTimeout": 600000
}
```

### `ACTIVITY_EVENT`

**When:** Device becomes active (first connect or data received after a period of inactivity).

**Data** (full DeviceState JSON, `active` = `true`):
```json
{
  "active": true,
  "lastConnectTime": 1609459200000,
  "lastActivityTime": 1609459200000,
  "lastDisconnectTime": 1609458000000,
  "lastInactivityAlarmTime": 0,
  "inactivityTimeout": 600000
}
```

### `INACTIVITY_EVENT`

**When:** Device becomes inactive (no data received within configured inactivity timeout).

**Data** (full DeviceState JSON, `active` = `false`):
```json
{
  "active": false,
  "lastConnectTime": 1609459200000,
  "lastActivityTime": 1609459200000,
  "lastDisconnectTime": 1609459500000,
  "lastInactivityAlarmTime": 1609460400000,
  "inactivityTimeout": 600000
}
```

---

## Entity Lifecycle Events

### `ENTITY_CREATED`

**When:** An entity is created via UI, REST API, or provisioning.
**Originator:** The newly created entity's ID.
**Data:** Full entity JSON (serialized via Jackson).

**Example (Device):**
```json
{
  "id": {"entityType": "DEVICE", "id": "784f394c-42b6-11e7-9926-dea266d719d9"},
  "createdTime": 1609459200000,
  "tenantId": {"entityType": "TENANT", "id": "..."},
  "customerId": {"entityType": "CUSTOMER", "id": "..."},
  "name": "Temperature Sensor A1",
  "type": "default",
  "label": "Rooftop Sensor",
  "deviceProfileId": {"entityType": "DEVICE_PROFILE", "id": "..."},
  "additionalInfo": {}
}
```

**Metadata:** Common entity action keys (`userId`, `userName`, `userEmail`, `entityName`, `entityType`, etc.)

### `ENTITY_UPDATED`

**When:** An entity is updated.
**Data:** Same full entity JSON with updated fields.

### `ENTITY_DELETED`

**When:** An entity is deleted.
**Data:** Full entity JSON as it was before deletion.

### Entity Types That Generate Lifecycle Events

The following entity types generate `ENTITY_CREATED`, `ENTITY_UPDATED`, and `ENTITY_DELETED` messages. The `entityType` metadata value and the `id.entityType` in the data payload will be the corresponding `EntityType` enum value.

**CE (Community Edition):**
- `DEVICE`, `DEVICE_PROFILE`, `ASSET`, `ASSET_PROFILE`, `CUSTOMER`, `USER`, `DASHBOARD`, `ENTITY_VIEW`, `EDGE`, `OTA_PACKAGE`

**PE (Professional Edition) -- additional:**
- `ENTITY_GROUP`, `ROLE`, `GROUP_PERMISSION`, `INTEGRATION`, `CONVERTER`, `SCHEDULER_EVENT`, `SECRET`, `OAUTH2_CLIENT`, `DOMAIN`, `AI_MODEL`

> **Note:** This list covers currently known entity types. New entity types may be added in future ThingsBoard releases and will follow the same `ENTITY_CREATED` / `ENTITY_UPDATED` / `ENTITY_DELETED` pattern with the same metadata structure.

> **Note:** For `DASHBOARD`, the `configuration` field is cleared to an empty string `""` to avoid sending large payloads through the rule engine.

---

## Entity Assignment Events

All assignment events include the common entity action metadata keys plus event-specific metadata listed below. Data is the full entity JSON.

### `ENTITY_ASSIGNED`

**When:** Entity is assigned to a customer.

**Additional metadata:**

| Key | Value |
|-----|-------|
| `assignedCustomerId` | UUID of the customer |
| `assignedCustomerName` | Name of the customer |

**Example metadata (merged with common keys):**
```json
{
  "userId": "a1b2c3d4-...",
  "userName": "admin@example.com",
  "userEmail": "admin@example.com",
  "entityName": "Temperature Sensor A1",
  "entityType": "DEVICE",
  "assignedCustomerId": "e5f6a7b8-...",
  "assignedCustomerName": "Acme Corp"
}
```

### `ENTITY_UNASSIGNED`

**When:** Entity is unassigned from a customer.

**Additional metadata:**

| Key | Value |
|-----|-------|
| `unassignedCustomerId` | UUID of the customer |
| `unassignedCustomerName` | Name of the customer |

### `ENTITY_ASSIGNED_TO_EDGE`

**When:** Entity is assigned to an Edge instance.

**Additional metadata:**

| Key | Value |
|-----|-------|
| `assignedEdgeId` | UUID of the edge |
| `assignedEdgeName` | Name of the edge |

### `ENTITY_UNASSIGNED_FROM_EDGE`

**When:** Entity is unassigned from an Edge instance.

**Additional metadata:**

| Key | Value |
|-----|-------|
| `unassignedEdgeId` | UUID of the edge |
| `unassignedEdgeName` | Name of the edge |

### `ENTITY_ASSIGNED_FROM_TENANT`

**When:** Entity is assigned from another tenant (cross-tenant transfer, incoming side).

**Additional metadata:**

| Key | Value |
|-----|-------|
| `assignedFromTenantId` | UUID of the source tenant |
| `assignedFromTenantName` | Name of the source tenant |

### `ENTITY_ASSIGNED_TO_TENANT`

**When:** Entity is assigned to another tenant (cross-tenant transfer, outgoing side).

**Additional metadata:**

| Key | Value |
|-----|-------|
| `assignedToTenantId` | UUID of the target tenant |
| `assignedToTenantName` | Name of the target tenant |

### `OWNER_CHANGED` (PE)

**When:** Entity ownership is changed (e.g., from tenant to customer, or between customers).

**Additional metadata:**

| Key | Value |
|-----|-------|
| `targetOwnerId` | UUID of the new owner |
| `targetOwnerType` | Entity type of the new owner (`TENANT` or `CUSTOMER`) |

### `ADDED_TO_ENTITY_GROUP` (PE)

**When:** Entity is added to an entity group.

**Additional metadata:**

| Key | Value |
|-----|-------|
| `addedToEntityGroupId` | UUID of the entity group |
| `addedToEntityGroupName` | Name of the entity group |

### `REMOVED_FROM_ENTITY_GROUP` (PE)

**When:** Entity is removed from an entity group.

**Additional metadata:**

| Key | Value |
|-----|-------|
| `removedFromEntityGroupId` | UUID of the entity group |
| `removedFromEntityGroupName` | Name of the entity group |

---

## Relation Events

### `RELATION_ADD_OR_UPDATE`

**When:** A relation is created or updated between two entities.
**Originator:** The entity on whose behalf the action was performed (typically the `from` entity).

**Data:**
```json
{
  "from": {"entityType": "ASSET", "id": "..."},
  "to": {"entityType": "DEVICE", "id": "..."},
  "type": "Contains",
  "typeGroup": "COMMON",
  "additionalInfo": {}
}
```

### `RELATION_DELETED`

**When:** A specific relation is deleted.
**Data:** Same structure as `RELATION_ADD_OR_UPDATE`.

### `RELATIONS_DELETED`

**When:** All relations for an entity are deleted (bulk delete).
**Originator:** The entity whose relations were deleted.
**Data:** Empty object `{}` (no relation-specific data since all were removed).

---

## Alarm Events

Alarm messages come from two distinct sources. Understanding this distinction is critical.

### Source 1: Rule Engine Alarm Nodes (Device Profile alarm rules, Create/Clear Alarm nodes)

These produce a single message type `ALARM` with metadata flags indicating what happened. The message is routed through the rule chain via **relation type** labels.

**Message type:** `ALARM`
**Originator:** The alarm originator entity (e.g., the device).

**Data:**
```json
{
  "id": {"entityType": "ALARM", "id": "a1b2c3d4-..."},
  "createdTime": 1609459200000,
  "tenantId": {"entityType": "TENANT", "id": "..."},
  "type": "High Temperature",
  "originator": {"entityType": "DEVICE", "id": "..."},
  "severity": "CRITICAL",
  "acknowledged": false,
  "cleared": false,
  "startTs": 1609459200000,
  "endTs": 1609459200000,
  "details": {"temperature": 95.5, "threshold": 80.0}
}
```

**Metadata flags (exactly one set of flags per message):**

| Scenario | Metadata flags | Relation type |
|----------|---------------|---------------|
| New alarm created | `isNewAlarm` = `true` | `Alarm Created` |
| Existing alarm updated (same severity) | `isExistingAlarm` = `true` | `Alarm Updated` |
| Severity changed | `isExistingAlarm` = `true`, `isSeverityUpdated` = `true` | `Alarm Severity Updated` |
| Alarm cleared | `isClearedAlarm` = `true` | `Alarm Cleared` |

### Source 2: User Actions (via UI / REST API)

When a user acknowledges, clears, assigns, or deletes an alarm through the API, the platform generates **distinct message types** (not the generic `ALARM` type):

- **`ALARM_ACK`** -- User acknowledged an alarm
- **`ALARM_CLEAR`** -- User cleared an alarm
- **`ALARM_DELETE`** -- User deleted an alarm
- **`ALARM_ASSIGNED`** -- User assigned an alarm to someone
- **`ALARM_UNASSIGNED`** -- User unassigned an alarm

**Data:** Full Alarm JSON (same structure as above).
**Metadata:** Common entity action keys (`userId`, `userName`, `userEmail`, etc.).
**Originator:** The alarm ID.

**Example (`ALARM_ACK`):**
```json
{
  "id": {"entityType": "ALARM", "id": "a1b2c3d4-..."},
  "createdTime": 1609459200000,
  "tenantId": {"entityType": "TENANT", "id": "..."},
  "type": "High Temperature",
  "originator": {"entityType": "DEVICE", "id": "..."},
  "severity": "CRITICAL",
  "acknowledged": true,
  "cleared": false,
  "startTs": 1609459200000,
  "endTs": 1609459200000,
  "details": {"temperature": 95.5, "threshold": 80.0}
}
```

---

## Alarm Comment Events

### `COMMENT_CREATED`

**When:** A user adds a comment to an alarm.
**Originator:** The alarm ID.
**Data:** Full Alarm JSON.

**Additional metadata:**

| Key | Value |
|-----|-------|
| `comment` | JSON string of the `AlarmComment` object |

### `COMMENT_UPDATED`

**When:** A user edits an existing alarm comment.
**Data:** Full Alarm JSON.
**Metadata:** Same as `COMMENT_CREATED`.

---

## RPC Events

### `TO_SERVER_RPC_REQUEST`

**When:** Device sends an RPC request to the server via transport.
**Originator:** Device ID.

**Data:**
```json
{
  "method": "getServerTime",
  "params": {}
}
```

### `RPC_CALL_FROM_SERVER_TO_DEVICE`

**When:** Server sends an RPC command to a device (triggered by rule chain or REST API).
**Originator:** Device ID.

**Data:**
```json
{
  "method": "setGpio",
  "params": {"pin": 4, "value": 1}
}
```

### Persistent RPC Lifecycle Events

When a **persistent** (two-way) RPC is used, the platform tracks the RPC through its lifecycle and generates a message for each status transition. The message type is `RPC_<STATUS>`.

**All 8 types:** `RPC_QUEUED`, `RPC_SENT`, `RPC_DELIVERED`, `RPC_SUCCESSFUL`, `RPC_TIMEOUT`, `RPC_EXPIRED`, `RPC_FAILED`, `RPC_DELETED`

**Originator:** Device ID (the target device of the RPC).
**Metadata:** Empty (no additional metadata).

**Example (`RPC_QUEUED` -- same structure for all 8 statuses, only `status` field changes):**
```json
{
  "id": {"entityType": "RPC", "id": "b2c3d4e5-..."},
  "createdTime": 1609459200000,
  "tenantId": {"entityType": "TENANT", "id": "..."},
  "deviceId": {"entityType": "DEVICE", "id": "784f394c-..."},
  "expirationTime": 1609545600000,
  "request": {
    "method": "setGpio",
    "params": {"pin": 4, "value": 1}
  },
  "response": null,
  "status": "QUEUED",
  "additionalInfo": null
}
```

---

## Provision Events

### `PROVISION_SUCCESS`

**When:** A device is successfully provisioned (credentials created or existing device re-provisioned).
**Originator:** Device ID.
**Data:** Full Device JSON.

### `PROVISION_FAILURE`

**When:** Device provisioning fails (device already provisioned, invalid credentials, etc.).
**Originator:** Device ID.
**Data:** Full Device JSON.

---

## REST API Request

### `REST_API_REQUEST`

**When:** An external client calls the rule engine REST API endpoint (`/api/rule-engine/...`). This allows you to extend the platform API with custom logic handled by the rule chain.
**Originator:** The entity ID specified in the REST call.

**Data:** The raw request body sent by the client (any valid JSON).

**Metadata:**

| Key | Description |
|-----|-------------|
| `serviceId` | ID of the platform server that received the request |
| `requestUUID` | UUID to correlate the request with the response (used by the `rest call reply` node) |
| `expirationTime` | Timestamp (ms) after which the request times out |

**Example data (custom payload from caller):**
```json
{
  "command": "recalibrate",
  "params": {"offset": 1.5}
}
```

> **Tip:** Use the **Rest Call Reply** rule node to send a response back to the caller.

---

## PE-Specific Events

### `generateReport` (PE)

**When:** A scheduler event triggers report generation.
**Data:** Report generation parameters.

### `OPC_UA_INT_SUCCESS` / `OPC_UA_INT_FAILURE` (PE)

**When:** OPC-UA integration completes successfully or fails.

---

## Tips for Extension Authors

- **Entity is already persisted when the rule chain fires.** By the time your extension receives the callback, the create/update/delete has already been committed to the database. You can safely query the ThingsBoard API for the current state. For delete events, the entity will already be gone from the API.

- **Metadata is NOT included in `msg.getData()` by default.** The REST API Call node sends only `msg.getData()` as the POST body. To include metadata fields, use a **Script** node before the REST API Call to merge metadata into the data object.

- **Originator is always the entity ID.** For entity events, the originator is the entity itself. For alarm user-action events, it's the alarm ID. For device transport events, it's the device ID. For persistent RPC lifecycle events, it's the target device ID.

- **Use the Message Type Switch node** to route messages by type. Each `TbMsgType` value maps to a named output on the switch node (e.g., "Post telemetry", "Entity Created", "Alarm Acknowledged").

- **Entity ID format:** Always `{"entityType": "DEVICE", "id": "uuid-string"}`. Extract the UUID with `data.get("id").get("id").asText()`.

- **Timestamps:** All timestamps are Unix epoch milliseconds (`long`).

- **URL templates:** The REST API Call node supports `${metadataKey}` for metadata values and `$[dataKey]` for data values in the URL. Example: `http://localhost:8090/api/billing/on-device-created?type=${deviceType}`.

- **Metadata key correction:** Assignment metadata keys use `Name` (e.g., `assignedCustomerName`, `unassignedCustomerName`), **not** `Title`. This applies to all customer, edge, tenant, and entity group assignment metadata.
