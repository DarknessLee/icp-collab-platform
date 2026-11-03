package com.icp.icp.support;

import java.time.LocalDate;

/**
 * 业务编码生成器（docs/04 §5）。
 *
 * <p>规则：前缀-yyyy-序号，序号按当前表中最大编码 +1；制度 ZD-yyyy-####、风险 FX-yyyy-###、
 * 控制点 KZ-yyyy-###、矩阵 JC-yyyy-###、评价计划 PJ-yyyy-##、缺陷 QX-yyyy-###、整改 ZG-yyyy-###；
 * 评价任务为 {plan_code}-##。</p>
 */
public final class CodeSeq {

    private CodeSeq() {
    }

    public static String next(String maxCode, String prefix, int width) {
        int year = LocalDate.now().getYear();
        int seq = 1;
        if (maxCode != null && !maxCode.isEmpty()) {
            int idx = maxCode.lastIndexOf('-');
            if (idx >= 0 && idx < maxCode.length() - 1) {
                try {
                    seq = Integer.parseInt(maxCode.substring(idx + 1)) + 1;
                } catch (NumberFormatException ignored) {
                    seq = 1;
                }
            }
        }
        return prefix + "-" + year + "-" + String.format("%0" + width + "d", seq);
    }

    /**
     * 评价任务编码：{plan_code}-##（与评价计划编码冲突提醒见 docs/04 §5）。
     */
    public static String nextTaskCode(String planCode, String maxTaskCode) {
        int seq = 1;
        if (maxTaskCode != null && !maxTaskCode.isEmpty()) {
            int idx = maxTaskCode.lastIndexOf('-');
            if (idx >= 0 && idx < maxTaskCode.length() - 1) {
                try {
                    seq = Integer.parseInt(maxTaskCode.substring(idx + 1)) + 1;
                } catch (NumberFormatException ignored) {
                    seq = 1;
                }
            }
        }
        return planCode + "-" + String.format("%02d", seq);
    }
}
