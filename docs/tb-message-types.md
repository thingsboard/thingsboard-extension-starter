# ThingsBoard Message Types Reference

When a rule chain event fires, the REST API Call node sends `msg.getData()` as the POST body to your extension endpoint. This document lists common message types, when they fire, and what JSON data they carry.

## Device Lifecycle

### Device Created
**When:** A new device is created (via UI, API, or provisioning).
**msg type:** `ENTITY_CREATED`
**Data:**
```json
{
  "id": {"entityType": "DEVICE", "id": "784f394c-42b6-11e7-9926-dea266d719d9"},
  "createdTime": 1609459200000,
  "tenantId": {"entityType": "TENANT", "id": "..."},
  "customerId": {"entityType": "CUSTOMER", "id": "..."},
  "name": "Temperature Sensor A1",
  "type": "default",
  "label": "",
  "deviceProfileId": {"entityType": "DEVICE_PROFILE", "id": "..."},
  "additionalInfo": {}
}
```

### Device Updated
**When:** Device entity is updated.
**msg type:** `ENTITY_UPDATED`
**Data:** Same structure as Device Created, with updated fields.

### Device Deleted
**When:** A device is deleted.
**msg type:** `ENTITY_DELETED`
**Data:** Same structure as Device Created.

### Device Assigned to Customer
**When:** Device is assigned to a customer.
**msg type:** `ENTITY_ASSIGNED`
**Data:** Same device JSON. Metadata includes `assignedCustomerId` and `assignedCustomerTitle`.

### Device Unassigned from Customer
**When:** Device is unassigned from a customer.
**msg type:** `ENTITY_UNASSIGNED`
**Data:** Same device JSON. Metadata includes `unassignedCustomerId` and `unassignedCustomerTitle`.

## Telemetry & Attributes

### Post Telemetry
**When:** Device sends telemetry data.
**msg type:** `POST_TELEMETRY_REQUEST`
**Data:**
```json
{
  "temperature": 25.5,
  "humidity": 60
}
```
**Metadata includes:** `deviceName`, `deviceType`, `ts` (timestamp).

### Post Attributes
**When:** Device sends client-side attributes.
**msg type:** `POST_ATTRIBUTES_REQUEST`
**Data:**
```json
{
  "firmware_version": "1.2.3",
  "serial_number": "SN-001"
}
```

### Attributes Updated
**When:** Server-side or shared attributes are updated (via API or rule chain).
**msg type:** `ATTRIBUTES_UPDATED`
**Data:**
```json
{
  "attribute1": "newValue",
  "attribute2": 42
}
```
**Metadata includes:** `scope` (`SERVER_SCOPE` or `SHARED_SCOPE`).

### Attributes Deleted
**When:** Attributes are deleted.
**msg type:** `ATTRIBUTES_DELETED`
**Data:**
```json
{
  "attributes": ["attribute1", "attribute2"]
}
```

## Alarms

### Alarm Created
**When:** A new alarm is created.
**msg type:** `ALARM`
**Data:**
```json
{
  "id": {"entityType": "ALARM", "id": "..."},
  "createdTime": 1609459200000,
  "tenantId": {"entityType": "TENANT", "id": "..."},
  "type": "High Temperature",
  "originator": {"entityType": "DEVICE", "id": "..."},
  "severity": "CRITICAL",
  "status": "ACTIVE_UNACK",
  "startTs": 1609459200000,
  "endTs": 1609459200000,
  "details": {"temperature": 95.5, "threshold": 80.0}
}
```

### Alarm Updated
**When:** An existing alarm is updated (severity change, acknowledged, etc.).
**msg type:** `ALARM`
**Data:** Same structure as Alarm Created, with updated fields. Check `status` field: `ACTIVE_UNACK`, `ACTIVE_ACK`, `CLEARED_UNACK`, `CLEARED_ACK`.

### Alarm Cleared
**When:** An alarm is cleared.
**msg type:** `ALARM`
**Data:** Same structure, `status` will be `CLEARED_UNACK` or `CLEARED_ACK`.

## Relations

### Relation Added
**When:** A relation is created between entities.
**msg type:** `RELATION_ADD_OR_UPDATE`
**Data:**
```json
{
  "from": {"entityType": "DEVICE", "id": "..."},
  "to": {"entityType": "ASSET", "id": "..."},
  "type": "Contains",
  "typeGroup": "COMMON",
  "additionalInfo": {}
}
```

### Relation Deleted
**When:** A relation is removed.
**msg type:** `RELATION_DELETED`
**Data:** Same structure as Relation Added.

## Other Entity Types

### Asset Created/Updated/Deleted
**msg type:** `ENTITY_CREATED` / `ENTITY_UPDATED` / `ENTITY_DELETED`
**Data:** Asset JSON with `id.entityType = "ASSET"`, `name`, `type`, `label`, etc.

### Customer Created/Updated/Deleted
**msg type:** `ENTITY_CREATED` / `ENTITY_UPDATED` / `ENTITY_DELETED`
**Data:** Customer JSON with `id.entityType = "CUSTOMER"`, `title`, `email`, `phone`, etc.

### Dashboard Created/Updated/Deleted
**msg type:** `ENTITY_CREATED` / `ENTITY_UPDATED` / `ENTITY_DELETED`
**Data:** Dashboard JSON with `id.entityType = "DASHBOARD"`, `title`, etc.

## Activity Events

### Activity
**When:** Device connects or sends data (first activity after inactivity).
**msg type:** `ACTIVITY_EVENT`
**Data:**
```json
{
  "active": true
}
```

### Inactivity
**When:** Device becomes inactive (no data within configured timeout).
**msg type:** `INACTIVITY_EVENT`
**Data:**
```json
{
  "active": false
}
```

## RPC

### RPC Request to Device (from server)
**When:** Server sends RPC command to device.
**msg type:** `RPC_CALL_FROM_SERVER_TO_DEVICE`
**Data:**
```json
{
  "method": "setGpio",
  "params": {"pin": 4, "value": 1}
}
```

### RPC Request from Device (to server)
**When:** Device sends RPC request to server.
**msg type:** `RPC_CALL_FROM_DEVICE_TO_SERVER`
**Data:**
```json
{
  "method": "getServerTime",
  "params": {}
}
```

## Tips for Extension Authors

- **Metadata** is NOT included in `msg.getData()` by default. The REST API Call node sends only `msg.getData()` as the POST body. To include metadata fields, use the **Script** node before the REST API Call to merge metadata into the data.
- **Entity ID format**: Always `{"entityType": "DEVICE", "id": "uuid-string"}`. Extract the UUID with `data.get("id").get("id").asText()`.
- **Timestamps**: All timestamps are Unix epoch milliseconds (long).
- **URL templates**: The REST API Call node supports `${metadataKey}` for metadata values and `$[dataKey]` for data values in the URL. Example: `http://localhost:8090/api/billing/on-device-created?type=${deviceType}`.
