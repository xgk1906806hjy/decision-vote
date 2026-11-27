package com.lukou.decision.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 本地上传目录与对外访问前缀。
 * 本地开发不配 public-base，返回相对路径 /uploads/...；
 * 上线时若 Nginx 单独接管静态资源，可配成 https://cdn.xxx.com。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    /** 上传根目录，相对路径按后端进程工作目录解析 */
    private String dir = "./uploads";

    /** 对外前缀，留空则返回相对路径 */
    private String publicBase = "";

    /** 单张头像大小上限（MB） */
    private int avatarMaxMb = 2;
}
