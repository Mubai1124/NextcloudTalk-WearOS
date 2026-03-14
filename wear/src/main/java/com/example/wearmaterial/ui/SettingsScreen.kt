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
fun SettingsScreen(
    onLogout: () -> Unit,
    onBack: () -> Unit,
    onOpenNotificationSettings: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenAppearance: () -> Unit = {}
) {
    val listState = rememberScalingLazyListState()
    
    // 设置状态
    var notificationsEnabled by remember { mutableStateOf(true) }
    var vibrationEnabled by remember { mutableStateOf(true) }
    
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
                    text = "设置",
                    style = MaterialTheme.typography.title2
                )
            }
            
            // 通知设置（子菜单）
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Column {
                            Text(
                                text = "通知设置",
                                style = MaterialTheme.typography.body1
                            )
                            Text(
                                text = if (notificationsEnabled) "通知已开启" else "通知已关闭",
                                style = MaterialTheme.typography.caption3,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = onOpenNotificationSettings,
                    colors = ChipDefaults.primaryChipColors()
                )
            }
            
            // 外观设置
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Column {
                            Text(
                                text = "外观设置",
                                style = MaterialTheme.typography.body1
                            )
                            Text(
                                text = "配色、主题",
                                style = MaterialTheme.typography.caption3,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = onOpenAppearance,
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
            
            // 关于
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Column {
                            Text(
                                text = "关于",
                                style = MaterialTheme.typography.body1
                            )
                            Text(
                                text = "Nextcloud Talk for Wear OS",
                                style = MaterialTheme.typography.caption3,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = onOpenAbout,
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
            
            // 退出登录
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colors.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "退出登录",
                                style = MaterialTheme.typography.body1,
                                color = MaterialTheme.colors.error
                            )
                        }
                    },
                    onClick = onLogout,
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}
