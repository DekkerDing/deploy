package io.github.dekkerding.deploy.delivery;

import java.util.List;

/**
 * 服务管理器适配器（design D8）：按目标 OS 生成服务定义与控制命令。
 * systemd（linux）/ WinSW（windows）—— 适配器只产出内容与命令串，
 * 执行由通道（SshChannel）完成，便于无目标机单测。
 */
public interface ServiceManager {

    boolean supportsOs(String os);

    /** 服务定义文件内容（unit 文本 / WinSW XML）。 */
    String generateServiceDefinition(DeliveryContext ctx);

    /** 服务定义文件在目标机的落点绝对路径。 */
    String serviceDefinitionPath(DeliveryContext ctx);

    /** 安装/更新服务后需要依次执行的命令（显式 shell 由调用方包装）。 */
    List<String> activateCommands(DeliveryContext ctx);

    /** 停用/卸载服务命令（回滚切换版本前停掉当前服务）。 */
    List<String> deactivateCommands(DeliveryContext ctx);

    /** 查询服务运行状态的命令（输出供健康检查参考）。 */
    String statusCommand(DeliveryContext ctx);
}
