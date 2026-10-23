package org.example.storevn.configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "StoreVN API",
                version = "v1",
                description = "API storefront: xem gói cước, tạo và tra cứu đơn hàng, thanh toán qua VNPAY. " +
                        "API nghiệp vụ trả ApiResponse; IPN trả RspCode/Message theo giao thức VNPAY.",
                contact = @Contact(name = "StoreVN"),
                license = @License(name = "Internal API")
        ),
        servers = @Server(url = "/api/v1", description = "StoreVN API")
)
public class OpenApiConfig {
}
