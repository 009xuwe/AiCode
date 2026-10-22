package com.aicode.feature.agent.domain.provider

import com.aicode.feature.agent.domain.model.ReasoningCapability
import com.aicode.feature.agent.domain.model.ReasoningEffort
import com.aicode.feature.settings.domain.model.AIProviderConfig
import com.aicode.feature.settings.domain.model.ModelMetadata
import com.aicode.feature.settings.domain.model.ProviderType
import com.aicode.feature.settings.domain.model.modelMetadataKey

/**
 * 提供商配置 → 该模型实际走的协议面。
 * OpenAI 系要区分 chat / responses：两者的「关闭思考」可靠度不同（chat 常把 none 降成 low）。
 */
fun reasoningProtocolOf(provider: AIProviderConfig): ReasoningProtocol = when (provider.type) {
    ProviderType.ANTHROPIC -> ReasoningProtocol.ANTHROPIC
    ProviderType.GEMINI -> ReasoningProtocol.GEMINI
    ProviderType.OPENAI ->
        if (provider.useResponseApi) ReasoningProtocol.OPENAI_RESPONSES else ReasoningProtocol.OPENAI_CHAT
}

/**
 * UI 侧的能力解析：与适配器走同一套规则（models.dev 目录 + 厂商兜底表），
 * 保证弹层里能选什么、请求里实际发什么永远一致。
 */
fun reasoningCapabilityOf(
    provider: AIProviderConfig?,
    model: String,
    modelMetadata: Map<String, ModelMetadata>
): ReasoningCapability {
    if (provider == null || model.isBlank()) return ReasoningCapability.UNKNOWN
    val declared = modelMetadata[modelMetadataKey(provider.id, model)]?.toReasoningCapability()
    return ReasoningResolver.capability(reasoningProtocolOf(provider), model, declared)
}

/** 打开思考时应回到哪一档：优先服务端默认档，其次「中」，最后取最高可用档。 */
fun ReasoningCapability.preferredOnLevel(): ReasoningEffort? =
    defaultLevel?.takeIf { it in levels }
        ?: levels.firstOrNull { it == ReasoningEffort.MEDIUM }
        ?: levels.lastOrNull()

/**
 * 弹层里要展示的档位（不含「关闭」，关闭由开关表达）：
 * - 能力已知：只列模型真实支持的档位；
 * - 能力未知（中转站改名 / 自定义模型）：退回全部档位由用户判断，UI 会同时给出「可能不生效」提示。
 */
fun ReasoningCapability.selectableLevelsForUi(): List<ReasoningEffort> =
    if (isKnown) levels else ReasoningEffort.entries.filter { it != ReasoningEffort.NONE }

/** 是否给「思考」总开关：能关闭，或能力未知（交给用户试）。 */
fun ReasoningCapability.showsThinkingToggle(): Boolean = canDisable || !isKnown
