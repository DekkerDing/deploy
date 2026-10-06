package io.github.dekkerding.deploy.delivery;

/**
 * 交付提供者 SPI（design D8）：
 * 通道（sshj / local / 未来 winrm）× 服务管理器适配（systemd / WinSW / 裸进程）。
 *
 * 实现负责把一个制品真正送到目标机并拉起服务：
 * 上传 → 安装/生成服务定义 → 重启 → 健康检查 → 回滚（按需）。
 * 部署留痕（deployment 行）与发布单状态流转由 DeliveryService 统一编排，SPI 只做动作。
 */
public interface DeliveryProvider {

    /** 本提供者支持的通道，对应 target_env.reach（SSH / LOCAL / ...）。 */
    boolean supports(String reach);

    /** 执行一次交付；失败抛 DeliveryException（由编排层捕获落 FAILED），成功返回说明消息。 */
    String deliver(DeliveryContext ctx);

    /**
     * 回滚（任务 6.7）：停掉 current 版本服务，把 rollbackTo 版本重新安装拉起并健康检查。
     * 两个上下文均由编排层从 deployment 留痕组装；失败抛 DeliveryException。
     */
    String rollback(DeliveryContext rollbackTo, DeliveryContext current);
}
