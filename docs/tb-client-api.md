# ThingsboardClient API Cheatsheet

`ThingsboardClient` extends the generated `ThingsboardApi` class and provides 100+ methods covering all ThingsBoard REST APIs. This document lists the most commonly used methods with examples.

All methods throw `ApiException` on failure.

> **Note:** Method signatures (parameter counts) vary by edition (CE / PE / PaaS). The examples below match the PaaS edition. CE methods typically have fewer optional parameters. Always check `target/api-docs/` for your edition's exact signatures.

## Devices

```java
// Get device by ID
Device device = tb.getDeviceById(deviceId);

// Get device by name (unique within tenant)
Device device = tb.getTenantDeviceByName("Temperature Sensor A1");

// Create or update a device
Device device = new Device();
device.setName("My Device");
device.setType("default");
Device saved = tb.saveDevice(device, null, null, null, null, null, null);

// Delete a device
tb.deleteDevice(deviceId);

// Get device credentials (access token)
DeviceCredentials creds = tb.getDeviceCredentialsByDeviceId(deviceId);
String accessToken = creds.getCredentialsId();

// List tenant devices (paginated)
PageDataDevice page = tb.getTenantDevices(10, 0, null, null, null, null);
List<Device> devices = page.getData();

// Get device info (includes customer name, device profile name, etc.)
DeviceInfo info = tb.getDeviceInfoById(deviceId);
```

## Assets

```java
// Get asset by ID
Asset asset = tb.getAssetById(assetId);

// Get asset by name (unique within tenant)
Asset asset = tb.getTenantAssetByName("Building A");

// Create or update an asset
Asset asset = new Asset();
asset.setName("Building A");
asset.setType("building");
Asset saved = tb.saveAsset(asset, null, null, null, null, null);

// Delete an asset
tb.deleteAsset(assetId);

// List tenant assets (paginated)
PageDataAsset page = tb.getTenantAssets(10, 0, null, null, null, null);
List<Asset> assets = page.getData();
```

## Customers

```java
// Get customer by ID
Customer customer = tb.getCustomerById(customerId);

// Get customer by title (unique within tenant)
Customer customer = tb.getTenantCustomer("Acme Corp");

// Create or update a customer
Customer customer = new Customer();
customer.setTitle("Acme Corp");
customer.setEmail("info@acme.com");
Customer saved = tb.saveCustomer(customer, null, null, null, null, null);
```

## Attributes

```java
// Save server-side attributes on a device
tb.saveDeviceAttributes(deviceId, "SERVER_SCOPE",
        """
        {"billingActive": true, "plan": "pro"}
        """);

// Save attributes on any entity (device, asset, etc.)
tb.saveEntityAttributesV2("ASSET", assetId, "SERVER_SCOPE",
        """
        {"key1": "value1", "key2": 42}
        """);

// Read attributes by scope
List<AttributeData> attrs = tb.getAttributesByScope(
        "DEVICE", deviceId, "SERVER_SCOPE",
        "billingActive,plan",  // comma-separated keys (or null for all)
        null);                 // alternative: List<String> keys

// List attribute key names
List<String> keys = tb.getAttributeKeys("DEVICE", deviceId);
List<String> scopedKeys = tb.getAttributeKeysByScope("DEVICE", deviceId, "SERVER_SCOPE");

// Delete attributes
tb.deleteDeviceAttributes(deviceId, "SERVER_SCOPE", "key1,key2", null);
```

### Read-modify-write pattern

A common pattern: read an attribute, change its value, save it back.

```java
// Read current value
List<AttributeData> attrs = tb.getAttributesByScope(
        "ASSET", assetId, "SERVER_SCOPE", "deviceCount", null);

long current = 0;
if (!attrs.isEmpty()) {
    current = ((Number) attrs.get(0).getValue()).longValue();
}

// Modify and save back
long updated = current + 1;
tb.saveEntityAttributesV2("ASSET", assetId, "SERVER_SCOPE",
        "{\"deviceCount\": %d}".formatted(updated));
```

### AttributeData.getValue() runtime types

`AttributeData.getValue()` returns `Object`. The actual Java type depends on the stored value:

| JSON value type | Java runtime type |
|----------------|-------------------|
| integer        | `Long`            |
| decimal        | `Double`          |
| string         | `String`          |
| boolean        | `Boolean`         |
| object / array | `Map` / `List`    |

Cast safely via `Number` for numeric values: `((Number) attr.getValue()).longValue()`.

## Telemetry

```java
// List telemetry key names for an entity
List<String> keys = tb.getTimeseriesKeys("DEVICE", deviceId);

// Get timeseries history
Map<String, List<TsData>> data = tb.getTimeseriesHistory(
        "DEVICE", deviceId,
        startTs, endTs,       // Unix epoch millis
        "temperature,humidity", // keys
        null, null, null,     // intervalType, interval, timeZone
        "100",                // limit
        "NONE",               // aggregation: NONE, AVG, SUM, MIN, MAX, COUNT
        "DESC",               // order
        true,                 // useStrictDataTypes
        null);                // alternative key list

// Delete timeseries data
tb.deleteEntityTimeseries("DEVICE", deviceId,
        "temperature", true,  // keys, deleteAllDataForKeys
        null, null,           // startTs, endTs (null = all)
        true, false, null);   // deleteLatest, rewriteLatest, keyList
```

## Alarms

```java
// Create an alarm
Alarm alarm = new Alarm();
alarm.setType("High Temperature");
alarm.setOriginator(new EntityId().entityType(EntityId.EntityTypeEnum.DEVICE).id(deviceId));
alarm.setSeverity(Alarm.SeverityEnum.CRITICAL);
Alarm saved = tb.saveAlarm(alarm);

// Get alarm by ID
Alarm alarm = tb.getAlarmById(alarmId);

// Acknowledge an alarm
tb.ackAlarm(alarmId);

// Clear an alarm
tb.clearAlarm(alarmId);
```

## Relations

```java
// Create a relation
EntityRelation relation = new EntityRelation();
relation.setFrom(new EntityId().entityType(EntityId.EntityTypeEnum.ASSET).id(assetId));
relation.setTo(new EntityId().entityType(EntityId.EntityTypeEnum.DEVICE).id(deviceId));
relation.setType("Contains");
relation.setTypeGroup(EntityRelation.TypeGroupEnum.COMMON);
tb.saveRelation(relation);
```

## Users

```java
// Get current user
User user = tb.getUser();

// Get user by ID
User user = tb.getUserById(userId);
```

## Dashboards

```java
// Get dashboard by ID
Dashboard dashboard = tb.getDashboardById(dashboardId);

// List tenant dashboards (paginated)
PageDataDashboardInfo page = tb.getTenantDashboards1(10, 0, null, null, null, null);
```

## Accessor Chains (getting a String ID)

All entity IDs follow the same pattern: `entity.getId().getId().toString()`.

```java
// From a Device
Device device = tb.getTenantDeviceByName("My Device");
String deviceId = device.getId().getId().toString();

// From an Asset
Asset asset = tb.getTenantAssetByName("Building A");
String assetId = asset.getId().getId().toString();

// From a Customer
Customer customer = tb.getTenantCustomer("Acme Corp");
String customerId = customer.getId().getId().toString();

// From a paginated result
PageDataDevice page = tb.getTenantDevices(10, 0, null, null, null, null);
for (Device d : page.getData()) {
    String id = d.getId().getId().toString();
    String name = d.getName();
}
```

### PageData fields

All `PageData*` types share the same structure:

```java
PageDataDevice page = tb.getTenantDevices(100, 0, null, null, null, null);
List<Device> items = page.getData();         // entities on this page
int totalPages     = page.getTotalPages();   // total number of pages
long totalElements = page.getTotalElements(); // total entity count
boolean hasNext    = page.getHasNext();      // more pages available?
```

## Tips

- **Entity type strings**: `"DEVICE"`, `"ASSET"`, `"CUSTOMER"`, `"TENANT"`, `"DASHBOARD"`, `"ALARM"`, `"USER"`, `"EDGE"`, `"ENTITY_VIEW"`, `"RULE_CHAIN"`
- **Attribute scopes**: `"SERVER_SCOPE"`, `"SHARED_SCOPE"`, `"CLIENT_SCOPE"`
- **Pagination**: Most list methods take `(pageSize, page, ...)`. Page is 0-indexed.
- **JSON body parameters**: Methods like `saveDeviceAttributes` take a `String body` — pass a JSON string.
- **All IDs are strings**: Even though ThingsBoard uses UUIDs internally, the Java client methods accept and return String IDs.
- **Error handling**: All methods throw `ApiException`. The exception has `getCode()` (HTTP status) and `getMessage()`.
- **Lookup by name**: Devices, assets, and customers can be looked up by name/title directly — no need to paginate and filter. Use `getTenantDeviceByName`, `getTenantAssetByName`, `getTenantCustomer`.
