package com.example.demo.order.entity;

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
@TableName("order_record")
public class OrderRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate orderDate;
    private LocalDateTime orderTime;
    private String trackingNumber;
    private String model;
    private String sn;
    private String remark;
    private String category;
    private String status;
    private BigDecimal amount;
    private String currency;
    private BigDecimal weight;
    private String customerName;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean imported;
    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private boolean inCurrentSettlement;
}
