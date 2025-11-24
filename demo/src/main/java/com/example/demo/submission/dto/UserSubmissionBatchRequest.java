package com.example.demo.submission.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class UserSubmissionBatchRequest {

    @NotEmpty(message = "请至少输入一个单号")
    private List<String> trackingNumbers;

    /**
     * 用户粘贴的原始文本，便于日志记录。
     */
    private String rawContent;

    /**
     * 管理员可指定的归属用户名；普通用户忽略该字段。
     */
    private String username;
}
