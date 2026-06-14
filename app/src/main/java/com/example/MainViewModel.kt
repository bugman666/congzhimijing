package com.example

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Account
import com.example.data.ApiClient
import com.example.data.LoginRequest
import com.example.data.Site
import com.example.data.UnlockRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.example.data.Announcement
import com.example.data.AnnouncementRequest
import com.example.data.Comment
import com.example.data.CommentRequest
import com.example.data.AppConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

enum class AppState {
    SPLASH, LOGIN, ADMIN_DASHBOARD, PORTAL, WEBVIEW
}

class MainViewModel(private val sharedPrefs: SharedPreferences) : ViewModel() {

    val appState = MutableStateFlow(AppState.SPLASH)
    val activeUrl = MutableStateFlow<String?>(null)
    
    private val apiService = ApiClient.apiService

    private val _isAdminUser = MutableStateFlow(false)
    val isAdminUser: StateFlow<Boolean> = _isAdminUser

    private val _allAccounts = MutableStateFlow<List<Account>>(emptyList())
    val allAccounts: StateFlow<List<Account>> = _allAccounts

    private val _allSites = MutableStateFlow<List<Site>>(emptyList())
    val allSites: StateFlow<List<Site>> = _allSites

    private val _announcements = MutableStateFlow<List<Announcement>>(emptyList())
    val announcements: StateFlow<List<Announcement>> = _announcements

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments

    private val _appConfig = MutableStateFlow(AppConfig())
    val appConfig: StateFlow<AppConfig> = _appConfig

    private val _nickname = MutableStateFlow(sharedPrefs.getString("user_nickname", "") ?: "")
    val nickname: StateFlow<String> = _nickname

    private val _lastNicknameChange = MutableStateFlow(sharedPrefs.getLong("user_nickname_last_change", 0L))
    val lastNicknameChange: StateFlow<Long> = _lastNicknameChange

    fun updateNickname(newName: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = apiService.updateNickname(token, com.example.data.NicknameRequest(newName))
                if (response.isSuccessful) {
                    val currentTime = System.currentTimeMillis()
                    sharedPrefs.edit()
                        .putString("user_nickname", newName)
                        .putLong("user_nickname_last_change", currentTime)
                        .apply()
                    _nickname.value = newName
                    _lastNicknameChange.value = currentTime
                    onResult(true, null)
                } else if (response.code() == 429) {
                    onResult(false, "修改太频繁，请3天后再试")
                } else {
                    onResult(false, "修改失败，请重试")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Update nickname error", e)
                onResult(false, "网络错误，请重试")
            }
        }
    }

    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class UnlockedSite(val name: String, val url: String)
    private val _unlockedSites = MutableStateFlow<List<UnlockedSite>>(emptyList())
    val unlockedSites: StateFlow<List<UnlockedSite>> = _unlockedSites

    private val moshi = Moshi.Builder().build()

    private val token: String
        get() = "Bearer " + (sharedPrefs.getString("jwt_token", "") ?: "")

    fun finishSplash() {
        if (appState.value == AppState.SPLASH) {
            val savedToken = sharedPrefs.getString("jwt_token", "")
            if (!savedToken.isNullOrEmpty()) {
                _isAdminUser.value = sharedPrefs.getBoolean("is_admin", false)
                appState.value = AppState.PORTAL
                loadUnlockedSites()
                fetchAnnouncements()
                fetchComments()
                fetchConfig()
            } else {
                appState.value = AppState.LOGIN
            }
        }
    }

    fun login(username: String, pass: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val response = apiService.login(LoginRequest(username, pass))
                if (response.isSuccessful && response.body() != null) {
                    val loginRes = response.body()!!
                    sharedPrefs.edit()
                        .putString("jwt_token", loginRes.token)
                        .putBoolean("is_admin", loginRes.isAdmin)
                        .apply()
                    
                    _isAdminUser.value = loginRes.isAdmin
                    
                    appState.value = AppState.PORTAL
                    loadUnlockedSites()
                    fetchAnnouncements()
                    fetchComments()
                    fetchConfig()
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Login error", e)
                onResult(false)
            }
        }
    }

    fun logout() {
        sharedPrefs.edit().clear().apply()
        _isAdminUser.value = false
        activeUrl.value = null
        _allAccounts.value = emptyList()
        _allSites.value = emptyList()
        appState.value = AppState.LOGIN
    }

    fun attemptSiteUnlock(passcode: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = apiService.unlock(token, UnlockRequest(passcode))
                if (response.isSuccessful && response.body() != null) {
                    val unlockRes = response.body()!!
                    activeUrl.value = unlockRes.url
                    appState.value = AppState.WEBVIEW
                    
                    // Save to local unlocked sites
                    val currentSites = _unlockedSites.value.toMutableList()
                    if (currentSites.none { it.url == unlockRes.url }) {
                        currentSites.add(UnlockedSite(unlockRes.name, unlockRes.url))
                        _unlockedSites.value = currentSites
                        saveUnlockedSites(currentSites)
                    }
                    
                    onResult(true, unlockRes.url)
                } else {
                    onResult(false, null)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Unlock error", e)
                onResult(false, null)
            }
        }
    }

    fun openUnlockedSite(site: UnlockedSite) {
        activeUrl.value = site.url
        appState.value = AppState.WEBVIEW
    }

    private fun saveUnlockedSites(sites: List<UnlockedSite>) {
        val adapter = moshi.adapter<List<UnlockedSite>>(Types.newParameterizedType(List::class.java, UnlockedSite::class.java))
        sharedPrefs.edit().putString("unlocked_sites", adapter.toJson(sites)).apply()
    }

    fun loadUnlockedSites() {
        val json = sharedPrefs.getString("unlocked_sites", null)
        if (!json.isNullOrEmpty()) {
            try {
                val adapter = moshi.adapter<List<UnlockedSite>>(Types.newParameterizedType(List::class.java, UnlockedSite::class.java))
                val sites = adapter.fromJson(json) ?: emptyList()
                _unlockedSites.value = sites
            } catch (e: Exception) {
                Log.e("MainViewModel", "Load sites error", e)
            }
        }
    }

    fun goToAdmin() {
        if (_isAdminUser.value) {
            appState.value = AppState.ADMIN_DASHBOARD
            fetchAdminData()
        }
    }

    val currentTabIndex = MutableStateFlow(0)

    fun returnToPortal(tabIndex: Int = 0) {
        currentTabIndex.value = tabIndex
        appState.value = AppState.PORTAL
    }

    // Announcements
    private fun fetchAnnouncements() {
        viewModelScope.launch {
            try {
                val response = apiService.getAnnouncements(token)
                if (response.isSuccessful) {
                    _announcements.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Fetch Announcements error", e)
            }
        }
    }

    fun addAnnouncement(content: String) {
        viewModelScope.launch {
            try {
                val response = apiService.addAnnouncement(token, AnnouncementRequest(content))
                if (response.isSuccessful) {
                    fetchAnnouncements()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Add Announcement error", e)
            }
        }
    }

    fun updateAnnouncement(id: Int, content: String) {
        viewModelScope.launch {
            try {
                val response = apiService.updateAnnouncement(token, id, AnnouncementRequest(content))
                if (response.isSuccessful) {
                    fetchAnnouncements()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Update Announcement error", e)
            }
        }
    }

    fun deleteAnnouncement(id: Int) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteAnnouncement(token, id)
                if (response.isSuccessful) {
                    fetchAnnouncements()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Delete Announcement error", e)
            }
        }
    }

    private fun fetchComments() {
        viewModelScope.launch {
            try {
                val response = apiService.getComments(token)
                if (response.isSuccessful) {
                    _comments.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Fetch Comments error", e)
            }
        }
    }

    fun addComment(content: String) {
        viewModelScope.launch {
            try {
                val authorName = if (_nickname.value.isNotBlank()) _nickname.value else null
                val response = apiService.addComment(token, CommentRequest(content, authorName))
                if (response.isSuccessful) {
                    fetchComments()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Add Comment error", e)
            }
        }
    }

    fun deleteComment(id: Int) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteComment(token, id)
                if (response.isSuccessful) {
                    fetchComments()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Delete Comment error", e)
            }
        }
    }

    fun updateComment(id: Int, content: String) {
        viewModelScope.launch {
            try {
                val authorName = if (_nickname.value.isNotBlank()) _nickname.value else null
                val response = apiService.updateComment(token, id, CommentRequest(content, authorName))
                if (response.isSuccessful) {
                    fetchComments()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Update Comment error", e)
            }
        }
    }
    
    fun fetchConfig() {
        viewModelScope.launch {
            try {
                val response = apiService.getConfig(token)
                if (response.isSuccessful) {
                    response.body()?.let { _appConfig.value = it }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Fetch Config error", e)
            }
        }
    }

    fun updateConfig(aboutAuthor: String, updateInfo: String) {
        viewModelScope.launch {
            try {
                val response = apiService.updateConfig(token, AppConfig(aboutAuthor, updateInfo))
                if (response.isSuccessful) {
                    response.body()?.let { _appConfig.value = it }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Update Config error", e)
            }
        }
    }

    // Admin Tools
    private fun fetchAdminData() {
        viewModelScope.launch {
             try {
                 val sitesRes = apiService.getSites(token)
                 if (sitesRes.isSuccessful) _allSites.value = sitesRes.body() ?: emptyList()
                 
                 val accountsRes = apiService.getAccounts(token)
                 if (accountsRes.isSuccessful) _allAccounts.value = accountsRes.body() ?: emptyList()
             } catch (e: Exception) {
                 Log.e("MainViewModel", "Fetch Admin Data error", e)
             }
        }
    }

    fun addAccount(username: String, pass: String, isAdmin: Boolean) {
        viewModelScope.launch {
            try {
                val acc = Account(username = username, passcode = pass, isAdmin = isAdmin)
                val response = apiService.addAccount(token, acc)
                if (response.isSuccessful) {
                    fetchAdminData()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Add Account error", e)
            }
        }
    }

    fun updateAccount(id: Int, username: String, pass: String, isAdmin: Boolean) {
        viewModelScope.launch {
            try {
                val acc = Account(id = id, username = username, passcode = pass, isAdmin = isAdmin)
                val response = apiService.updateAccount(token, id, acc)
                if (response.isSuccessful) {
                    fetchAdminData()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Update Account error", e)
            }
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteAccount(token, account.id)
                if (response.isSuccessful) {
                    fetchAdminData()
                }
            } catch (e: Exception) {
                 Log.e("MainViewModel", "Delete Account error", e)
            }
        }
    }

    fun addSite(name: String, url: String, passcode: String) {
        viewModelScope.launch {
            try {
                val site = Site(name = name, url = url, passcode = passcode)
                val response = apiService.addSite(token, site)
                if (response.isSuccessful) {
                    fetchAdminData()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Add Site error", e)
            }
        }
    }

    fun updateSite(id: Int, name: String, url: String, passcode: String) {
        viewModelScope.launch {
            try {
                val site = Site(id = id, name = name, url = url, passcode = passcode)
                val response = apiService.updateSite(token, id, site)
                if (response.isSuccessful) {
                    fetchAdminData()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Update Site error", e)
            }
        }
    }

    fun deleteSite(site: Site) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteSite(token, site.id)
                if (response.isSuccessful) {
                    fetchAdminData()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Delete Site error", e)
            }
        }
    }
}
