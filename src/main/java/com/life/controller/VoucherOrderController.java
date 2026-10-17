package com.life.controller;

import com.life.common.Result;
import com.life.service.VoucherOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀接口。演示环境 userId 由请求头传入；
 * 真实环境应从登录态（Redis token）解析。
 */
@RestController
@RequestMapping("/voucher-order")
@RequiredArgsConstructor
public class VoucherOrderController {

    private final VoucherOrderService voucherOrderService;

    @PostMapping("/seckill/{voucherId}")
    public Result<Long> seckill(@PathVariable("voucherId") Long voucherId,
                                @RequestHeader(value = "user-id", defaultValue = "1") Long userId) {
        Long orderId = voucherOrderService.seckillVoucher(voucherId, userId);
        return Result.ok(orderId);
    }
}
