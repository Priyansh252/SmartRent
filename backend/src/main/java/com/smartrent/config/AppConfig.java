package com.smartrent.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** One clock for all business date logic (booking rules, pricing, reports) so it can be replaced in tests. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public OpenAPI smartRentOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SmartRent API")
                .version("1.0")
                .description("Smart car rental & fleet management. Session (cookie) authentication with CSRF: "
                        + "log in at /login, then send the XSRF-TOKEN cookie value in the X-XSRF-TOKEN header on "
                        + "POST/PUT/PATCH/DELETE requests."));
    }
}
