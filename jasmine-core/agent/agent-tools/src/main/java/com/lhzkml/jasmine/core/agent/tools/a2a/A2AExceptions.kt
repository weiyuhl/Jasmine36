package com.lhzkml.jasmine.core.agent.tools.a2a

/**
 * A2A 协议异常体系
 * 完整移植 koog 的 a2a exceptions
 */

/** A2A 基础异常 */
open class A2AException(
    message: String,
    val code: Int = -32000,
    cause: Throwable? = null
) : Exception(message, cause)

/** 任务未找到 */
class A2ATaskNotFoundException(
    message: String, cause: Throwable? = null
) : A2AException(message, -32001, cause)

/** 任务不可取消 */
class A2ATaskNotCancelableException(
    message: String, cause: Throwable? = null
) : A2AException(message, -32002, cause)

/** 推送通知不支持 */
class A2APushNotificationNotSupportedException(
    message: String, cause: Throwable? = null
) : A2AException(message, -32003, cause)

/** 不支持的操作 */
class A2AUnsupportedOperationException(
    message: String, cause: Throwable? = null
) : A2AException(message, -32004, cause)

/** 内容类型不支持 */
class A2AContentTypeNotSupportedException(
    message: String, cause: Throwable? = null
) : A2AException(message, -32005, cause)

/** 无效参数 */
class A2AInvalidParamsException(
    message: String, cause: Throwable? = null
) : A2AException(message, -32602, cause)

/** 认证扩展名片未配置 */
class A2AAuthenticatedExtendedCardNotConfiguredException(
    message: String, cause: Throwable? = null
) : A2AException(message, -32006, cause)

// ========== 服务端内部异常 ==========

/** 任务操作异常 */
class TaskOperationException(
    message: String, cause: Throwable? = null
) : Exception(message, cause)

/** 消息操作异常 */
class MessageOperationException(
    message: String, cause: Throwable? = null
) : Exception(message, cause)

/** 无效事件异常 */
class InvalidEventException(
    message: String, cause: Throwable? = null
) : Exception(message, cause)

/** 推送通知异常 */
class PushNotificationException(
    message: String, cause: Throwable? = null
) : Exception(message, cause)

/** 会话非活跃异常 */
class SessionNotActiveException(
    message: String, cause: Throwable? = null
) : Exception(message, cause)
