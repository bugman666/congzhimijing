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

enum class AppState {
    SPLASH, LOGIN, ADMIN_DASHBOARD, PORTAL, WEBVIEW
}

class MainViewModel(private val sharedPrefs: SharedPreferences) : ViewModel() {

    val appState = MutableStateFlow(AppState.SPLASH)
    val activeUrl = MutableStateFlow<String?>(null)
    
    // We can hold isAdmin config in memory or preferences
    private var isAdminUser = false

    private val apiService = ApiClient.apiService

    private val _allAccounts = MutableStateFlow<List<Account>>(emptyList())
    val allAccounts: StateFlow<List<Account>> = _allAccounts

    private val _allSites = MutableStateFlow<List<Site>>(emptyList())
    val allSites: StateFlow<List<Site>> = _allSites

    private val token: String
        get() = "Bearer " + (sharedPrefs.getString("jwt_token", "") ?: "")

    fun finishSplash() {
        if (appState.value == AppState.SPLASH) {
            val savedToken = sharedPrefs.getString("jwt_token", "")
            if (!savedToken.isNullOrEmpty()) {
                isAdminUser = sharedPrefs.getBoolean("is_admin", false)
                if (isAdminUser) {
                    appState.value = AppState.ADMIN_DASHBOARD
                    fetchAdminData()
                } else {
                    appState.value = AppState.PORTAL
                }
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
                    
                    isAdminUser = loginRes.isAdmin
                    
                    if (isAdminUser) {
                        appState.value = AppState.ADMIN_DASHBOARD
                        fetchAdminData()
                    } else {
                        appState.value = AppState.PORTAL
                    }
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
        isAdminUser = false
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

    fun returnToPortal() {
        if (isAdminUser) {
            appState.value = AppState.ADMIN_DASHBOARD
        } else {
            appState.value = AppState.PORTAL
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
