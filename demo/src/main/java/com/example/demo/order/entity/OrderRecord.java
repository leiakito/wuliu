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

    @TableField(exist = false)
    private String ownerUsername;

    // ===== Excel 导入样式（不入库，仅用于前端展示） =====
    @TableField(exist = false)
    private String modelBgColor;
    @TableField(exist = false)
    private String modelFontColor;
    @TableField(exist = false)
    private Boolean modelStrike;

    @TableField(exist = false)
    private String snBgColor;
    @TableField(exist = false)
    private String snFontColor;
    @TableField(exist = false)
    private Boolean snStrike;

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

    @TableField(exist = false)
    private Integer excelRowIndex;
    
    @TableField(exist = false)
    private String trackingBgColor;
    @TableField(exist = false)
    private String trackingFontColor;
    @TableField(exist = false)
    private Boolean trackingStrike;
}
