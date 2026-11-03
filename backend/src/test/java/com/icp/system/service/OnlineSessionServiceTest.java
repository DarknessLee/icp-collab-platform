package com.icp.system.service;

import com.icp.common.api.ErrorCode;
import com.icp.common.exception.BusinessException;
import com.icp.system.dto.OnlineSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 在线会话单测：登记可见、吊销后不可用、按用户批量下线、关键字过滤。
 */
class OnlineSessionServiceTest {

    @Test
    void registeredSessionIsListedAndTouched() {
        OnlineSessionService service = new OnlineSessionService();
        service.register("jti-1", 1L, "admin", "系统管理员", "信息技术部", "127.0.0.1", "junit", 7200);
        service.touch("jti-1");

        assertThat(service.list(1, 10, null).getList()).hasSize(1);
        assertThat(service.list(1, 10, "admin").getTotal()).isEqualTo(1);
        assertThat(service.isRevoked("jti-1")).isFalse();
    }

    @Test
    void revokedSessionIsRejectedUntilExpiry() {
        OnlineSessionService service = new OnlineSessionService();
        service.register("jti-2", 2L, "exec", "王建国", "总经理办公室", "127.0.0.1", "junit", 7200);
        service.revoke("jti-2");

        assertThat(service.isRevoked("jti-2")).isTrue();
        assertThatThrownBy(() -> service.require("jti-2")).isInstanceOf(BusinessException.class);
        assertThat(service.list(1, 10, null).getList()).isEmpty();
    }

    @Test
    void revokeByUserRemovesAllSessions() {
        OnlineSessionService service = new OnlineSessionService();
        service.register("jti-3", 3L, "icpadmin", "李静", "内控合规部", "127.0.0.1", "junit", 7200);
        service.register("jti-4", 3L, "icpadmin", "李静", "内控合规部", "127.0.0.2", "junit", 7200);
        service.revokeByUser(3L);

        assertThat(service.isRevoked("jti-3")).isTrue();
        assertThat(service.isRevoked("jti-4")).isTrue();
        assertThat(service.list(1, 10, null).getTotal()).isZero();
    }

    @Test
    void missingSessionRaisesNotFound() {
        OnlineSessionService service = new OnlineSessionService();
        assertThatThrownBy(() -> service.require("missing"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
        OnlineSession session = new OnlineSession();
        session.setJti("x");
        assertThat(session.getJti()).isEqualTo("x");
    }
}
