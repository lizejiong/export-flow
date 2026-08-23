package com.example.exportflow.common.error;

import com.example.exportflow.common.api.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @Test
    void returnsStructuredBusinessError() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();

        mvc.perform(get("/test/fail").header("X-Request-Id", "request-123"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NO_EXPORT_DATA"))
                .andExpect(jsonPath("$.message").value("没有可导出的订单"))
                .andExpect(jsonPath("$.requestId").value("request-123"))
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @RestController
    static class FailingController {
        @GetMapping("/test/fail")
        void fail() {
            throw new BusinessException("NO_EXPORT_DATA", HttpStatus.UNPROCESSABLE_ENTITY, "没有可导出的订单");
        }
    }
}
