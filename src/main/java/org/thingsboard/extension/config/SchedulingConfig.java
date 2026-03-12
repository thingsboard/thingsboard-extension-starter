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
package org.thingsboard.extension.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.task.SimpleAsyncTaskSchedulerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Customizes the auto-configured {@link org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler}
 * used when {@code spring.threads.virtual.enabled=true}.
 *
 * <p>Sets a custom {@link org.springframework.util.ErrorHandler} that logs exceptions at ERROR level
 * with the full stack trace. This ensures scheduled tasks continue running on the next trigger
 * after a failure -- no silent death.
 *
 * <p>The project uses virtual threads, so Spring Boot auto-configures
 * {@code SimpleAsyncTaskScheduler} (not {@code ThreadPoolTaskScheduler}).
 * The {@link SimpleAsyncTaskSchedulerCustomizer} is the Spring Boot-idiomatic way
 * to modify the auto-configured scheduler without replacing it.
 */
@Configuration
public class SchedulingConfig {

    private static final Logger log = LoggerFactory.getLogger(SchedulingConfig.class);

    @Bean
    public SimpleAsyncTaskSchedulerCustomizer schedulerErrorHandler() {
        return scheduler -> scheduler.setErrorHandler(t ->
            log.error("Scheduled task failed: {}", t.getMessage(), t)
        );
    }

}
