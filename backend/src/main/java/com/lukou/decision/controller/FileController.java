package com.lukou.decision.controller;

import com.lukou.decision.common.BusinessException;
import com.lukou.decision.common.R;
import com.lukou.decision.config.ContentProperties;
import com.lukou.decision.config.UploadProperties;
import com.lukou.decision.entity.MediaCheck;
import com.lukou.decision.entity.User;
import com.lukou.decision.mapper.MediaCheckMapper;
import com.lukou.decision.mapper.UserMapper;
import com.lukou.decision.service.WxApiService;
import com.lukou.decision.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * 头像上传。只收图片、只按魔数判类型，文件名完全由服务端生成。
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final UploadProperties props;
    private final ContentProperties contentProps;
    private final WxApiService wxApiService;
    private final UserMapper userMapper;
    private final MediaCheckMapper mediaCheckMapper;

    @PostMapping("/avatar")
    public R<Map<String, String>> avatar(@RequestParam("file") MultipartFile file) {
        UserContext.require();
        if (file == null || file.isEmpty()) {
            throw new BusinessException("没有收到图片");
        }
        long maxBytes = (long) props.getAvatarMaxMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessException("图片不能超过 " + props.getAvatarMaxMb() + "MB");
        }
        String ext = sniffImage(file);
        if (ext == null) {
            throw new BusinessException("只支持 jpg / png / webp 图片");
        }

        String month = MONTH.format(LocalDate.now());
        Path dir = Paths.get(props.getDir(), "avatar", month).toAbsolutePath().normalize();
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(name).toFile());
        } catch (IOException e) {
            log.error("头像保存失败 dir={}", dir, e);
            throw new BusinessException(500, "图片保存失败，请稍后再试");
        }

        String path = "/uploads/avatar/" + month + "/" + name;
        String base = props.getPublicBase() == null ? "" : props.getPublicBase().trim();
        String url = base.isEmpty() ? path : base.replaceAll("/+$", "") + path;
        submitMediaCheck(path, url);
        return R.ok(Map.of("url", url, "path", path));
    }

    /**
     * 头像异步送审（mediaCheckAsync）：best-effort，提交失败不阻塞上传。
     * 前提：AUDIT_MOCK=false 且 UPLOAD_PUBLIC_BASE 已配置（微信要能公网拉到图）。
     * 结果经 /api/wx/callback 推回，违规头像会被重置为空。
     */
    private void submitMediaCheck(String path, String url) {
        if (contentProps.isAuditMock()) {
            return;
        }
        if (!url.startsWith("http")) {
            log.warn("UPLOAD_PUBLIC_BASE 未配置，头像 {} 跳过微信异步送审", path);
            return;
        }
        try {
            Long userId = UserContext.get();
            if (userId == null) {
                return;
            }
            User user = userMapper.selectById(userId);
            if (user == null || user.getOpenid() == null || user.getOpenid().startsWith("mock_")) {
                return;
            }
            String traceId = wxApiService.mediaCheckAsync(url, user.getOpenid(), 1);
            MediaCheck mc = new MediaCheck();
            mc.setTraceId(traceId);
            mc.setUserId(userId);
            mc.setMediaPath(path);
            mc.setStatus(0);
            mediaCheckMapper.insert(mc);
        } catch (Exception e) {
            log.warn("头像异步送审提交失败 path={}（不阻塞上传）", path, e);
        }
    }

    /**
     * 按文件头判类型。扩展名和 Content-Type 都可伪造，这里不信它们。
     */
    private String sniffImage(MultipartFile file) {
        byte[] head = new byte[12];
        try (InputStream in = file.getInputStream()) {
            if (in.read(head) < 12) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if ((head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return "png";
        }
        if (head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E') {
            return "webp";
        }
        return null;
    }
}
