package com.example.exportflow.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiResponseBodyAdviceTest {

    @Test
    void wrapsSuccessfulJsonResponse() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SuccessController())
                .setControllerAdvice(new ApiResponseBodyAdvice())
                .addFilters(new RequestIdFilter())
                .build();

        mvc.perform(get("/test/success").header("X-Request-Id", "request-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data.value").value("ok"))
                .andExpect(jsonPath("$.requestId").value("request-123"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @RestController
    static class SuccessController {
        @GetMapping("/test/success")
        Map<String, String> success() {
            return Map.of("value", "ok");
        }
    }
}
