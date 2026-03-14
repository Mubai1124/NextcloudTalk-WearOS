package com.example.wearmaterial.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*

@Composable
fun QuickReplyScreen(
    conversationId: String,
    onReplySelected: (String) -> Unit,
    onCustomInput: () -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    
    // 预设消息
    val presetMessages = listOf(
        "好的" to "同意/确认",
        "收到" to "已收到信息",
        "稍等" to "请稍等片刻",
        "马上来" to "即将到达",
        "谢谢" to "表示感谢"
    )
    
    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        timeText = { TimeText() }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 标题
            item {
                Text(
                    text = "快速回复",
                    style = MaterialTheme.typography.title2
                )
            }
            
            // 预设消息列表
            items(presetMessages.size) { index ->
                val (message, description) = presetMessages[index]
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Column {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.body1
                            )
                            Text(
                                text = description,
                                style = MaterialTheme.typography.caption3,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = when (index) {
                                0 -> Icons.Default.Check
                                1 -> Icons.Default.DoneAll
                                2 -> Icons.Default.Schedule
                                3 -> Icons.Default.DirectionsRun
                                else -> Icons.Default.Favorite
                            },
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = { onReplySelected(message) },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
            
            // 自定义输入按钮
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "自定义输入",
                                style = MaterialTheme.typography.body1
                            )
                        }
                    },
                    onClick = onCustomInput,
                    colors = ChipDefaults.primaryChipColors()
                )
            }
        }
    }
}