package com.example.demo.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("settlement_record")
public class SettlementRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String trackingNumber;
    private BigDecimal amount;
    private String currency;
    private Boolean manualInput;
    private String status;
    private Boolean warning;
    private String settleBatch;
    private LocalDate payableAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private String orderStatus;

    @TableField(exist = false)
    private BigDecimal orderAmount;
}
