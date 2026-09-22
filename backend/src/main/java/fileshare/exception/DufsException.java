package fileshare.exception;

import org.springframework.http.HttpStatus;

/**
 * dufs 文件服务器返回了非 2xx 状态码。携带原始状态码，便于映射成合适的 HTTP 响应，
 * 避免出现「文件不存在」却返回 500 的情况。
 */
public class DufsException extends RuntimeException {

    private final HttpStatus status;

    public DufsException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static DufsException of(int statusCode, String action) {
        HttpStatus status = HttpStatus.resolve(statusCode);
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }
        String message = switch (statusCode) {
            case 404 -> "文件或目录不存在";
            case 403 -> "文件服务器拒绝该操作";
            case 405 -> "文件服务器不支持该操作";
            case 409 -> "目标已存在";
            case 412 -> "目标已存在";
            default -> action + "失败：文件服务器返回 " + statusCode;
        };
        return new DufsException(status, message);
    }
}
