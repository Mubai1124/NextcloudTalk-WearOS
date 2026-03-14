package com.example.wearmaterial.navigation

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.compose.material.*
import com.example.wearmaterial.ui.*
import com.example.wearmaterial.viewmodel.TalkViewModel
import com.example.wearmaterial.viewmodel.UiState

sealed class Screen(val route: String) {
    object Loading : Screen("loading")
    object Login : Screen("login")
    object Conversations : Screen("conversations")
    object Chat : Screen("chat/{conversationId}/{conversationName}") {
        fun createRoute(conversationId: String, conversationName: String): String {
            return "chat/$conversationId/${java.net.URLEncoder.encode(conversationName, "UTF-8")}"
        }
    }
    object QuickReply : Screen("quick_reply/{conversationId}") {
        fun createRoute(conversationId: String): String {
            return "quick_reply/$conversationId"
        }
    }
    object TextInput : Screen("text_input/{conversationId}") {
        fun createRoute(conversationId: String): String {
            return "text_input/$conversationId"
        }
    }
    object Settings : Screen("settings")
    object Input : Screen("input/{field}") {
        fun createRoute(field: String): String {
            return "input/$field"
        }
    }
    // 新功能：创建会话
    object CreateConversation : Screen("create_conversation")
    // 新功能：Bot 管理
    object BotManagement : Screen("bot_management/{conversationId}") {
        fun createRoute(conversationId: String): String {
            return "bot_management/$conversationId"
        }
    }
    // 新功能：通知设置
    object NotificationSettings : Screen("notification_settings")
    // 新功能：语音消息录制
    object VoiceRecord : Screen("voice_record/{conversationId}") {
        fun createRoute(conversationId: String): String {
            return "voice_record/$conversationId"
        }
    }
    // 关于界面
    object About : Screen("about")
    // 外观设置
    object Appearance : Screen("appearance")
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberSwipeDismissableNavController(),
    currentTheme: String = "Nextcloud",
    onThemeChange: (String) -> Unit = {}
) {
    val viewModel: TalkViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState(initial = false)
    val isInitialized by viewModel.isInitialized.collectAsState()
    
    // 确定起始目的地
    val startDestination = when {
        isLoggedIn && isInitialized -> Screen.Conversations.route
        isLoggedIn -> Screen.Loading.route  // 正在初始化，显示加载页面
        else -> Screen.Login.route
    }
    
    SwipeDismissableNavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // 加载页面 - 用于初始化等待
        composable(Screen.Loading.route) {
            LoadingScreen()
        }
        
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = viewModel,
                onNavigateToInput = { field ->
                    navController.navigate(Screen.Input.createRoute(field))
                },
                onLoginSuccess = {
                    navController.navigate(Screen.Conversations.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(
            route = Screen.Input.route,
            arguments = listOf(
                navArgument("field") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val field = backStackEntry.arguments?.getString("field") ?: ""
            
            InputScreen(
                viewModel = viewModel,
                field = field,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Conversations.route) {
            ConversationsScreen(
                onConversationClick = { token, name ->
                    navController.navigate(Screen.Chat.createRoute(token, name))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onCreateConversation = {
                    navController.navigate(Screen.CreateConversation.route)
                },
                onManageBots = { token ->
                    navController.navigate(Screen.BotManagement.createRoute(token))
                }
            )
        }
        
        // 新功能：创建会话
        composable(Screen.CreateConversation.route) {
            CreateConversationScreen(
                onBack = {
                    navController.popBackStack()
                },
                onConversationCreated = { token, name ->
                    navController.navigate(Screen.Chat.createRoute(token, name)) {
                        popUpTo(Screen.Conversations.route)
                    }
                }
            )
        }
        
        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("conversationName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            val conversationName = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("conversationName") ?: "聊天",
                "UTF-8"
            )
            
            ChatScreen(
                conversationId = conversationId,
                conversationName = conversationName,
                onTextInput = {
                    navController.navigate(Screen.TextInput.createRoute(conversationId))
                },
                onQuickReply = {
                    navController.navigate(Screen.QuickReply.createRoute(conversationId))
                },
                onBack = {
                    navController.popBackStack()
                },
                onManageBots = {
                    navController.navigate(Screen.BotManagement.createRoute(conversationId))
                },
                onVoiceRecord = {
                    navController.navigate(Screen.VoiceRecord.createRoute(conversationId))
                }
            )
        }
        
        composable(
            route = Screen.QuickReply.route,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            
            QuickReplyScreen(
                conversationId = conversationId,
                onReplySelected = { message ->
                    viewModel.sendMessage(conversationId, message)
                    navController.popBackStack()
                },
                onCustomInput = {
                    navController.navigate(Screen.TextInput.createRoute(conversationId))
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = Screen.TextInput.route,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            
            TextInputScreen(
                title = "输入消息",
                onSend = { message ->
                    viewModel.sendMessage(conversationId, message)
                    navController.popBackStack()
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onLogout = {
                    viewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Conversations.route) { inclusive = true }
                    }
                },
                onBack = {
                    navController.popBackStack()
                },
                onOpenNotificationSettings = {
                    navController.navigate(Screen.NotificationSettings.route)
                },
                onOpenAbout = {
                    navController.navigate(Screen.About.route)
                },
                onOpenAppearance = {
                    navController.navigate(Screen.Appearance.route)
                }
            )
        }
        
        // 关于界面
        composable(Screen.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        
        // 外观设置
        composable(Screen.Appearance.route) {
            AppearanceSettingsScreen(
                currentTheme = currentTheme,
                onThemeChange = onThemeChange,
                onBack = { navController.popBackStack() }
            )
        }
        
        // 新功能：通知设置
        composable(Screen.NotificationSettings.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            var notificationsEnabled by remember { mutableStateOf(true) }
            var vibrationEnabled by remember { mutableStateOf(true) }
            
            NotificationSettingsScreen(
                onBack = { navController.popBackStack() },
                notificationsEnabled = notificationsEnabled,
                onNotificationsEnabledChange = { notificationsEnabled = it },
                vibrationEnabled = vibrationEnabled,
                onVibrationEnabledChange = { vibrationEnabled = it }
            )
        }
        
        // 新功能：语音消息录制
        composable(
            route = Screen.VoiceRecord.route,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            
            VoiceRecordScreen(
                conversationId = conversationId,
                onBack = { navController.popBackStack() },
                onSend = { file ->
                    // 发送语音消息
                    navController.popBackStack()
                }
            )
        }
        
        // 新功能：Bot 管理
        composable(
            route = Screen.BotManagement.route,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            
            BotManagementScreen(
                conversationId = conversationId,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

// 加载屏幕
@Composable
fun LoadingScreen() {
    Scaffold(
        timeText = { TimeText() }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    indicatorColor = MaterialTheme.colors.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "正在加载...",
                    style = MaterialTheme.typography.caption1
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    viewModel: TalkViewModel,
    onNavigateToInput: (String) -> Unit,
    onLoginSuccess: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val uiState by viewModel.uiState.collectAsState()
    
    // 从 ViewModel 获取输入数据
    val serverUrl by viewModel.inputServerUrl.collectAsState()
    val username by viewModel.inputUsername.collectAsState()
    val password by viewModel.inputPassword.collectAsState()
    
    // 监听登录成功
    LaunchedEffect(uiState) {
        if (uiState is UiState.Success) {
            onLoginSuccess()
        }
    }
    
    // 登录失败时重置状态
    LaunchedEffect(uiState) {
        if (uiState is UiState.Error) {
            kotlinx.coroutines.delay(3000)
            viewModel.resetUiState()
        }
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "Nextcloud Talk",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colors.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Nextcloud Talk",
                        style = MaterialTheme.typography.title2
                    )
                }
            }
            
            // 错误提示
            if (uiState is UiState.Error) {
                item {
                    Text(
                        text = (uiState as UiState.Error).message,
                        style = MaterialTheme.typography.caption2,
                        color = androidx.compose.ui.graphics.Color.Red,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
            
            // 加载中
            if (uiState is UiState.Loading) {
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
            
            // 服务器地址输入
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { 
                        Text(
                            text = if (serverUrl.isEmpty()) "点击输入服务器地址" else serverUrl,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = { onNavigateToInput("serverUrl") },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
            
            // 用户名输入
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { 
                        Text(
                            text = if (username.isEmpty()) "点击输入用户名" else username,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = { onNavigateToInput("username") },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
            
            // 密码输入
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { 
                        Text(
                            text = if (password.isEmpty()) "点击输入密码" else "••••••",
                            maxLines = 1
                        ) 
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = { onNavigateToInput("password") },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
            
            // 登录按钮
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("登录") },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Login,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        if (serverUrl.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()) {
                            viewModel.login(serverUrl, username, password)
                        }
                    },
                    colors = ChipDefaults.primaryChipColors(),
                    enabled = serverUrl.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty() &&
                              uiState !is UiState.Loading
                )
            }
        }
    }
}

/**
 * 输入界面
 */
@Composable
fun InputScreen(
    viewModel: TalkViewModel,
    field: String,
    onBack: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    
    var inputValue by remember { mutableStateOf("") }
    
    val fieldLabel = when (field) {
        "serverUrl" -> "服务器地址"
        "username" -> "用户名"
        "password" -> "密码"
        else -> "输入"
    }
    
    val isPassword = field == "password"
    
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
                    text = fieldLabel,
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
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.body1.copy(
                            color = MaterialTheme.colors.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colors.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                when (field) {
                                    "serverUrl" -> viewModel.setInputServerUrl(inputValue)
                                    "username" -> viewModel.setInputUsername(inputValue)
                                    "password" -> viewModel.setInputPassword(inputValue)
                                }
                                onBack()
                            }
                        ),
                        decorationBox = { innerTextField ->
                            if (inputValue.isEmpty()) {
                                Text(
                                    text = if (isPassword) "输入密码..." else "输入$fieldLabel...",
                                    style = MaterialTheme.typography.body1,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }
            
            // 确认按钮
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("确认") },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        when (field) {
                            "serverUrl" -> viewModel.setInputServerUrl(inputValue)
                            "username" -> viewModel.setInputUsername(inputValue)
                            "password" -> viewModel.setInputPassword(inputValue)
                        }
                        onBack()
                    },
                    colors = ChipDefaults.primaryChipColors()
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
