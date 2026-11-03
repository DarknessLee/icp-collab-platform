package com.icp.system.service;

import com.icp.common.api.ErrorCode;
import com.icp.common.exception.BusinessException;
import org.springframework.stereotype.Service;

/**
 * 密码策略：从 sys_config 读取 sys.password.minLength / sys.password.requireComplex，
 * 缺省 8 位、不强制复杂度（种子未预置这两个 key，读取不到时使用默认值）。
 */
@Service
public class PasswordPolicyService {

    public static final String KEY_MIN_LENGTH = "sys.password.minLength";
    public static final String KEY_REQUIRE_COMPLEX = "sys.password.requireComplex";

    private static final int DEFAULT_MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 64;

    private final ConfigService configService;

    public PasswordPolicyService(ConfigService configService) {
        this.configService = configService;
    }

    public int minLength() {
        return configService.getInt(KEY_MIN_LENGTH, DEFAULT_MIN_LENGTH);
    }

    public boolean requireComplex() {
        return configService.getBoolean(KEY_REQUIRE_COMPLEX, false);
    }

    public void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码不能为空");
        }
        int min = minLength();
        if (password.length() < min) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码长度不能少于 " + min + " 位");
        }
        if (password.length() > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码长度不能超过 " + MAX_LENGTH + " 位");
        }
        if (requireComplex()) {
            boolean hasLetter = password.chars().anyMatch(Character::isLetter);
            boolean hasDigit = password.chars().anyMatch(Character::isDigit);
            if (!hasLetter || !hasDigit) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "密码必须同时包含字母和数字");
            }
        }
    }
}
