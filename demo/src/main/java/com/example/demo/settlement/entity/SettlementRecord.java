package com.example.demo.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
@TableName("settlement_record")
public class SettlementRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String trackingNumber;
    private String model;
    private BigDecimal amount;
    private String currency;
    private Boolean manualInput;
    private String status;
    private Boolean warning;
    private String settleBatch;
    private LocalDate payableAt;
    private String remark;
    private String ownerUsername;
    private LocalDateTime orderTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
    private String confirmedBy;
    private LocalDateTime confirmedAt;

    @TableField(exist = false)
    private String orderStatus;

    @TableField(exist = false)
    private BigDecimal orderAmount;

    @TableField(exist = false)
    private String orderCreatedBy;

    @TableField(exist = false)
    private List<String> submissionUsers;

    @TableField(exist = false)
    private String orderSn;

    // ===== 继承订单样式用于前端展示（不入库） =====
    @TableField(exist = false)
    private String trackingBgColor;
    @TableField(exist = false)
    private String trackingFontColor;
    @TableField(exist = false)
    private Boolean trackingStrike;

    @TableField(exist = false)
    private String snBgColor;
    @TableField(exist = false)
    private String snFontColor;
    @TableField(exist = false)
    private Boolean snStrike;

    @TableField(exist = false)
    private String modelBgColor;
    @TableField(exist = false)
    private String modelFontColor;
    @TableField(exist = false)
    private Boolean modelStrike;

    @TableField(exist = false)
    private String amountBgColor;
    @TableField(exist = false)
    private String amountFontColor;
    @TableField(exist = false)
    private Boolean amountStrike;

    @TableField(exist = false)
    private String remarkBgColor;
    @TableField(exist = false)
    private String remarkFontColor;
    @TableField(exist = false)
    private Boolean remarkStrike;
}
