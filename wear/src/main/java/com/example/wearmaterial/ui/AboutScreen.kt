package com.example.wearmaterial.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.example.wearmaterial.R

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val listState = rememberScalingLazyListState()
    
    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        timeText = { TimeText() }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 应用图标和名称
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher),
                        contentDescription = "App Icon",
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Nextcloud Talk",
                        style = MaterialTheme.typography.title2
                    )
                    Text(
                        text = "for Wear OS",
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.primary
                    )
                    Text(
                        text = "v1.0.0",
                        style = MaterialTheme.typography.caption3,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            
            // 分隔线
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // 作者
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "作者",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.primary
                    )
                    Text(
                        text = "Mubai1124",
                        style = MaterialTheme.typography.body2
                    )
                }
            }
            
            // 项目地址
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "项目地址",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.primary
                    )
                    Text(
                        text = "github.com/Mubai1124/",
                        style = MaterialTheme.typography.caption1
                    )
                    Text(
                        text = "NextcloudTalk-WearOS",
                        style = MaterialTheme.typography.caption1
                    )
                }
            }
            
            // 开源协议
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "开源协议",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.primary
                    )
                    Text(
                        text = "GPL-3.0-or-later",
                        style = MaterialTheme.typography.body2
                    )
                }
            }
            
            // 致谢
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "致谢",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.primary
                    )
                    Text(
                        text = "基于 Nextcloud Talk Android",
                        style = MaterialTheme.typography.caption1
                    )
                    Text(
                        text = "(GPL-3.0-or-later)",
                        style = MaterialTheme.typography.caption3,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            
            // 第三方库
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "第三方库",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.primary
                    )
                    Text(
                        text = "• OkHttp (Apache 2.0)",
                        style = MaterialTheme.typography.caption3
                    )
                    Text(
                        text = "• Retrofit (Apache 2.0)",
                        style = MaterialTheme.typography.caption3
                    )
                    Text(
                        text = "• Gson (Apache 2.0)",
                        style = MaterialTheme.typography.caption3
                    )
                    Text(
                        text = "• Jetpack Compose (Apache 2.0)",
                        style = MaterialTheme.typography.caption3
                    )
                    Text(
                        text = "• Room (Apache 2.0)",
                        style = MaterialTheme.typography.caption3
                    )
                }
            }
            
            // 返回按钮
            item {
                Spacer(modifier = Modifier.height(8.dp))
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
