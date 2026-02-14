package com.lhzkml.jasmine.core.prompt.executor

import com.lhzkml.jasmine.core.prompt.llm.ChatClientException
import com.lhzkml.jasmine.core.prompt.llm.ErrorType
import com.lhzkml.jasmine.core.prompt.llm.LLMProvider
import com.lhzkml.jasmine.core.prompt.llm.RetryConfig
import com.lhzkml.jasmine.core.prompt.llm.executeWithRetry
import com.lhzkml.jasmine.core.prompt.model.BalanceDetail
import com.lhzkml.jasmine.core.prompt.model.BalanceInfo
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

/**
 * 硅基流动客户端
 *
 * 余额查询: GET https://api.siliconflow.cn/v1/user/info
 * 认证: Authorization: Bearer <TOKEN>
 * 返回: { data: { balance: "0.88", chargeBalance: "88.00", totalBalance: "88.88" } }
 */
class SiliconFlowClient(
    apiKey: String,
    baseUrl: String = DEFAULT_BASE_URL,
    chatPath: String = "/v1/chat/completions",
    retryConfig: RetryConfig = RetryConfig.DEFAULT,
    httpClient: HttpClient? = null
) : OpenAICompatibleClient(apiKey, baseUrl, retryConfig, httpClient, chatPath) {

    companion object {
        const val DEFAULT_BASE_URL = "https://api.siliconflow.cn"
    }

    override val provider = LLMProvider.SiliconFlow

    @Serializable
    private data class SiliconFlowUserInfoResponse(
        val code: Int = 0,
        val message: String = "",
        val status: Boolean = false,
        val data: SiliconFlowUserData? = null
    )

    @Serializable
    private data class SiliconFlowUserData(
        val balance: String = "0",
        val chargeBalance: String = "0",
        val totalBalance: String = "0"
    )

    override suspend fun getBalance(): BalanceInfo {
        return executeWithRetry(retryConfig) {
            try {
                val response: HttpResponse = httpClient.get("${baseUrl}/v1/user/info") {
                    header("Authorization", "Bearer $apiKey")
                    accept(ContentType.Application.Json)
                }

                if (!response.status.isSuccess()) {
                    val body = try { response.bodyAsText() } catch (_: Exception) { null }
                    throw ChatClientException.fromStatusCode(provider.name, response.status.value, body)
                }

                val body = response.bodyAsText()
                val result = json.decodeFromString<SiliconFlowUserInfoResponse>(body)
                val data = result.data
                    ?: throw ChatClientException(provider.name, "响应中没有用户数据", ErrorType.PARSE_ERROR)

                val totalBalance = try { data.totalBalance.toDouble() } catch (_: Exception) { 0.0 }

                BalanceInfo(
                    isAvailable = totalBalance > 0,
                    balances = listOf(
                        BalanceDetail(
                            currency = "CNY",
                            totalBalance = data.totalBalance,
                            grantedBalance = data.balance,
                            toppedUpBalance = data.chargeBalance
                        )
                    )
                )
            } catch (e: ChatClientException) {
                throw e
            } catch (e: Exception) {
                throw ChatClientException(provider.name, "查询余额失败: ${e.message}", ErrorType.UNKNOWN, cause = e)
            }
        }
    }
}
