package com.example.demo.submission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_submission_log")
public class UserSubmissionLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String content;
    private LocalDateTime createdAt;
}
