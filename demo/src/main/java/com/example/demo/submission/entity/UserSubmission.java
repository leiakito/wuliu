package com.example.demo.submission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.demo.order.entity.OrderRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_submission")
public class UserSubmission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String trackingNumber;

    private String status;

    private BigDecimal amount;

    private LocalDate submissionDate;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private OrderRecord order;
}
