package com.example.wearmaterial.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.example.wearmaterial.service.MessagePollingService

/**
 * 通知设置子菜单
 */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    vibrationEnabled: Boolean,
    onVibrationEnabledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()
    
    // 检查通知权限状态
    val hasNotificationPermission = remember {
        mutableStateOf(checkNotificationPermission(context))
    }
    
    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        timeText = { TimeText() }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 标题
            item {
                Text(
                    text = "通知设置",
                    style = MaterialTheme.typography.title2
                )
            }
            
            // 权限状态
            item {
                PermissionStatusCard(hasPermission = hasNotificationPermission.value)
            }
            
            // 设置指引
            if (!hasNotificationPermission.value) {
                item {
                    NotificationPermissionGuide()
                }
            }
            
            // 通知开关
            item {
                ToggleChip(
                    modifier = Modifier.fillMaxWidth(),
                    checked = notificationsEnabled && hasNotificationPermission.value,
                    onCheckedChange = { enabled ->
                        if (enabled && !hasNotificationPermission.value) {
                            // 引导用户去设置
                            openNotificationSettings(context)
                        } else {
                            onNotificationsEnabledChange(enabled)
                            if (enabled) {
                                MessagePollingService.start(context)
                            } else {
                                MessagePollingService.stop(context)
                            }
                        }
                    },
                    label = { Text("消息通知") },
                    appIcon = {
                        Icon(
                            imageVector = if (hasNotificationPermission.value) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                            contentDescription = null
                        )
                    },
                    toggleControl = {
                        Icon(
                            imageVector = if (notificationsEnabled && hasNotificationPermission.value) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null
                        )
                    },
                    enabled = hasNotificationPermission.value
                )
            }
            
            // 振动开关
            item {
                ToggleChip(
                    modifier = Modifier.fillMaxWidth(),
                    checked = vibrationEnabled,
                    onCheckedChange = onVibrationEnabledChange,
                    label = { Text("振动提醒") },
                    appIcon = {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = null
                        )
                    },
                    toggleControl = {
                        Icon(
                            imageVector = if (vibrationEnabled) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null
                        )
                    }
                )
            }
            
            // 后台服务说明
            item {
                Text(
                    text = "开启通知后，应用将在后台持续监听新消息",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
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

/**
 * 权限状态卡片
 */
@Composable
private fun PermissionStatusCard(hasPermission: Boolean) {
    val backgroundColor = if (hasPermission) {
        MaterialTheme.colors.primary.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colors.error.copy(alpha = 0.2f)
    }
    
    val contentColor = if (hasPermission) {
        MaterialTheme.colors.primary
    } else {
        MaterialTheme.colors.error
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (hasPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = if (hasPermission) "通知权限已开启" else "通知权限未开启",
            style = MaterialTheme.typography.caption1,
            color = contentColor
        )
    }
}

/**
 * 通知权限设置指引
 */
@Composable
private fun NotificationPermissionGuide() {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "如何开启通知权限：",
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.onSurface
        )
        
        Text(
            text = "1. 点击下方按钮打开应用设置",
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
        )
        
        Text(
            text = "2. 找到「通知」选项",
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
        )
        
        Text(
            text = "3. 开启「允许通知」",
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
        )
        
        Chip(
            modifier = Modifier.fillMaxWidth(),
            label = { Text("打开应用设置") },
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null
                )
            },
            onClick = { openNotificationSettings(context) },
            colors = ChipDefaults.primaryChipColors()
        )
    }
}

/**
 * 检查通知权限
 */
private fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) 
            as android.app.NotificationManager
        notificationManager.areNotificationsEnabled()
    }
}

/**
 * 打开通知设置
 */
private fun openNotificationSettings(context: Context) {
    try {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // 无法打开设置页面
    }
}
