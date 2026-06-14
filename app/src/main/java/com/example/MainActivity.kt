package com.example

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
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
fun SecretAppMain(viewModel: MainViewModel) {
    val appState by viewModel.appState.collectAsState()
    
    Crossfade(targetState = appState, label = "main_crossfade") { state ->
        when (state) {
            AppState.SPLASH -> SplashScreen(viewModel)
            AppState.LOGIN -> LoginScreen(viewModel)
            AppState.ADMIN_DASHBOARD -> AdminDashboardScreen(viewModel)
            AppState.PORTAL -> PasscodeScreen(viewModel)
            AppState.WEBVIEW -> {
                val url by viewModel.activeUrl.collectAsState()
                url?.let {
                    WebViewScreen(
                        url = it,
                        onReturnHome = {
                            viewModel.logout()
                        },
                        onSwitchSite = {
                            viewModel.returnToPortal()
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
            }

            Box(modifier = Modifier.weight(1f).padding(16.dp)) {
                if (tabIndex == 0) {
                    SitesManager(viewModel, cardBg, textColors)
                } else {
                    AccountsManager(viewModel, cardBg, textColors)
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
                    IconButton(onClick = { viewModel.deleteSite(site) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color.Red)
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
                        IconButton(onClick = { viewModel.deleteAccount(acc) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun PasscodeScreen(viewModel: MainViewModel) {
    var passcode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    val isDark = isSystemInDarkTheme()
    
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
            }
            
            // Bottom Icons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.logout() },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .size(48.dp)
                        .background(cardBg, CircleShape)
                        .border(1.dp, borderColor, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "退出登录", tint = textColors, modifier = Modifier.alpha(0.6f))
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
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                    }
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {}
                    webChromeClient = WebChromeClient()

                    loadUrl(activeUrl)
                }
                webViewCache[activeUrl] = webView
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

@Composable
fun SplashScreen(viewModel: MainViewModel) {
    LaunchedEffect(Unit) {
        delay(2000) // Show for 2 seconds
        viewModel.finishSplash()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        coil.compose.AsyncImage(
            model = "http://47.238.233.72:3000/static/splash_image.png",
            contentDescription = "Splash Screen",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

