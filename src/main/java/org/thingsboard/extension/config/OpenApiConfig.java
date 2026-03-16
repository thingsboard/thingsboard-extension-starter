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

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thingsboard.client.ThingsboardClient;

@Configuration
public class OpenApiConfig {

    private static final String AUTH_HEADER = "X-Authorization";

    static {
        // ThingsboardClient is auto-resolved from the X-Authorization header — hide it from Swagger UI
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(ThingsboardClient.class);
    }

    @Bean
    public OpenAPI extensionOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ThingsBoard Extension API")
                        .description("Custom extension endpoints for ThingsBoard platform")
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes("ThingsBoard", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(AUTH_HEADER)
                                .description("Enter the full value including prefix, for example:\n\n" +
                                             "API key: '**ApiKey** tb_WXlnQiJ-VQ8...'\n\n" +
                                             "JWT: '**Bearer** eyJhbGciOiJIUzI1NiJ9...'")))
                .addSecurityItem(new SecurityRequirement().addList("ThingsBoard"));
    }

}
