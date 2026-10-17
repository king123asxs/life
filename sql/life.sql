-- =============================================
-- 惠享生活 数据库初始化脚本
-- 使用: mysql -uroot -p < life.sql
-- =============================================
CREATE DATABASE IF NOT EXISTS `life` DEFAULT CHARACTER SET utf8mb4;
USE `life`;

-- 用户表
DROP TABLE IF EXISTS `tb_user`;
CREATE TABLE `tb_user` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `nick_name`   VARCHAR(32)  NOT NULL COMMENT '昵称',
  `phone`       VARCHAR(11)  NOT NULL COMMENT '手机号',
  `password`    VARCHAR(128) NOT NULL COMMENT '密码(加密)',
  `icon`        VARCHAR(256) DEFAULT NULL COMMENT '头像',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB COMMENT='用户表';

-- 商铺表
DROP TABLE IF EXISTS `tb_shop`;
CREATE TABLE `tb_shop` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(128)  NOT NULL COMMENT '商铺名称',
  `type_id`     BIGINT UNSIGNED NOT NULL COMMENT '商铺类型',
  `images`      VARCHAR(1024) DEFAULT NULL COMMENT '照片，逗号分隔',
  `area`        VARCHAR(128)  DEFAULT NULL,
  `address`     VARCHAR(256)  DEFAULT NULL COMMENT '地址',
  `x`           DOUBLE        NOT NULL DEFAULT 0 COMMENT '经度',
  `y`           DOUBLE        NOT NULL DEFAULT 0 COMMENT '纬度',
  `avg_price`   BIGINT        DEFAULT NULL COMMENT '均价',
  `sold`        INT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '销量',
  `comments`    INT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '评论数',
  `score`       INT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '评分',
  `open_hours`  VARCHAR(32)   DEFAULT NULL COMMENT '营业时间',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_type_id` (`type_id`)
) ENGINE=InnoDB COMMENT='商铺表';

-- 秒杀优惠券表
DROP TABLE IF EXISTS `tb_voucher`;
CREATE TABLE `tb_voucher` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id`       BIGINT UNSIGNED NOT NULL COMMENT '商铺 id',
  `title`         VARCHAR(128) NOT NULL COMMENT '券标题',
  `sub_title`     VARCHAR(256) DEFAULT NULL COMMENT '副标题',
  `rules`         VARCHAR(1024) DEFAULT NULL COMMENT '使用规则',
  `pay_value`     BIGINT UNSIGNED NOT NULL COMMENT '售价(分)',
  `actual_value`  BIGINT UNSIGNED NOT NULL COMMENT '面值(分)',
  `type`          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0 普通券 1 秒杀券',
  `stock`         INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '库存',
  `begin_time`    DATETIME     DEFAULT NULL COMMENT '生效时间',
  `end_time`      DATETIME     DEFAULT NULL COMMENT '失效时间',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_shop_id` (`shop_id`)
) ENGINE=InnoDB COMMENT='优惠券表';

-- 优惠券订单表
DROP TABLE IF EXISTS `tb_voucher_order`;
CREATE TABLE `tb_voucher_order` (
  `id`          BIGINT UNSIGNED NOT NULL COMMENT '订单 id(分布式 id)',
  `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '用户 id',
  `voucher_id`  BIGINT UNSIGNED NOT NULL COMMENT '券 id',
  `pay_time`    DATETIME     DEFAULT NULL COMMENT '支付时间',
  `status`      TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1 未支付 2 已支付 3 已关闭 4 已核销',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `pay_time2`   DATETIME     DEFAULT NULL,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_voucher` (`user_id`, `voucher_id`) COMMENT '一人一单兜底',
  KEY `idx_status_time` (`status`, `create_time`) COMMENT '关单扫描'
) ENGINE=InnoDB COMMENT='优惠券订单表';

-- 示例数据
INSERT INTO `tb_shop` (`name`, `type_id`, `address`, `x`, `y`, `avg_price`, `sold`, `score`, `open_hours`) VALUES
('南京大牌档(德基广场店)', 1, '南京市玄武区中山路18号德基广场7F', 118.784, 32.045, 88, 4321, 45, '10:00-22:00'),
('胖哥俩肉蟹煲(新街口店)', 1, '南京市秦淮区中山东路99号', 118.790, 32.041, 96, 3210, 43, '10:00-21:30'),
('金逸影城(新街口店)', 2, '南京市秦淮区中山南路1号', 118.787, 32.038, 45, 8765, 44, '09:00-23:30');

INSERT INTO `tb_voucher` (`shop_id`, `title`, `sub_title`, `pay_value`, `actual_value`, `type`, `stock`, `begin_time`, `end_time`) VALUES
(1, '100元代金券', '全场可用', 8000, 10000, 1, 100, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(2, '双人套餐券', '肉蟹煲双人套餐', 12800, 19800, 1, 50, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY));
