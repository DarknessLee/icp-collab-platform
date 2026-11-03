package com.icp.system.service;

import com.icp.common.api.ErrorCode;
import com.icp.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 密码策略单测：默认 8 位；配置复杂度后必须字母+数字。
 */
@ExtendWith(MockitoExtension.class)
class PasswordPolicyServiceTest {

    @Mock
    private ConfigService configService;

    private PasswordPolicyService passwordPolicyService;

    @BeforeEach
    void setUp() {
        passwordPolicyService = new PasswordPolicyService(configService);
        lenient().when(configService.getInt(PasswordPolicyService.KEY_MIN_LENGTH, 8)).thenReturn(8);
        lenient().when(configService.getBoolean(PasswordPolicyService.KEY_REQUIRE_COMPLEX, false))
                .thenReturn(false);
    }

    @Test
    void tooShortPasswordRejected() {
        assertThatThrownBy(() -> passwordPolicyService.validate("abc123"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void blankPasswordRejected() {
        assertThatThrownBy(() -> passwordPolicyService.validate("  "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void defaultPolicyAllowsLettersOnly() {
        passwordPolicyService.validate("abcdefgh");
        assertThat(passwordPolicyService.minLength()).isEqualTo(8);
    }

    @Test
    void complexPolicyRequiresLetterAndDigit() {
        when(configService.getBoolean(PasswordPolicyService.KEY_REQUIRE_COMPLEX, false)).thenReturn(true);

        assertThatThrownBy(() -> passwordPolicyService.validate("abcdefgh"))
                .isInstanceOf(BusinessException.class);
        passwordPolicyService.validate("abcd1234");
    }
}
