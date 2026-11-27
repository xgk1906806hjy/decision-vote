package com.lukou.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 头像异步送审记录：上传时提交 mediaCheckAsync 拿 trace_id，
 * 微信回调（/api/wx/callback）按 trace_id 回填结果，违规则重置用户头像。
 */
@Data
@TableName("dv_media_check")
public class MediaCheck {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;
    private Long userId;
    private String mediaPath;

    /** 回调结果：pass / review / risky；回调前为空 */
    private String suggest;

    /** 0=待回调 1=通过 2=违规已重置 3=检测失败 */
    private Integer status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}