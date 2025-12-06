package com.example.demo.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("order_cell_style")
public class OrderCellStyle {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /**
     * 字段标识：tracking | model | sn | amount | remark
     */
    private String field;

    private String bgColor;

    private String fontColor;

    private Boolean strike;

    private Boolean bold;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

