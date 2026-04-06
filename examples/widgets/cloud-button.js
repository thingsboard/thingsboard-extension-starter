/*
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
/**
 * ThingsBoard Button Widget -- Cloud Example
 *
 * What it does:   Calls the extension's /api/extension/report/generate
 *                 endpoint on an external host and shows a tenant summary
 *                 report in a popup.
 *
 * Widget type:    Action -- Button widget "On click" handler
 * Where to paste: Widget -> Settings -> Actions -> "On click" -> Custom action (JS)
 * What to customize: Change the URL below to your extension's public address
 *                    (host and port).
 *
 * CORS requirement: The extension must have CORS_ALLOWED_ORIGINS set to your
 *                   ThingsBoard Cloud origin (e.g. https://your-tb.thingsboard.cloud).
 *                   See the extension's CORS_ALLOWED_ORIGINS environment variable.
 */

// Read the current user's JWT from browser storage.
// NOTE: Requires authenticated dashboard. Public dashboards have no JWT -- call returns 401.
var jwt = localStorage.getItem('jwt_token');

// Full URL -- the extension runs on a separate host from ThingsBoard Cloud.
// Change this to your extension's public address.
var url = 'https://your-extension-host:8090/api/extension/report/generate';

// Use fetch() for cross-origin requests to the external extension.
// The on-premise approach (widgetContext.http) only works for same-origin requests.
fetch(url, {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        // Explicit 'Bearer ' prefix required -- fetch() does not add it automatically.
        // widgetContext.http adds the prefix for you, but fetch() sends the raw value.
        'X-Authorization': 'Bearer ' + jwt
    },
    // Request body -- the controller accepts any JSON. Empty object is fine.
    body: JSON.stringify({})
})
.then(function(response) {
    if (!response.ok) {
        throw new Error('HTTP ' + response.status);
    }
    return response.json();
})
.then(function(data) {
    widgetContext.showSuccessToast('Devices: ' + data.totalDevices +
        ', Assets: ' + data.totalAssets +
        ', Users: ' + data.totalUsers);
})
.catch(function(error) {
    // fetch() bypasses Angular's HttpClient, so errors must be handled manually.
    widgetContext.showErrorToast('Error: ' + (error.message || 'Request failed'));
});
