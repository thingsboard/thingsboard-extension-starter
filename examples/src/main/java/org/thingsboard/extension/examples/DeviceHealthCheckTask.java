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
package org.thingsboard.extension.examples;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.thingsboard.client.ThingsboardClient;
import org.thingsboard.client.model.PageDataDevice;

import java.util.concurrent.TimeUnit;

/**
 * Example: run a background job on a schedule using configured TB credentials.
 *
 * Credential requirements (set one of the following in application.yml or env vars):
 *   TB_AUTH_API_KEY=your-api-key
 *   or
 *   TB_AUTH_USERNAME=your-username
 *   TB_AUTH_PASSWORD=your-password
 *
 * No rule chain wiring needed -- the task runs automatically on the configured schedule.
 *
 * Exceptions are caught by SchedulingConfig's ErrorHandler and logged at ERROR level;
 * the task continues running on the next trigger after a failure.
 *
 * If authentication credentials are not set, the ThingsboardClient bean is not created
 * and this component is silently skipped (via @ConditionalOnBean).
 */
@Slf4j
@ConditionalOnBean(ThingsboardClient.class)
@Component
public class DeviceHealthCheckTask {

    private final ThingsboardClient tb;

    public DeviceHealthCheckTask(ThingsboardClient tb) {
        this.tb = tb;
    }

    @Scheduled(fixedRate = 60, timeUnit = TimeUnit.SECONDS)
    public void run() throws Exception {
        PageDataDevice page = tb.getTenantDevices(100, 0, null, null, null, null);
        // Production code should paginate using page.getHasNext()
        long total = page.getTotalElements();
        log.info("Health check: {} devices in tenant", total);

        String ts = String.valueOf(System.currentTimeMillis());
        for (var device : page.getData()) {
            String deviceId = device.getId().getId().toString();
            tb.saveDeviceAttributes(deviceId, "SERVER_SCOPE",
                    "{\"lastHealthCheckTs\": " + ts + "}");
        }
    }

}
