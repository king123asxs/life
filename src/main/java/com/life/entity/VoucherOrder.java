package com.life.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 订单状态: 1 未支付 2 已支付 3 已关闭 4 已核销 */
@Data
@TableName("tb_voucher_order")
public class VoucherOrder {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long voucherId;
    private LocalDateTime payTime;
    /** 1 未支付 2 已支付 3 已关闭 4 已核销 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
