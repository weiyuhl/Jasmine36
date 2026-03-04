package com.lhzkml.jasmine.mnn

import com.google.gson.annotations.SerializedName

data class MnnModelConfig(
    @SerializedName("llm_model") var llmModel: String? = null,
    @SerializedName("llm_weight") var llmWeight: String? = null,
    @SerializedName("backend_type") var backendType: String? = "cpu",
    @SerializedName("thread_num") var threadNum: Int? = 4,
    @SerializedName("precision") var precision: String? = "low",
    @SerializedName("use_mmap") var useMmap: Boolean? = false,
    @SerializedName("memory") var memory: String? = "low",
    @SerializedName("sampler_type") var samplerType: String? = "mixed",
    @SerializedName("temperature") var temperature: Float? = 0.6f,
    @SerializedName("topP") var topP: Float? = 0.95f,
    @SerializedName("topK") var topK: Int? = 20,
    @SerializedName("minP") var minP: Float? = 0.05f,
    @SerializedName("tfs_z") var tfsZ: Float? = 1.0f,
    @SerializedName("typical") var typical: Float? = 0.95f,
    @SerializedName("penalty") var penalty: Float? = 1.02f,
    @SerializedName("n_gram") var nGram: Int? = 8,
    @SerializedName("n_gram_factor") var nGramFactor: Float? = 1.02f,
    @SerializedName("max_new_tokens") var maxNewTokens: Int? = 2048
)

data class MnnModelInfo(
    val modelId: String,
    val modelName: String,
    val modelPath: String,
    val sizeBytes: Long,
    val vendor: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val isDownloaded: Boolean = false,
    val config: MnnModelConfig? = null
)

data class MnnMarketModel(
    val modelName: String = "",
    val vendor: String = "",
    val description: String = "",
    @SerializedName("size_gb") val sizeB: Double = 0.0,
    @SerializedName("file_size") val fileSize: Long = 0,
    val tags: List<String> = emptyList(),
    @SerializedName("extra_tags") val extraTags: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val sources: Map<String, String> = emptyMap(),
    @SerializedName("min_app_version") val minAppVersion: String = ""
) {
    val modelId: String get() {
        val src = sources["ModelScope"] ?: sources["HuggingFace"] ?: sources.values.firstOrNull()
        return src ?: modelName
    }
    val sizeCategory: String get() = when {
        sizeB < 1.0 -> "<1B"
        sizeB < 5.0 -> "1B-5B"
        sizeB < 15.0 -> "5B-15B"
        else -> ">15B"
    }
}

data class MnnMarketData(
    val version: String = "1",
    val tagTranslations: Map<String, String> = emptyMap(),
    val quickFilterTags: List<String> = emptyList(),
    val vendorOrder: List<String> = emptyList(),
    val models: List<MnnMarketModel> = emptyList()
)

enum class MnnDownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    PAUSED,
    DOWNLOADED,
    ERROR
}

enum class MnnDownloadSource(val displayName: String) {
    HUGGING_FACE("HuggingFace"),
    MODEL_SCOPE("魔搭"),
    MODELERS("魔乐");

    companion object {
        fun fromString(s: String): MnnDownloadSource = when (s) {
            "HuggingFace" -> HUGGING_FACE
            "ModelScope", "魔搭" -> MODEL_SCOPE
            "Modelers", "魔乐" -> MODELERS
            else -> MODEL_SCOPE
        }
    }
}

data class MnnFilterState(
    val tags: Set<String> = emptySet(),
    val sizeCategories: Set<String> = emptySet(),
    val vendors: Set<String> = emptySet(),
    val downloadState: MnnDownloadFilterState? = null
) {
    val isEmpty: Boolean get() = tags.isEmpty() && sizeCategories.isEmpty() &&
        vendors.isEmpty() && downloadState == null
    val activeCount: Int get() = tags.size + sizeCategories.size +
        vendors.size + (if (downloadState != null) 1 else 0)
}

enum class MnnDownloadFilterState(val displayName: String) {
    DOWNLOADED("已下载"),
    NOT_DOWNLOADED("未下载"),
    DOWNLOADING("下载中")
}

data class MnnDownloadTask(
    val modelId: String,
    val modelName: String,
    val source: MnnDownloadSource,
    val repoPath: String,
    var state: MnnDownloadState = MnnDownloadState.DOWNLOADING,
    var progress: Float = 0f,
    var downloadedBytes: Long = 0,
    var totalBytes: Long = 0,
    var error: String? = null
)
