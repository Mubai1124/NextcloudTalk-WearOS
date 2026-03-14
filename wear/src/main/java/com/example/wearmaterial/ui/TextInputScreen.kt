package com.example.wearmaterial.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*

@Composable
fun TextInputScreen(
    title: String = "输入消息",
    onSend: (String) -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // 自动聚焦并弹出键盘
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    
    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        timeText = { TimeText() }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 标题
            item {
                Text(
                    text = title,
                    style = MaterialTheme.typography.title2
                )
            }
            
            // 输入框
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.body1.copy(
                            color = MaterialTheme.colors.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colors.primary),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (text.isNotBlank()) {
                                    onSend(text)
                                    text = ""
                                }
                            }
                        ),
                        decorationBox = { innerTextField ->
                            if (text.isEmpty()) {
                                Text(
                                    text = "点击此处输入...",
                                    style = MaterialTheme.typography.body1,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }
            
            // 字数统计
            item {
                Text(
                    text = "${text.length} 字",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                )
            }
            
            // 发送按钮
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("发送") },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        if (text.isNotBlank()) {
                            onSend(text)
                        }
                    },
                    colors = ChipDefaults.primaryChipColors(),
                    enabled = text.isNotBlank()
                )
            }
            
            // 取消按钮
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("取消") },
                    onClick = onBack,
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}
