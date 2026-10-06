package io.github.dekkerding.deploy.delivery;

/** 交付执行异常：编排层捕获后落部署记录 FAILED 与发布单 FAILED。 */
public class DeliveryException extends RuntimeException {

    public DeliveryException(String message) {
        super(message);
    }

    public DeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
