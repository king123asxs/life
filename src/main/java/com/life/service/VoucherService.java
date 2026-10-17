package com.life.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.life.entity.Voucher;

public interface VoucherService extends IService<Voucher> {
    Voucher queryById(Long id);
}
