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
 * ThingsBoard Button Widget -- On-Premise Example
 *
 * What it does:   Calls the extension's /api/extension/widget/current-stats
 *                 endpoint and shows the total device count in a popup.
 *
 * Widget type:    Action -- Button widget "On click" handler
 * Where to paste: Widget -> Settings -> Actions -> "On click" -> Custom action (JS)
 * What to customize: Nothing required for on-premise with default ports.
 *                    Change the URL if your extension runs on a different path.
 */

// Relative URL -- ThingsBoard resolves this against its own host.
// HAProxy routes /api/extension/* to the extension service.
var url = '/api/extension/widget/current-stats';

// Request body -- the controller accepts any JSON. Empty object is fine.
var body = {};

// self.ctx.http is ThingsBoard's built-in HTTP client.
// For relative /api URLs, it automatically adds the user's JWT
// in the X-Authorization header -- no manual auth needed.
self.ctx.http.post(url, body).then(
    function(response) {
        // response is the parsed JSON body from the extension
        alert('Total devices: ' + response.totalDevices);
    },
    function(error) {
        alert('Error: ' + (error.message || 'Request failed'));
    }
);
