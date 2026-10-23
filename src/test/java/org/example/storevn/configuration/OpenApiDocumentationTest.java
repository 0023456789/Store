package org.example.storevn.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiDocumentListsStorefrontAndVnpayOperations() throws Exception {
        mockMvc.perform(get("/storevn/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("StoreVN API"))
                .andExpect(jsonPath("$.servers[0].url").value("/api/v1"))
                .andExpect(jsonPath("$.paths['/store/plans'].get.summary").value("Danh sách gói cước đang bán"))
                .andExpect(jsonPath("$.paths['/store/orders'].post").exists())
                .andExpect(jsonPath("$.paths['/payments/vnpay/ipn'].get.parameters").isArray())
                .andExpect(jsonPath("$.paths['/payments/vnpay/return'].get.responses['302']").exists());
    }
}
