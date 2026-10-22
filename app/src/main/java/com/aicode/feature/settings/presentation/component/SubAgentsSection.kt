package com.aicode.feature.settings.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicode.R
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.core.theme.semanticColors
import com.aicode.core.ui.SwipeToDeleteRow
import com.aicode.feature.agent.domain.subagent.AgentDefinitionScope
import com.aicode.feature.settings.presentation.SubAgentUiEntry
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.Users

/**
 * 子代理二级页：与「技能」一致的折叠分组列表——「当前项目 / 全局」两组各自可折叠，
 * 每行一个子代理（图标 + 名称 + 描述 + 模型标签），左滑删除，点击行进入详情。
 * 新建入口在顶栏的「＋」，启用开关与编辑入口在详情页。
 *
 * 列表走 [LazyColumn] 只组合可见行：子代理数量随项目增长，一次组合全部行会明显掉帧。
 */
@Composable
internal fun SubAgentsSection(
    projectName: String?,
    entries: List<SubAgentUiEntry>,
    onDelete: (SubAgentUiEntry) -> Unit,
    onOpenDetail: (SubAgentUiEntry) -> Unit
) {
    // 只在数据变化时分组，避免每次重组都产生两份新 List。
    val (projectAgents, globalAgents) = remember(entries) {
        entries.filter { it.scope == AgentDefinitionScope.PROJECT } to
            entries.filter { it.scope == AgentDefinitionScope.GLOBAL }
    }

    if (entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl, vertical = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.lg)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        FeatherIcons.Users,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(R.string.subagents_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.subagents_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    var projectExpanded by rememberSaveable { mutableStateOf(true) }
    var globalExpanded by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        item(key = "project-header", contentType = "header") {
            CollapsibleGroupHeader(
                text = if (projectName != null) {
                    stringResource(R.string.perm_current_project, projectName)
                } else {
                    stringResource(R.string.perm_current_project_none)
                },
                expanded = projectExpanded,
                onToggle = { projectExpanded = !projectExpanded }
            )
        }
        if (projectExpanded) {
            if (projectAgents.isEmpty()) {
                item(key = "project-empty", contentType = "empty") {
                    SettingsGroup { SubAgentEmptyHint(stringResource(R.string.subagents_no_project)) }
                }
            } else {
                itemsIndexed(
                    items = projectAgents,
                    key = { _, entry -> "project:${entry.name}" },
                    contentType = { _, _ -> "agent-row" }
                ) { index, entry ->
                    LazyGroupItem(index = index, lastIndex = projectAgents.lastIndex) {
                        SubAgentRow(
                            entry = entry,
                            onDelete = { onDelete(entry) },
                            onClick = { onOpenDetail(entry) }
                        )
                    }
                }
            }
        }

        item(key = "global-header", contentType = "header") {
            CollapsibleGroupHeader(
                text = stringResource(R.string.perm_global),
                expanded = globalExpanded,
                onToggle = { globalExpanded = !globalExpanded }
            )
        }
        if (globalExpanded) {
            if (globalAgents.isEmpty()) {
                item(key = "global-empty", contentType = "empty") {
                    SettingsGroup { SubAgentEmptyHint(stringResource(R.string.subagents_no_global)) }
                }
            } else {
                itemsIndexed(
                    items = globalAgents,
                    key = { _, entry -> "global:${entry.name}" },
                    contentType = { _, _ -> "agent-row" }
                ) { index, entry ->
                    LazyGroupItem(index = index, lastIndex = globalAgents.lastIndex) {
                        SubAgentRow(
                            entry = entry,
                            onDelete = { onDelete(entry) },
                            onClick = { onOpenDetail(entry) }
                        )
                    }
                }
            }
        }
    }
}

/** 单个子代理行：图标 + 名称/描述 + 模型标签 + 右箭头；左滑删除，点击行进入详情。 */
@Composable
private fun SubAgentRow(
    entry: SubAgentUiEntry,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    // 行背景由 SwipeToDeleteRow 提供，这里不再重复画一层。
    SwipeToDeleteRow(onDelete = onDelete, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.xs, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = FeatherIcons.Users,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    entry.model?.let { model ->
                        McpPill(
                            text = model,
                            textColor = MaterialTheme.colorScheme.tertiary,
                            backgroundColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                        )
                    }
                    // 只在禁用时挂标签：默认启用是常态，每行都挂一个反而护不住重点。
                    if (entry.disabled) {
                        McpPill(
                            text = stringResource(R.string.common_disabled),
                            textColor = MaterialTheme.colorScheme.outline,
                            backgroundColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )
                    }
                }
                Text(
                    text = entry.description.ifBlank { stringResource(R.string.mcp_no_description) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            Icon(
                imageVector = FeatherIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.semanticColors.subtleText,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** 分组内空状态：一行灰字，与行内容对齐。 */
@Composable
private fun SubAgentEmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = 12.dp)
    )
}
