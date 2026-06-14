package com.surg.extract.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Async("notifyExecutor")
    public void sendEmailNotification(String to, String subject, String content) {
        if (!StringUtils.hasText(to) || !to.contains("@")) {
            log.warn("邮箱地址无效: {}", to);
            return;
        }
        if (!StringUtils.hasText(mailFrom)) {
            log.warn("发件人邮箱未配置，跳过邮件发送: to={}", to);
            return;
        }
        try {
            jakarta.mail.internet.MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(to.split(",|;"));
            helper.setSubject(subject);
            helper.setText(content, true);
            mailSender.send(message);
            log.info("邮件通知发送成功: {}", to);
        } catch (Exception e) {
            log.error("邮件通知发送失败: {}, error: {}", to, e.getMessage(), e);
        }
    }

    public void sendBatchCompleteNotification(String notifyType, String notifyTarget,
                                               String taskName, int total, int success, int failed) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        double successRate = total > 0 ? (success * 100.0 / total) : 0;

        String subject = String.format("【批量处理完成】%s - 成功率 %.1f%%", taskName, successRate);
        StringBuilder content = new StringBuilder();
        content.append("<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>");
        content.append("<h2 style='color: #1677ff;'>批量处理任务完成通知</h2>");
        content.append("<table style='width: 100%; border-collapse: collapse; margin-top: 20px;'>");
        content.append("<tr><td style='padding: 10px; border-bottom: 1px solid #e5e7eb; font-weight: bold; width: 120px;'>任务名称:</td>")
                .append("<td style='padding: 10px; border-bottom: 1px solid #e5e7eb;'>").append(taskName).append("</td></tr>");
        content.append("<tr><td style='padding: 10px; border-bottom: 1px solid #e5e7eb; font-weight: bold;'>完成时间:</td>")
                .append("<td style='padding: 10px; border-bottom: 1px solid #e5e7eb;'>").append(time).append("</td></tr>");
        content.append("<tr><td style='padding: 10px; border-bottom: 1px solid #e5e7eb; font-weight: bold;'>总文件数:</td>")
                .append("<td style='padding: 10px; border-bottom: 1px solid #e5e7eb;'>").append(total).append("</td></tr>");
        content.append("<tr><td style='padding: 10px; border-bottom: 1px solid #e5e7eb; font-weight: bold;'>成功数:</td>")
                .append("<td style='padding: 10px; border-bottom: 1px solid #e5e7eb; color: #52c41a;'>").append(success).append("</td></tr>");
        content.append("<tr><td style='padding: 10px; border-bottom: 1px solid #e5e7eb; font-weight: bold;'>失败数:</td>")
                .append("<td style='padding: 10px; border-bottom: 1px solid #e5e7eb; color: ").append(failed > 0 ? "#ff4d4f" : "#8c8c8c").append(";'>")
                .append(failed).append("</td></tr>");
        content.append("<tr><td style='padding: 10px; font-weight: bold;'>成功率:</td>")
                .append("<td style='padding: 10px;'>")
                .append(String.format("<span style='font-size: 20px; font-weight: bold; color: %s;'>%.1f%%</span>",
                        successRate >= 80 ? "#52c41a" : successRate >= 60 ? "#faad14" : "#ff4d4f", successRate))
                .append("</td></tr>");
        content.append("</table>");
        content.append("<div style='margin-top: 20px; padding: 15px; background-color: #f6ffed; border: 1px solid #b7eb8f; border-radius: 4px;'>");
        content.append("请登录系统查看详细结果，失败文件可重试处理。");
        content.append("</div>");
        content.append("</div>");

        sendEmailNotification(notifyTarget, subject, content.toString());
    }
}
