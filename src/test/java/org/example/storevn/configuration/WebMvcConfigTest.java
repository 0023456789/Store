package org.example.storevn.configuration;

import org.example.storevn.controller.HealthController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HealthController.class)
@ActiveProfiles("test")
@Import(WebMvcConfig.class)
class WebMvcConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("OPTIONS preflight from allowed origin returns 200 with CORS headers")
    void cors_allowedOrigin_returnsAccessControlHeaders() throws Exception {
        mockMvc.perform(options("/ping")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("OPTIONS preflight from unauthorized origin does not return Access-Control-Allow-Origin")
    void cors_unauthorizedOrigin_doesNotAllow() throws Exception {
        mockMvc.perform(options("/ping")
                        .header("Origin", "http://malicious-site.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
