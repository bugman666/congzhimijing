package com.example

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val sharedPrefs = getSharedPreferences("secret_prefs", android.content.Context.MODE_PRIVATE)

        setContent {
            MyApplicationTheme {
                val viewModel: MainViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return MainViewModel(sharedPrefs) as T
                    }
                })
                SecretAppMain(viewModel)
            }
        }
    }
}

@Composable
fun UpdateDialog(viewModel: MainViewModel) {
    val updateInfo by viewModel.updateAvailable.collectAsState()
    val context = LocalContext.current
    var isDownloading by remember { mutableStateOf(false) }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { /* Should be un-dismissable according to requirements */ },
            title = { Text("发现新版本") },
            text = { Text("有新版本 ${info.version} 可用，请更新后继续使用。\nURL: ${info.url}") },
            confirmButton = {
                Button(
                    onClick = {
                        isDownloading = true
                        startDownload(context, info.url, info.version)
                    },
                    enabled = !isDownloading
                ) {
                    Text(if (isDownloading) "正在下载..." else "立即更新")
                }
            },
            dismissButton = {
                // If it is strictly non-ignorable, we can hide or omit the dismiss button
                // But giving an exit option is good, or just wait for download.
            }
        )
    }
}

private fun startDownload(context: Context, url: String, version: String) {
    val fileName = "update-$version.apk"
    val request = DownloadManager.Request(Uri.parse(url)).apply {
        setTitle("App Update")
        setDescription("Downloading new version $version")
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
    }

    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val downloadId = manager.enqueue(request)

    val onComplete = object : BroadcastReceiver() {
        override fun onReceive(ctxt: Context?, intent: Intent?) {
            if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(context, fileName)
                    context.unregisterReceiver(this)
                }
            }
        }
    }
    context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
}

private fun installApk(context: Context, fileName: String) {
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
    if (!file.exists()) return

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun SecretAppMain(viewModel: MainViewModel) {
    val appState by viewModel.appState.collectAsState()
    
    UpdateDialog(viewModel)

    Crossfade(targetState = appState, label = "main_crossfade") { state ->
        when (state) {
            AppState.SPLASH -> SplashScreen(viewModel)
            AppState.LOGIN -> LoginScreen(viewModel)
            AppState.ADMIN_DASHBOARD -> AdminDashboardScreen(viewModel)
            AppState.PORTAL -> MainContainerScreen(viewModel)
            AppState.WEBVIEW -> {
                val url by viewModel.activeUrl.collectAsState()
                url?.let {
                    WebViewScreen(
                        url = it,
                        onReturnHome = {
                            viewModel.returnToPortal(0)
                        },
                        onSwitchSite = {
                            viewModel.returnToPortal(1)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: MainViewModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    val isDark = isSystemInDarkTheme()
    val bgColors = if (isDark) Zinc950 else Zinc50
    val textColors = if (isDark) Zinc100 else Zinc900

    fun attemptLogin() {
        viewModel.login(username.trim(), password.trim()) { success ->
            if (!success) {
                errorMsg = "Login Failed"
                password = ""
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColors), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(0.8f)) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = textColors, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text("系统登录", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textColors)
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = username,
                onValueChange = { username = it; errorMsg = "" },
                label = { Text("账号") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColors,
                    unfocusedTextColor = textColors
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMsg = "" },
                label = { Text("密码") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { attemptLogin() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColors,
                    unfocusedTextColor = textColors
                )
            )
            if (errorMsg.isNotEmpty()) {
                Text(errorMsg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { attemptLogin() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo500)
            ) {
                Text("进入")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(viewModel: MainViewModel) {
    var tabIndex by remember { mutableStateOf(0) }
    val isDark = isSystemInDarkTheme()
    val bgColors = if (isDark) Zinc950 else Zinc50
    val textColors = if (isDark) Zinc100 else Zinc900
    val cardBg = if (isDark) Zinc900 else Color.White

    Scaffold(
        containerColor = bgColors,
        topBar = {
            TopAppBar(
                title = { Text("管理员控制台") },
                actions = {
                    IconButton(onClick = { viewModel.appState.value = AppState.PORTAL }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "前往传送门")
                    }
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "退出登录")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bgColors,
                    titleContentColor = textColors,
                    actionIconContentColor = textColors
                )
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            TabRow(selectedTabIndex = tabIndex, containerColor = bgColors, contentColor = Indigo500) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }) { Text("站点管理", modifier = Modifier.padding(16.dp), color = textColors) }
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }) { Text("账号管理", modifier = Modifier.padding(16.dp), color = textColors) }
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }) { Text("公告管理", modifier = Modifier.padding(16.dp), color = textColors) }
                Tab(selected = tabIndex == 3, onClick = { tabIndex = 3 }) { Text("配置管理", modifier = Modifier.padding(16.dp), color = textColors) }
            }

            Box(modifier = Modifier.weight(1f).padding(16.dp)) {
                when (tabIndex) {
                    0 -> SitesManager(viewModel, cardBg, textColors)
                    1 -> AccountsManager(viewModel, cardBg, textColors)
                    2 -> AnnouncementsManager(viewModel, cardBg, textColors)
                    3 -> ConfigManager(viewModel, cardBg, textColors)
                }
            }
        }
    }
}

@Composable
fun AnnouncementsManager(viewModel: MainViewModel, cardBg: Color, textColors: Color) {
    val announcements by viewModel.announcements.collectAsState()
    var content by remember { mutableStateOf("") }
    
    var showEditDialog by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Int?>(null) }
    var editContent by remember { mutableStateOf("") }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("修改公告") },
            text = {
                OutlinedTextField(
                    value = editContent,
                    onValueChange = { editContent = it },
                    label = { Text("内容") },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editContent.isNotBlank() && editId != null) {
                        viewModel.updateAnnouncement(editId!!, editContent)
                        showEditDialog = false
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("取消") }
            }
        )
    }

    Column {
        Card(colors = CardDefaults.cardColors(containerColor = cardBg), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("发布公告", fontWeight = FontWeight.Bold, color = textColors)
                OutlinedTextField(
                    value = content, 
                    onValueChange = { content = it }, 
                    label = { Text("内容") }, 
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
                Button(onClick = {
                    if (content.isNotBlank()) {
                        viewModel.addAnnouncement(content)
                        content = ""
                    }
                }, modifier = Modifier.padding(top = 8.dp)) { Text("发布") }
            }
        }
        
        LazyColumn {
            items(announcements) { ann ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).background(cardBg, RoundedCornerShape(8.dp)).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ann.content, color = textColors)
                        if (!ann.createdAt.isNullOrEmpty()) {
                            Text("时间: ${ann.createdAt}", fontSize = 12.sp, color = textColors.copy(alpha=0.6f))
                        }
                    }
                    Row {
                        IconButton(onClick = { 
                            editId = ann.id
                            editContent = ann.content
                            showEditDialog = true
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "修改", tint = Indigo500)
                        }
                        IconButton(onClick = { viewModel.deleteAnnouncement(ann.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigManager(viewModel: MainViewModel, cardBg: Color, textColors: Color) {
    val config by viewModel.appConfig.collectAsState()
    var editUpdateInfo by remember { mutableStateOf(config.updateInfo) }
    var editAboutAuthor by remember { mutableStateOf(config.aboutAuthor) }

    LaunchedEffect(config) {
        editUpdateInfo = config.updateInfo
        editAboutAuthor = config.aboutAuthor
    }

    Column(modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        Card(colors = CardDefaults.cardColors(containerColor = cardBg), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("配置管理", fontWeight = FontWeight.Bold, color = textColors, modifier = Modifier.padding(bottom = 16.dp))

                OutlinedTextField(
                    value = editUpdateInfo,
                    onValueChange = { editUpdateInfo = it },
                    label = { Text("检查更新内容") },
                    modifier = Modifier.fillMaxWidth().height(150.dp).padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = editAboutAuthor,
                    onValueChange = { editAboutAuthor = it },
                    label = { Text("关于作者内容") },
                    modifier = Modifier.fillMaxWidth().height(150.dp).padding(bottom = 16.dp)
                )

                Button(
                    onClick = {
                        viewModel.updateConfig(editAboutAuthor, editUpdateInfo)
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("保存配置")
                }
            }
        }
    }
}

@Composable
fun SitesManager(viewModel: MainViewModel, cardBg: Color, textColors: Color) {
    val sites by viewModel.allSites.collectAsState()
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var passcode by remember { mutableStateOf("") }

    var showEditDialog by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Int?>(null) }
    var editName by remember { mutableStateOf("") }
    var editUrl by remember { mutableStateOf("") }
    var editPasscode by remember { mutableStateOf("") }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("修改站点") },
            text = {
                Column {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editUrl, onValueChange = { editUrl = it }, label = { Text("网址") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editPasscode, onValueChange = { editPasscode = it }, label = { Text("特定解锁密钥") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editName.isNotEmpty() && editUrl.isNotEmpty() && editPasscode.isNotEmpty() && editId != null) {
                        viewModel.updateSite(editId!!, editName, editUrl, editPasscode)
                        showEditDialog = false
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("取消") }
            }
        )
    }

    Column {
        Card(colors = CardDefaults.cardColors(containerColor = cardBg), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("添加支持站点", fontWeight = FontWeight.Bold, color = textColors)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("网址 (https://...)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = passcode, onValueChange = { passcode = it }, label = { Text("特定解锁密钥") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    if (name.isNotEmpty() && url.isNotEmpty() && passcode.isNotEmpty()) {
                        viewModel.addSite(name, url, passcode)
                        name = ""; url = ""; passcode = ""
                    }
                }, modifier = Modifier.padding(top = 8.dp)) { Text("添加站点") }
            }
        }
        
        LazyColumn {
            items(sites) { site ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).background(cardBg, RoundedCornerShape(8.dp)).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(site.name, fontWeight = FontWeight.Bold, color = textColors)
                        Text(site.url, fontSize = 12.sp, color = textColors.copy(alpha=0.6f))
                        Text("密钥: ${site.passcode}", fontSize = 12.sp, color = Indigo500)
                    }
                    Row {
                        IconButton(onClick = { 
                            editId = site.id
                            editName = site.name
                            editUrl = site.url
                            editPasscode = site.passcode
                            showEditDialog = true
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "修改", tint = Indigo500)
                        }
                        IconButton(onClick = { viewModel.deleteSite(site) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountsManager(viewModel: MainViewModel, cardBg: Color, textColors: Color) {
    val accounts by viewModel.allAccounts.collectAsState()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(false) }

    var showEditDialog by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Int?>(null) }
    var editUsername by remember { mutableStateOf("") }
    var editPassword by remember { mutableStateOf("") }
    var editIsAdmin by remember { mutableStateOf(false) }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("修改账号") },
            text = {
                Column {
                    OutlinedTextField(value = editUsername, onValueChange = { editUsername = it }, label = { Text("账号") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editPassword, onValueChange = { editPassword = it }, label = { Text("密码") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = editIsAdmin, onCheckedChange = { editIsAdmin = it })
                        Text("是否为管理员", color = textColors)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editUsername.isNotEmpty() && editPassword.isNotEmpty() && editId != null) {
                        viewModel.updateAccount(editId!!, editUsername, editPassword, editIsAdmin)
                        showEditDialog = false
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("取消") }
            }
        )
    }

    Column {
        Card(colors = CardDefaults.cardColors(containerColor = cardBg), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("添加账号", fontWeight = FontWeight.Bold, color = textColors)
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("账号") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("密码") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isAdmin, onCheckedChange = { isAdmin = it })
                    Text("是否为管理员", color = textColors)
                }
                Button(onClick = {
                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        viewModel.addAccount(username, password, isAdmin)
                        username = ""; password = ""; isAdmin = false
                    }
                }, modifier = Modifier.padding(top = 8.dp)) { Text("添加账号") }
            }
        }
        
        LazyColumn {
            items(accounts) { acc ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).background(cardBg, RoundedCornerShape(8.dp)).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(acc.username, fontWeight = FontWeight.Bold, color = textColors)
                        Text(if (acc.isAdmin) "管理员" else "普通用户", fontSize = 12.sp, color = if(acc.isAdmin) Emerald500 else Color.Gray)
                    }
                    if (acc.username != "2643819278@qq.com") {
                        Row {
                            IconButton(onClick = { 
                                editId = acc.id
                                editUsername = acc.username
                                editPassword = acc.passcode
                                editIsAdmin = acc.isAdmin
                                showEditDialog = true
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "修改", tint = Indigo500)
                            }
                            IconButton(onClick = { viewModel.deleteAccount(acc) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun MainContainerScreen(viewModel: MainViewModel) {
    val tabIndex by viewModel.currentTabIndex.collectAsState()
    val isDark = isSystemInDarkTheme()
    val bgColors = if (isDark) Zinc950 else Zinc50
    val cardBg = if (isDark) Zinc900 else Color.White
    val textColors = if (isDark) Zinc100 else Zinc900

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = cardBg) {
                NavigationBarItem(
                    selected = tabIndex == 0,
                    onClick = { viewModel.currentTabIndex.value = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "主界面") },
                    label = { Text("主界面") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Indigo500, selectedTextColor = Indigo500)
                )
                NavigationBarItem(
                    selected = tabIndex == 1,
                    onClick = { viewModel.currentTabIndex.value = 1 },
                    icon = { Icon(Icons.Filled.Lock, contentDescription = "秘境") },
                    label = { Text("秘境") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Indigo500, selectedTextColor = Indigo500)
                )
                NavigationBarItem(
                    selected = tabIndex == 2,
                    onClick = { viewModel.currentTabIndex.value = 2 },
                    icon = { Icon(Icons.Filled.Email, contentDescription = "社区") },
                    label = { Text("社区") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Indigo500, selectedTextColor = Indigo500)
                )
                NavigationBarItem(
                    selected = tabIndex == 3,
                    onClick = { viewModel.currentTabIndex.value = 3 },
                    icon = { Icon(Icons.Filled.Person, contentDescription = "个人中心") },
                    label = { Text("个人中心") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Indigo500, selectedTextColor = Indigo500)
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (tabIndex) {
                0 -> PortalTabScreen(viewModel)
                1 -> SecretRealmTabScreen(viewModel)
                2 -> CommunityTabScreen(viewModel)
                3 -> ProfileTabScreen(viewModel)
            }
        }
    }
}

@Composable
fun SecretRealmTabScreen(viewModel: MainViewModel) {
    val unlockedSites by viewModel.unlockedSites.collectAsState()
    val isDark = isSystemInDarkTheme()
    val bgColors = if (isDark) Zinc950 else Zinc50
    val textColors = if (isDark) Zinc100 else Zinc900
    val cardBg = if (isDark) Zinc900 else Color.White

    Column(modifier = Modifier.fillMaxSize().background(bgColors).padding(16.dp)) {
        Text("你的秘境", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textColors, modifier = Modifier.padding(bottom = 16.dp))
        if (unlockedSites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("尚未解锁任何站点", color = textColors.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(unlockedSites) { site ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        onClick = { viewModel.openUnlockedSite(site) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = Indigo500)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(site.name, fontWeight = FontWeight.Bold, color = textColors)
                                Text(site.url, fontSize = 12.sp, color = textColors.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CommunityTabScreen(viewModel: MainViewModel) {
    val comments by viewModel.comments.collectAsState()
    val nickname by viewModel.nickname.collectAsState()
    val isAdmin by viewModel.isAdminUser.collectAsState()
    var content by remember { mutableStateOf("") }
    val isDark = isSystemInDarkTheme()
    val bgColors = if (isDark) Zinc950 else Zinc50
    val textColors = if (isDark) Zinc100 else Zinc900
    val cardBg = if (isDark) Zinc900 else Color.White

    var selectedComment by remember { mutableStateOf<com.example.data.Comment?>(null) }
    var editCommentText by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var showActionDialog by remember { mutableStateOf(false) }

    if (showActionDialog && selectedComment != null) {
        AlertDialog(
            onDismissRequest = { showActionDialog = false; selectedComment = null },
            title = { Text("管理评论", fontWeight = FontWeight.Bold) },
            text = { Text("请选择要执行的操作", color = textColors) },
            confirmButton = {
                TextButton(onClick = {
                    showActionDialog = false
                    showEditDialog = true
                }) { Text("修改", color = Indigo500) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showActionDialog = false
                    showDeleteDialog = true
                }) { Text("删除", color = Color.Red) }
            },
            containerColor = cardBg,
            titleContentColor = textColors
        )
    }

    if (showDeleteDialog && selectedComment != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; selectedComment = null },
            title = { Text("删除评论", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除这条评论吗？", color = textColors) },
            confirmButton = {
                TextButton(onClick = {
                    selectedComment?.let { viewModel.deleteComment(it.id) }
                    showDeleteDialog = false
                    selectedComment = null
                }) { Text("删除", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; selectedComment = null }) { Text("取消") }
            },
            containerColor = cardBg,
            titleContentColor = textColors
        )
    }

    if (showEditDialog && selectedComment != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false; selectedComment = null },
            title = { Text("修改评论", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editCommentText,
                    onValueChange = { editCommentText = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedComment?.let { viewModel.updateComment(it.id, editCommentText) }
                    showEditDialog = false
                    selectedComment = null
                }) { Text("保存", color = Indigo500) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false; selectedComment = null }) { Text("取消") }
            },
            containerColor = cardBg,
            titleContentColor = textColors
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(bgColors).padding(16.dp)) {
        Text("社区交流", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textColors, modifier = Modifier.padding(bottom = 16.dp))
        
        Card(colors = CardDefaults.cardColors(containerColor = cardBg), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("发表看法...") },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
                Button(onClick = {
                    if (content.isNotBlank()) {
                        viewModel.addComment(content)
                        content = ""
                    }
                }, modifier = Modifier.align(Alignment.End).padding(top = 8.dp)) {
                    Text("发布")
                }
            }
        }
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(comments) { comment ->
                val displayAuthor = comment.account?.nickname ?: comment.author ?: "访客"
                val canManage = isAdmin || (nickname.isNotBlank() && displayAuthor == nickname)
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (canManage) {
                                Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        selectedComment = comment
                                        editCommentText = comment.content
                                        if (isAdmin) {
                                            showActionDialog = true
                                        } else {
                                            showDeleteDialog = true
                                        }
                                    }
                                )
                            } else Modifier
                        )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(displayAuthor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Indigo500)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(comment.content, color = textColors)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileTabScreen(viewModel: MainViewModel) {
    val config by viewModel.appConfig.collectAsState()
    val isAdmin by viewModel.isAdminUser.collectAsState()
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showAuthorDialog by remember { mutableStateOf(false) }
    
    val nickname by viewModel.nickname.collectAsState()
    var showNicknameDialog by remember { mutableStateOf(false) }
    var tempNickname by remember { mutableStateOf("") }
    var nicknameError by remember { mutableStateOf<String?>(null) }
    
    val isDark = isSystemInDarkTheme()
    val bgColors = if (isDark) Zinc950 else Zinc50
    val textColors = if (isDark) Zinc100 else Zinc900
    val cardBg = if (isDark) Zinc900 else Color.White
    val secondaryText = if (isDark) Zinc400 else Zinc500
    val dividerColor = if (isDark) Zinc800 else Zinc200

    if (showNicknameDialog) {
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false; nicknameError = null },
            title = { Text("修改昵称", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = tempNickname,
                        onValueChange = { tempNickname = it },
                        label = { Text("新昵称") },
                        singleLine = true,
                        isError = nicknameError != null
                    )
                    if (nicknameError != null) {
                        Text(nicknameError!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Text("提示：昵称每3天只能修改一次。", fontSize = 12.sp, color = secondaryText, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (tempNickname.isBlank()) {
                        nicknameError = "昵称不能为空"
                    } else {
                        viewModel.updateNickname(tempNickname) { success, errorMsg ->
                            if (success) {
                                showNicknameDialog = false
                                nicknameError = null
                            } else {
                                nicknameError = errorMsg ?: "修改太频繁，请3天后再试"
                            }
                        }
                    }
                }) { Text("确定", color = Indigo500) }
            },
            dismissButton = {
                TextButton(onClick = { showNicknameDialog = false; nicknameError = null }) { Text("取消", color = secondaryText) }
            },
            containerColor = cardBg,
            titleContentColor = textColors
        )
    }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("检查更新", fontWeight = FontWeight.Bold) },
            text = { Text(config.updateInfo, color = textColors) },
            confirmButton = { TextButton(onClick = { showUpdateDialog = false }) { Text("确定", color = Indigo500) } },
            containerColor = cardBg,
            titleContentColor = textColors
        )
    }
    if (showAuthorDialog) {
        AlertDialog(
            onDismissRequest = { showAuthorDialog = false },
            title = { Text("关于作者", fontWeight = FontWeight.Bold) },
            text = { Text(config.aboutAuthor, color = textColors) },
            confirmButton = { TextButton(onClick = { showAuthorDialog = false }) { Text("确定", color = Indigo500) } },
            containerColor = cardBg,
            titleContentColor = textColors
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColors)
            .padding(top = 32.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Section
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Indigo500.copy(alpha = 0.1f), shape = RoundedCornerShape(50.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = Indigo500
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (nickname.isNotBlank()) nickname else if (isAdmin) "超级管理员" else "普通访客",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = textColors
            )
            IconButton(onClick = { tempNickname = nickname; showNicknameDialog = true }, modifier = Modifier.size(32.dp).padding(start = 8.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit Nickname", tint = Indigo500, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            text = if (isAdmin) "您拥有所有站点的最高权限" else "欢迎来到秘境，尽情探索吧",
            fontSize = 14.sp,
            color = secondaryText,
            modifier = Modifier.padding(top = 4.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        // Settings Section
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                SettingsRow(
                    icon = Icons.Filled.Info,
                    label = "关于作者",
                    onClick = { showAuthorDialog = true },
                    textColors = textColors,
                    iconTint = Indigo500
                )
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(dividerColor).padding(horizontal = 16.dp))
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    label = "检查更新",
                    onClick = { showUpdateDialog = true },
                    textColors = textColors,
                    iconTint = Indigo500
                )
                if (isAdmin) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(dividerColor).padding(horizontal = 16.dp))
                    SettingsRow(
                        icon = Icons.Filled.Lock,
                        label = "进入管理员控制台",
                        onClick = { viewModel.goToAdmin() },
                        textColors = textColors,
                        iconTint = Indigo500
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = { viewModel.logout() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.Red.copy(alpha=0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red.copy(alpha=0.8f))
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("退出登录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    textColors: Color,
    iconTint: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, color = textColors, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = textColors.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
    }
}

@Composable
fun PortalTabScreen(viewModel: MainViewModel) {
    var passcode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    val isDark = isSystemInDarkTheme()
    val isAdmin by viewModel.isAdminUser.collectAsState()
    
    val bgColors = if (isDark) Zinc950 else Zinc50
    val textColors = if (isDark) Zinc100 else Zinc900
    val cardBg = if (isDark) Zinc900 else Color.White
    val borderColor = if (isDark) Zinc800 else Zinc200
    val dividerColor = if (isDark) Zinc800 else Zinc300
    val fieldBg = if (isDark) Zinc900.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f)

    fun attemptUnlock() {
        if (passcode.trim().isEmpty()) return
        viewModel.attemptSiteUnlock(passcode.trim()) { success, _ ->
            if (!success) {
                errorMessage = "密码错误"
                passcode = ""
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(1000L)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = bgColors
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Emerald500).alpha(pulseAlpha))
                    Text(
                        text = "同步激活",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = textColors,
                        modifier = Modifier.alpha(if(isDark) 0.4f else 0.5f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.size(16.dp).border(2.dp, if(isDark) Zinc700 else Zinc300, CircleShape))
                    Text(
                        text = currentTime,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColors,
                        modifier = Modifier.alpha(0.6f)
                    )
                }
            }

            // Center Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Logo/Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(cardBg)
                        .border(1.dp, borderColor, RoundedCornerShape(32.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Indigo500, Sky400)))
                            .alpha(0.9f)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "聪之秘境",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-0.5).sp,
                    color = textColors
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "请输入密钥以启动站点。",
                    fontSize = 14.sp,
                    color = textColors,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(0.5f).padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(40.dp))

                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(fieldBg).border(1.dp, borderColor, RoundedCornerShape(16.dp))
                ) {
                    TextField(
                        value = passcode,
                        onValueChange = { 
                            passcode = it
                            if (errorMessage.isNotEmpty()) errorMessage = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Indigo500,
                            focusedTextColor = textColors,
                            unfocusedTextColor = textColors
                        ),
                        placeholder = { 
                            Text(
                                text = "通行密钥", 
                                color = textColors.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontSize = 20.sp,
                                letterSpacing = 8.sp // Adjusted
                            ) 
                        },
                        textStyle = LocalTextStyle.current.copy(
                            textAlign = TextAlign.Center,
                            fontSize = 20.sp,
                            letterSpacing = 8.sp // Adjusted
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { attemptUnlock() }
                        ),
                        singleLine = true
                    )
                }

                AnimatedVisibility(
                    visible = errorMessage.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { attemptUnlock() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Zinc100 else Zinc900,
                        contentColor = if (isDark) Zinc900 else Color.White
                    )
                ) {
                    Text("验证", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Stats row
                Row(
                    modifier = Modifier.alpha(0.4f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("登录状态", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = textColors)
                        Text("持久", fontSize = 12.sp, color = textColors)
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(dividerColor))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("缓存", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = textColors)
                        Text("84.2 MB", fontSize = 12.sp, color = textColors)
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(dividerColor))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("内核", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = textColors)
                        Text("WebKit", fontSize = 12.sp, color = textColors)
                    }
                }
                
                val announcements by viewModel.announcements.collectAsState()
                if (announcements.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("系统公告", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Indigo500, modifier = Modifier.padding(bottom = 8.dp))
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(announcements) { ann ->
                                    var showAnnDialog by remember { mutableStateOf(false) }
                                    if (showAnnDialog) {
                                        AlertDialog(
                                            onDismissRequest = { showAnnDialog = false },
                                            title = { Text("系统公告", fontWeight = FontWeight.Bold) },
                                            text = {
                                                Column(modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                                                    Text(ann.content, color = textColors)
                                                    if (!ann.createdAt.isNullOrEmpty()) {
                                                        Spacer(modifier = Modifier.height(16.dp))
                                                        Text(ann.createdAt!!, fontSize = 12.sp, color = textColors.copy(alpha=0.5f))
                                                    }
                                                }
                                            },
                                            confirmButton = { TextButton(onClick = { showAnnDialog = false }) { Text("关闭", color = Indigo500) } },
                                            containerColor = cardBg,
                                            titleContentColor = textColors
                                        )
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showAnnDialog = true }
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = ann.content,
                                            fontSize = 13.sp,
                                            color = textColors,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        if (!ann.createdAt.isNullOrEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(ann.createdAt!!, fontSize = 10.sp, color = textColors.copy(alpha=0.5f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// Memory Management Strategy: WebViews are stored uniquely by URL.
// When a particular URL is active, it is visible and resumed. 
// When not active, it is hidden and onPause() is called to sleep background timers and JS.
@Composable
fun WebViewScreen(url: String, onReturnHome: () -> Unit, onSwitchSite: () -> Unit) {
    val context = LocalContext.current
    var isMenuExpanded by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler {
        val webView = webViewCache[url]
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            onReturnHome()
        }
    }

    // Dictionary to manage multiple webviews
    // In Compose, using a Box with all created WebViews. Only the matching URL is visible and resumed.
    Box(modifier = Modifier.fillMaxSize()) {
        WebViewManager(activeUrl = url)

        // Global Navigation Float Button
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomEnd) {
            Column(horizontalAlignment = Alignment.End) {
                AnimatedVisibility(visible = isMenuExpanded) {
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 16.dp)) {
                        FloatingActionButton(
                            onClick = { onReturnHome(); isMenuExpanded = false },
                            containerColor = Zinc100,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(Icons.Filled.Home, contentDescription = "返回主界面", tint = Zinc900)
                        }
                        FloatingActionButton(
                            onClick = { onSwitchSite(); isMenuExpanded = false },
                            containerColor = Zinc100,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "切换站点", tint = Zinc900)
                        }
                        FloatingActionButton(
                            onClick = {
                                webViewCache[url]?.reload()
                                isMenuExpanded = false
                            },
                            containerColor = Zinc100,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新页面", tint = Zinc900)
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { isMenuExpanded = !isMenuExpanded },
                    containerColor = Indigo500
                ) {
                    Icon(if (isMenuExpanded) Icons.Filled.Close else Icons.Filled.Menu, contentDescription = "菜单", tint = Color.White)
                }
            }
        }
    }
}

val webViewCache = mutableMapOf<String, WebView>()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewManager(activeUrl: String) {
    val context = LocalContext.current

    // Iterate over cached WebViews to pause inactive ones
    DisposableEffect(activeUrl) {
        webViewCache.forEach { (url, webView) ->
            if (url == activeUrl) {
                webView.onResume()
                webView.resumeTimers()
            } else {
                webView.onPause() // Auto-sleep for previously opened sites
            }
        }
        onDispose {
            // we could destroy here, but we are caching
        }
    }

    // Wrap AndroidView so we create it once per URL and show only the active one
    // But since compose doesn't let us easily retain AndroidView across recompositions naturally without rebuilding,
    // we use a FrameLayout to hold our cached webviews, and bring the active one to front.
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            android.widget.FrameLayout(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { frameLayout ->
            // Ensure Webview for URL exists
            var webView = webViewCache[activeUrl]
            if (webView == null) {
                webView = WebView(context).apply {
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                        
                        loadsImagesAutomatically = true
                        blockNetworkImage = true
                    }
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.settings?.blockNetworkImage = false
                        }

                        @SuppressLint("WebViewClientOnReceivedSslError")
                        override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                            handler?.proceed()
                        }
                        override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                            return false
                        }
                    }
                    webChromeClient = WebChromeClient()

                    loadUrl(activeUrl)
                }
                webViewCache[activeUrl] = webView
            }

            val parent = webView.parent as? android.view.ViewGroup
            if (parent != frameLayout) {
                parent?.removeView(webView)
                frameLayout.addView(webView)
            }

            // Hide all, show active
            for (i in 0 until frameLayout.childCount) {
                val child = frameLayout.getChildAt(i)
                child.visibility = android.view.View.GONE
            }
            webView.visibility = android.view.View.VISIBLE
            webView.bringToFront()
        }
    )
}

suspend fun resolveRedirect(url: String): Pair<String, String?> {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
            val request = Request.Builder().url(url).head().build()
            val response = client.newCall(request).execute()
            val location = response.header("Location")
            val etag = response.header("ETag")
            response.close()
            
            if (location != null) {
                // If the server redirects, return the final URL
                Pair(response.request.url.resolve(location)?.toString() ?: location, null)
            } else {
                Pair(url, etag) // no redirect, keep original url and possible ETag
            }
        } catch (e: Exception) {
            Pair(url, null)
        }
    }
}

@Composable
fun SplashScreen(viewModel: MainViewModel) {
    var finalImageUrl by remember { mutableStateOf<String?>(null) }
    var imageEtag by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        val (url, etag) = resolveRedirect("http://47.238.233.72:3005/api/v1/portal/splash")
        finalImageUrl = url
        imageEtag = etag
        delay(2000) // Show for 2 seconds
        viewModel.finishSplash()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        finalImageUrl?.let { url ->
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .apply {
                        if (imageEtag != null) {
                            memoryCacheKey("$url-$imageEtag")
                            diskCacheKey("$url-$imageEtag")
                        }
                    }
                    .crossfade(true)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .build(),
                contentDescription = "Splash Screen",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

