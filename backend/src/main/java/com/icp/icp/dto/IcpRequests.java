package com.icp.icp.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 内控域（M2b）请求体集合。通用查询参数直接走 @RequestParam，只有动作类接口用这里的 record。
 */
public final class IcpRequests {

    private IcpRequests() {
    }

    /** 通用状态变更（风险启停等）。 */
    public record Status(Integer status) {
    }

    /** 制度发布：可选生效/失效日期（缺省沿用实体已有值）。 */
    public record Publish(LocalDate effectiveDate, LocalDate expireDate) {
    }

    /** 评价任务提交：分数 + 结论 + 底稿（底稿可选，一次提交会落一条 icp_evaluation_workpaper）。 */
    public record TaskSubmit(@NotNull BigDecimal score, @NotNull Integer conclusion, Workpaper workpaper) {
    }

    /** 底稿：evidence 为附件 id 数组，落库时序列化为 JSON 字符串。 */
    public record Workpaper(String sampleDesc, String testDesc, Integer testResult,
                            List<Long> evidence, String conclusion) {
    }

    /** 缺陷发起整改：生成整改单。 */
    public record DefectRectify(String rectifyPlan, Long responsibleUserId, LocalDate deadline) {
    }

    /** 整改提交（附整改证据）。 */
    public record RectifySubmit(String evidence) {
    }

    /** 整改复核意见（approve/reject 共用）。 */
    public record RectifyReview(String reviewOpinion) {
    }
}
