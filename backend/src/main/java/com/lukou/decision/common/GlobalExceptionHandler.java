package com.lukou.decision.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusiness(BusinessException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public R<Void> handleValidation(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getDefaultMessage())
                .orElse("参数不合法");
        return R.fail(400, msg);
    }

    /**
     * 超过 multipart 上限时容器直接抛异常，这里转成人话，避免前端只看到"服务器开小差"
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<Void> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return R.fail(400, "图片太大了，请换一张小一点的");
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        log.error("未处理异常", e);
        return R.fail(500, "服务器开小差了，请稍后再试");
    }
}
