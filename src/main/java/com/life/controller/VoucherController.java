package com.life.controller;

import com.life.common.Result;
import com.life.entity.Voucher;
import com.life.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/voucher")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    @GetMapping("/{id}")
    public Result<Voucher> queryById(@PathVariable("id") Long id) {
        return Result.ok(voucherService.queryById(id));
    }
}
