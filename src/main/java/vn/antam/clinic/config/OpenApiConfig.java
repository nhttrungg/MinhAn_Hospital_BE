package vn.antam.clinic.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI clinicOpenApi() {
        return new OpenAPI()
            .info(
                new Info()
                    .title("Minh An Clinic API")
                    .version("v1")
                    .description(
                        "Hợp đồng REST cho hệ thống quản lý bệnh viện Minh An. Tiền tệ VND, múi giờ Asia/Ho_Chi_Minh."
                    )
            )
            .components(
                new Components().addSecuritySchemes(
                    "session",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("ANTAM_SESSION")
                        .description(
                            "Cookie phiên HttpOnly. Các yêu cầu ghi cần header X-XSRF-TOKEN."
                        )
                )
            );
    }
}
