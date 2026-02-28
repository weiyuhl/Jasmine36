package com.lhzkml.jasmine.core.agent.a2a.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Agent 名片 �?A2A 协议�?Agent 的自描述清单
 * 完整移植 koog �?AgentCard.kt
 *
 * 提供 Agent 的身份、能力、技能、通信方式和安全要求等元数据�?
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AgentCard(
    @EncodeDefault
    val protocolVersion: String = "0.3.0",
    val name: String,
    val description: String,
    val url: String,
    @EncodeDefault
    val preferredTransport: TransportProtocol = TransportProtocol.JSONRPC,
    val additionalInterfaces: List<AgentInterface>? = null,
    val iconUrl: String? = null,
    val provider: AgentProvider? = null,
    val version: String,
    val documentationUrl: String? = null,
    val capabilities: AgentCapabilities,
    val securitySchemes: Map<String, SecurityScheme>? = null,
    val security: List<Map<String, List<String>>>? = null,
    val defaultInputModes: List<String>,
    val defaultOutputModes: List<String>,
    val skills: List<AgentSkill>,
    val supportsAuthenticatedExtendedCard: Boolean? = false,
    val signatures: List<AgentCardSignature>? = null
) {
    init {
        additionalInterfaces?.let { interfaces ->
            requireNotNull(interfaces.find { it.url == url && it.transport == preferredTransport }) {
                "If additionalInterfaces are specified, they must include an entry matching the main 'url' and 'preferredTransport'."
            }
        }
    }
}


/** 传输协议 */
@Serializable
data class TransportProtocol(val value: String) {
    companion object {
        val JSONRPC = TransportProtocol("JSONRPC")
        val HTTP_JSON_REST = TransportProtocol("HTTP+JSON/REST")
        val GRPC = TransportProtocol("GRPC")
    }
}

/** Agent 接口（URL + 传输协议组合�?*/
@Serializable
data class AgentInterface(
    val url: String,
    val transport: TransportProtocol
)

/** Agent 服务提供�?*/
@Serializable
data class AgentProvider(
    val organization: String,
    val url: String
)

/** Agent 能力声明 */
@Serializable
data class AgentCapabilities(
    val streaming: Boolean? = null,
    val pushNotifications: Boolean? = null,
    val stateTransitionHistory: Boolean? = null,
    val extensions: List<AgentExtension>? = null
)

/** 协议扩展 */
@Serializable
data class AgentExtension(
    val uri: String,
    val description: String? = null,
    val required: Boolean? = null,
    val params: Map<String, JsonElement>? = null
)

// ========== 安全方案 ==========

/** 安全方案基接�?*/
@Serializable
sealed interface SecurityScheme {
    val type: String
}

/** API Key 安全方案 */
@Serializable
@SerialName("apiKey")
@OptIn(ExperimentalSerializationApi::class)
data class APIKeySecurityScheme(
    @SerialName("in") val location: ApiKeyLocation,
    val name: String,
    val description: String? = null
) : SecurityScheme {
    @EncodeDefault
    override val type: String = "apiKey"
}

@Serializable
enum class ApiKeyLocation {
    @SerialName("cookie") Cookie,
    @SerialName("header") Header,
    @SerialName("query") Query
}

/** HTTP 认证安全方案 */
@Serializable
@SerialName("http")
@OptIn(ExperimentalSerializationApi::class)
data class HTTPAuthSecurityScheme(
    val scheme: String,
    val bearerFormat: String? = null,
    val description: String? = null
) : SecurityScheme {
    @EncodeDefault
    override val type: String = "http"
}

/** OAuth2 安全方案 */
@Serializable
@SerialName("oauth2")
@OptIn(ExperimentalSerializationApi::class)
data class OAuth2SecurityScheme(
    val flows: OAuthFlows,
    val oauth2MetadataUrl: String? = null,
    val description: String? = null
) : SecurityScheme {
    @EncodeDefault
    override val type: String = "oauth2"
}

@Serializable
data class OAuthFlows(
    val authorizationCode: AuthorizationCodeOAuthFlow? = null,
    val clientCredentials: ClientCredentialsOAuthFlow? = null,
    val implicit: ImplicitOAuthFlow? = null,
    val password: PasswordOAuthFlow? = null
)

@Serializable
data class AuthorizationCodeOAuthFlow(
    val authorizationUrl: String,
    val tokenUrl: String,
    val scopes: Map<String, String>,
    val refreshUrl: String? = null
)

@Serializable
data class ClientCredentialsOAuthFlow(
    val tokenUrl: String,
    val scopes: Map<String, String>,
    val refreshUrl: String? = null
)

@Serializable
data class ImplicitOAuthFlow(
    val authorizationUrl: String,
    val scopes: Map<String, String>,
    val refreshUrl: String? = null
)

@Serializable
data class PasswordOAuthFlow(
    val tokenUrl: String,
    val scopes: Map<String, String>,
    val refreshUrl: String? = null
)

/** OpenID Connect 安全方案 */
@Serializable
@SerialName("openIdConnect")
@OptIn(ExperimentalSerializationApi::class)
data class OpenIdConnectSecurityScheme(
    val openIdConnectUrl: String,
    val description: String? = null
) : SecurityScheme {
    @EncodeDefault
    override val type: String = "openIdConnect"
}

/** mTLS 安全方案 */
@Serializable
@SerialName("mutualTLS")
@OptIn(ExperimentalSerializationApi::class)
data class MutualTLSSecurityScheme(
    val description: String? = null
) : SecurityScheme {
    @EncodeDefault
    override val type: String = "mutualTLS"
}

// ========== 技�?==========

/** Agent 技�?*/
@Serializable
data class AgentSkill(
    val id: String,
    val name: String,
    val description: String,
    val tags: List<String>,
    val examples: List<String>? = null,
    val inputModes: List<String>? = null,
    val outputModes: List<String>? = null,
    val security: List<Map<String, List<String>>>? = null
)

/** Agent 名片签名 (JWS) */
@Serializable
data class AgentCardSignature(
    @SerialName("protected") val protectedHeader: String,
    val signature: String,
    val header: Map<String, JsonElement>? = null
)
