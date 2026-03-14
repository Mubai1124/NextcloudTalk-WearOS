package com.example.wearmaterial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.example.wearmaterial.ui.theme.AppColors

/**
 * 外观设置界面
 */
@Composable
fun AppearanceSettingsScreen(
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val colorSchemes = AppColors.allSchemes
    
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
                    text = "外观设置",
                    style = MaterialTheme.typography.title2
                )
            }
            
            // 配色选择标题
            item {
                Text(
                    text = "选择配色",
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.primary
                )
            }
            
            // 配色选项
            colorSchemes.forEach { scheme ->
                item {
                    val isSelected = currentTheme == scheme.name
                    
                    Chip(
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // 颜色预览圆点
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(scheme.primary)
                                    )
                                    
                                    Text(
                                        text = scheme.name,
                                        style = MaterialTheme.typography.body2
                                    )
                                }
                                
                                // 选中标记
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colors.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        onClick = { onThemeChange(scheme.name) },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = if (isSelected) {
                                MaterialTheme.colors.primary.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colors.surface
                            }
                        )
                    )
                }
            }
            
            // 说明
            item {
                Text(
                    text = "配色将在重启应用后生效",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            // 返回按钮
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("返回") },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null
                        )
                    },
                    onClick = onBack,
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}
