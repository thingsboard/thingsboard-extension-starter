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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.thingsboard.client.ThingsboardClient;
import org.thingsboard.client.model.PageDataDevice;

import java.util.concurrent.TimeUnit;

/**
 * Example: run a background job on a schedule using preconfigured TB credentials.
 *
 * Credential requirements (set one of the following):
 *   TB_PRECONFIGURED_API_KEY=your-api-key
 *   or
 *   TB_PRECONFIGURED_USERNAME=your-username
 *   TB_PRECONFIGURED_PASSWORD=your-password
 *
 * No rule chain wiring needed -- the task runs automatically on the configured schedule.
 *
 * Exceptions are caught by SchedulingConfig's ErrorHandler and logged at ERROR level;
 * the task continues running on the next trigger after a failure.
 *
 * If preconfigured credentials are not set, the application will fail to start with
 * NoSuchBeanDefinitionException -- this is intentional.
 */
@Component
public class DeviceHealthCheckTask {

    private static final Logger log = LoggerFactory.getLogger(DeviceHealthCheckTask.class);

    private final ThingsboardClient tb;

    public DeviceHealthCheckTask(@Qualifier("preconfiguredTbClient") ThingsboardClient tb) {
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
