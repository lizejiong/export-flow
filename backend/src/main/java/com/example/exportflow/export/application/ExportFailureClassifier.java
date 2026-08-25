package com.example.exportflow.export.application;

import com.example.exportflow.common.error.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;

import java.io.IOException;

@Component
public class ExportFailureClassifier {
    public Failure classify(Throwable throwable) {
        if (throwable instanceof BusinessException business) {
            return new Failure(business.code(), business.getMessage(), false);
        }
        if (throwable instanceof IllegalArgumentException) {
            return new Failure("EXPORT_DATA_ERROR", "导出数据格式不正确", false);
        }
        if (throwable instanceof DataAccessException) {
            return new Failure("EXPORT_DATA_ACCESS_ERROR", "导出数据读取失败，请稍后重试", true);
        }
        if (throwable instanceof IOException) {
            return new Failure("EXPORT_FILE_ERROR", "导出文件写入失败，请稍后重试", true);
        }
        return new Failure("EXPORT_SYSTEM_ERROR", "导出处理失败，请稍后重试", true);
    }

    public record Failure(String code, String message, boolean retryable) {}
}
