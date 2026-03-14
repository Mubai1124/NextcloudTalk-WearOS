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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.*
import com.example.wearmaterial.viewmodel.CreateConversationState
import com.example.wearmaterial.viewmodel.TalkViewModel

/**
 * 创建会话界面
 */
@Composable
fun CreateConversationScreen(
    onBack: () -> Unit,
    onConversationCreated: (String, String) -> Unit
) {
    val viewModel: TalkViewModel = viewModel()
    val listState = rememberScalingLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    
    var conversationName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(3) } // 默认公开群组
    
    val createState by viewModel.createConversationState.collectAsState()
    
    // 监听创建成功
    LaunchedEffect(createState) {
        if (createState is CreateConversationState.Success) {
            val room = (createState as CreateConversationState.Success).room
            viewModel.resetCreateConversationState()
            onConversationCreated(room.token, room.displayName ?: room.name)
        }
    }
    
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
                    text = "新建会话",
                    style = MaterialTheme.typography.title2
                )
            }
            
            // 会话名称输入
            item {
                Text(
                    text = "会话名称",
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
            
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    BasicTextField(
                        value = conversationName,
                        onValueChange = { conversationName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.body1.copy(
                            color = MaterialTheme.colors.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colors.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (conversationName.isNotEmpty()) {
                                    viewModel.createConversation(conversationName, selectedType)
                                }
                            }
                        ),
                        decorationBox = { innerTextField ->
                            if (conversationName.isEmpty()) {
                                Text(
                                    text = "输入会话名称...",
                                    style = MaterialTheme.typography.body1,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }
            
            // 会话类型选择
            item {
                Text(
                    text = "会话类型",
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
            
            // 群组
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { 
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("群组")
                        }
                    },
                    onClick = { selectedType = 2 },
                    colors = if (selectedType == 2) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors()
                )
            }
            
            // 公开群组
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { 
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("公开群组")
                        }
                    },
                    onClick = { selectedType = 3 },
                    colors = if (selectedType == 3) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors()
                )
            }
            
            // 状态显示
            when (createState) {
                is CreateConversationState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                indicatorColor = MaterialTheme.colors.primary
                            )
                        }
                    }
                }
                is CreateConversationState.Error -> {
                    item {
                        Text(
                            text = (createState as CreateConversationState.Error).message,
                            style = MaterialTheme.typography.caption2,
                            color = androidx.compose.ui.graphics.Color.Red
                        )
                    }
                }
                else -> {}
            }
            
            // 创建按钮
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("创建") },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        if (conversationName.isNotEmpty()) {
                            viewModel.createConversation(conversationName, selectedType)
                        }
                    },
                    colors = ChipDefaults.primaryChipColors(),
                    enabled = conversationName.isNotEmpty() && createState !is CreateConversationState.Loading
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
