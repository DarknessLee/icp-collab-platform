/**
 * 应用级常量：软件名称/版本统一在此维护，UI、页面标题、登录页脚共用。
 */
export const APP_NAME = '企业内控与协同运营管理平台'
export const APP_VERSION = 'V1.0'
export const APP_SHORT_NAME = '内控协同'
export const APP_DEMO_LABEL = '演示 Demo'

/** 后端未启动时是否允许前端演示模式（Mock 登录，仅网络不可达时触发） */
export const MOCK_ENABLED = import.meta.env.VITE_ENABLE_MOCK !== 'false'

/** 登录验证码占位开关，M1 默认关闭 */
export const CAPTCHA_ENABLED = import.meta.env.VITE_ENABLE_CAPTCHA === 'true'
