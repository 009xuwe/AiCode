package com.aicode.feature.agent.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicode.R
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.feature.agent.presentation.SubAgentCardState
import com.aicode.feature.agent.presentation.SubAgentCardStatus
import androidx.compose.ui.res.stringResource
import compose.icons.FeatherIcons
import compose.icons.feathericons.Cpu
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * `task` 工具的专属卡片宿主：状态与操作用 CompositionLocal 下发，避免把两个参数一路穿过
 * 消息列表、分组与气泡三层组合。
 *
 * 只放演示层需要的东西：按子会话 id 索引的实时状态 + 「进入会话 / 停止」两个动作。
 */
data class SubAgentCardHost(
    val states: Map<String, SubAgentCardState> = emptyMap(),
    val onOpen: (String) -> Unit = {},
    val onStop: (String) -> Unit = {}
)

val LocalSubAgentCardHost = staticCompositionLocalOf<SubAgentCardHost?> { null }

/** 从 `task(create)` 的工具参数与结果里解析出的派发信息；工具结果缺失时（流式中）也可能只有参数。 */
internal data class ParsedSubAgentDispatch(
    val subSessionId: String?,
    val agentName: String?,
    val description: String,
    /** 结果里回显的档位（inherit 表示继承父会话）。 */
    val tier: String?,
    val model: String?,
    val warning: String?
)

private val json = Json { ignoreUnknownKeys = true }

/**
 * 解析 `task(create)`：参数给「派的是什么活」，结果给「派到哪个会话、用了什么档位/模型」。
 *
 * 结果里 `data` 既可能是对象也可能是被序列化成字符串的对象（两种传输形态都出现过），
 * 与 [parseWebSearchResult] 同样做双重容错；解析不出 id 时卡片仍按参数渲染，只是不能跳转。
 */
internal fun parseSubAgentDispatch(argsJson: String?, content: String?): ParsedSubAgentDispatch? {
    val args = argsJson?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
    val action = (args?.get("action") as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase() ?: "create"
    // 只给 create 出卡片；read/send/stop/list 仍是普通工具行。
    if (action != "create") return null

    val result = content?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
    // transport 用 status 作判别字段（见 ToolResult 的序列化配置）。
    val status = (result?.get("status") as? JsonPrimitive)?.contentOrNull
    if (status == "error") {
        // 派发失败（如子代理定义不存在）：交给通用工具行展示错误，不显示一张"正在运行"的卡。
        return null
    }
    val data = result?.get("data").let { element ->
        when (element) {
            is JsonObject -> element
            is JsonPrimitive -> runCatching { json.parseToJsonElement(element.content).jsonObject }.getOrNull()
            else -> null
        }
    }

    val agentName = (data?.get("agent") as? JsonPrimitive)?.contentOrNull
        ?: (args?.get("agent") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    val description = (args?.get("description") as? JsonPrimitive)?.contentOrNull?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: (args?.get("prompt") as? JsonPrimitive)?.contentOrNull?.trim()
            ?.lineSequence()?.firstOrNull()?.trim().orEmpty()

    return ParsedSubAgentDispatch(
        subSessionId = (data?.get("id") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() },
        agentName = agentName,
        description = description,
        tier = (data?.get("tier") as? JsonPrimitive)?.contentOrNull,
        model = (data?.get("model") as? JsonPrimitive)?.contentOrNull,
        warning = (data?.get("warning") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    )
}

/**
 * 子代理派发卡片：替代 `task` 的普通工具行。
 *
 * 两种形态共用一张卡——派发时显示「谁在做什么、跑在哪个模型上」，结束后同一张卡换成结果摘要与
 * 改动文件。实时状态来自 [LocalSubAgentCardHost]；查不到（历史消息 / 进程重启）就退化为静态卡。
 */
@Composable
internal fun SubAgentDispatchCard(dispatch: ParsedSubAgentDispatch) {
    val host = LocalSubAgentCardHost.current
    val state = dispatch.subSessionId?.let { host?.states?.get(it) }
    val status = state?.status ?: SubAgentCardStatus.COMPLETED
    val running = status == SubAgentCardStatus.RUNNING
    var excerptExpanded by remember(dispatch.subSessionId) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.mdLarge),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = FeatherIcons.Cpu,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = dispatch.agentName ?: stringResource(R.string.subagent_card_default_agent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                modelBadge(dispatch)?.let { badge ->
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.weight(1f))
                SubAgentStatusChip(status = status, elapsedMs = state?.elapsedMs)
            }

            if (dispatch.description.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = dispatch.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (excerptExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val excerpt = state?.excerpt?.takeIf { it.isNotBlank() }
            if (excerpt != null) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = excerpt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (excerptExpanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        if (excerptExpanded) R.string.subagent_card_collapse else R.string.subagent_card_expand
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = Spacing.xs)
                        .clickable { excerptExpanded = !excerptExpanded }
                )
            }

            val files = state?.changedFiles.orEmpty()
            if (files.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.subagent_card_changed_files, files.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.xs))
                files.take(MAX_FILE_CHIPS).forEach { path ->
                    Text(
                        text = path.substringAfterLast('/'),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(Radius.sm)
                            )
                            .padding(horizontal = Spacing.xs, vertical = 1.dp)
                    )
                }
            }

            dispatch.warning?.let { warning ->
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = warning,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (dispatch.subSessionId != null && (running || status != SubAgentCardStatus.COMPLETED)) {
                Spacer(Modifier.height(Spacing.xs))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (running) {
                        Text(
                            text = stringResource(R.string.subagent_card_stop),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable { host?.onStop?.invoke(dispatch.subSessionId) }
                        )
                    }
                    Text(
                        text = stringResource(R.string.subagent_card_open),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { host?.onOpen?.invoke(dispatch.subSessionId) }
                    )
                }
            } else if (dispatch.subSessionId != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.subagent_card_open),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { host?.onOpen?.invoke(dispatch.subSessionId) }
                )
            }
        }
    }
}

/** 档位 + 模型：档位是「轻量/均衡/强力」，模型是实际落地的 model id；inherit 时不显示模型。 */
@Composable
private fun modelBadge(dispatch: ParsedSubAgentDispatch): String? {
    val tier = tierLabel(dispatch.tier)
    val model = dispatch.model?.takeIf { it.isNotBlank() && it != "inherit" }
    return when {
        tier != null && model != null -> "$tier · $model"
        tier != null -> tier
        model != null -> model
        else -> null
    }
}

@Composable
private fun tierLabel(tier: String?): String? = when (tier?.lowercase()) {
    "light" -> stringResource(R.string.settings_subagent_tier_light)
    "standard" -> stringResource(R.string.settings_subagent_tier_standard)
    "strong" -> stringResource(R.string.settings_subagent_tier_strong)
    else -> null
}

@Composable
private fun SubAgentStatusChip(status: SubAgentCardStatus, elapsedMs: Long?) {
    val (labelRes, tint) = when (status) {
        SubAgentCardStatus.RUNNING -> R.string.subagent_card_status_running to MaterialTheme.colorScheme.primary
        SubAgentCardStatus.COMPLETED -> R.string.subagent_card_status_completed to MaterialTheme.colorScheme.primary
        SubAgentCardStatus.FAILED -> R.string.subagent_card_status_failed to MaterialTheme.colorScheme.error
        SubAgentCardStatus.STOPPED -> R.string.subagent_card_status_stopped to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (status == SubAgentCardStatus.RUNNING) {
            StatusDot(color = tint)
            Spacer(Modifier.width(Spacing.xs / 2))
        }
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
        if (elapsedMs != null && elapsedMs > 0) {
            Spacer(Modifier.width(Spacing.xs / 2))
            Text(
                text = formatElapsed(elapsedMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(5.dp)
            .background(color = color, shape = CircleShape)
    )
}

private fun formatElapsed(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m${seconds % 60}s"
}

/** 卡片上最多列出几个改动文件；再多对一眼扫读没意义，进会话看全量。 */
private const val MAX_FILE_CHIPS = 4
