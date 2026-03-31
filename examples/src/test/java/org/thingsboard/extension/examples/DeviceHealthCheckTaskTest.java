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

import org.junit.jupiter.api.Test;
import org.thingsboard.client.ThingsboardClient;
import org.thingsboard.client.model.Device;
import org.thingsboard.client.model.PageDataDevice;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeviceHealthCheckTaskTest {

    @Test
    void savesHealthCheckAttributeForEachDevice() throws Exception {
        ThingsboardClient tb = mock(ThingsboardClient.class);

        Device device = mock(Device.class, RETURNS_DEEP_STUBS);
        when(device.getId().getId()).thenReturn(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));

        PageDataDevice page = mock(PageDataDevice.class);
        when(page.getData()).thenReturn(List.of(device));
        when(page.getTotalElements()).thenReturn(1L);
        when(tb.getTenantDevices(anyInt(), anyInt(), any(), any(), any(), any())).thenReturn(page);

        DeviceHealthCheckTask task = new DeviceHealthCheckTask(tb);
        task.run();

        verify(tb).saveDeviceAttributes(
                eq("550e8400-e29b-41d4-a716-446655440000"),
                eq("SERVER_SCOPE"),
                contains("lastHealthCheckTs"));
    }

}
