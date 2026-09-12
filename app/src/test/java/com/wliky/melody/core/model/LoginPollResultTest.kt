package com.wliky.melody.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 扫码状态码映射。
 *
 * 8821 是网易近年新增的风控码：命中后继续轮询毫无意义，
 * 必须停下来引导用户换一条路（Cookie 登录），这里把它固化下来防止回归。
 */
class LoginPollResultTest {

    @Test
    fun `标准状态码映射正确`() {
        assertTrue(LoginPollResult(801, "").isWaitingScan)
        assertTrue(LoginPollResult(802, "").isWaitingConfirm)
        assertTrue(LoginPollResult(803, "").isSuccess)
        assertTrue(LoginPollResult(800, "").isExpired)
        assertTrue(LoginPollResult(8821, "环境异常").isRiskControlled)
    }

    @Test
    fun `状态码之间互不串台`() {
        val success = LoginPollResult(803, "")
        assertFalse(success.isRiskControlled)
        assertFalse(success.isExpired)
        assertFalse(success.isWaitingScan)

        val risk = LoginPollResult(8821, "")
        assertFalse(risk.isSuccess)
        assertFalse(risk.isExpired)
        assertFalse(risk.isWaitingScan)
        assertFalse(risk.isWaitingConfirm)
    }

    @Test
    fun `未知状态码不会命中任何分支`() {
        val unknown = LoginPollResult(-1, "无法解析登录状态")
        assertFalse(unknown.isSuccess)
        assertFalse(unknown.isExpired)
        assertFalse(unknown.isWaitingScan)
        assertFalse(unknown.isWaitingConfirm)
        assertFalse(unknown.isRiskControlled)
    }
}
