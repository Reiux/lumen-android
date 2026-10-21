package com.reiux.lumen.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.reiux.lumen.data.model.GoogleAccount
import com.reiux.lumen.data.network.GoogleAuthClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

class AccountRepository(
    context: Context,
    private val authClient: GoogleAuthClient = GoogleAuthClient()
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("lumen_accounts", Context.MODE_PRIVATE)

    private val _accounts = MutableStateFlow<List<GoogleAccount>>(emptyList())
    val accounts: StateFlow<List<GoogleAccount>> = _accounts.asStateFlow()

    private val _selectedAccount = MutableStateFlow<GoogleAccount?>(null)
    val selectedAccount: StateFlow<GoogleAccount?> = _selectedAccount.asStateFlow()

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        val json = prefs.getString(KEY_ACCOUNTS, null)
        val selectedEmail = prefs.getString(KEY_SELECTED_ACCOUNT, null)

        val list = mutableListOf<GoogleAccount>()
        if (!json.isNullOrEmpty()) {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    GoogleAccount(
                        email = obj.getString("email"),
                        credentialString = obj.getString("credentialString"),
                        displayName = obj.optString("displayName", null.toString()).takeIf { it != "null" && it.isNotEmpty() },
                        avatarPath = obj.optString("avatarPath", null.toString()).takeIf { it != "null" && it.isNotEmpty() },
                        androidId = obj.optString("androidId", ""),
                        masterToken = obj.optString("masterToken", "")
                    )
                )
            }
        }
        _accounts.value = list
        _selectedAccount.value = list.firstOrNull { it.email == selectedEmail } ?: list.firstOrNull()
    }

    suspend fun addAccountFromOAuthToken(oauthToken: String): Result<GoogleAccount> {
        return runCatching {
            val account = authClient.exchangeOAuthToken(oauthToken)
            saveAccount(account)
            account
        }
    }

    fun importCredentialString(credential: String): Result<GoogleAccount> {
        return runCatching {
            val map = parseQueryString(credential)
            val email = map["Email"] ?: throw IllegalArgumentException("Missing Email in credential")
            val token = map["Token"] ?: throw IllegalArgumentException("Missing Token in credential")
            val androidId = map["androidId"] ?: ""

            val account = GoogleAccount(
                email = email,
                credentialString = credential,
                androidId = androidId,
                masterToken = token
            )
            saveAccount(account)
            account
        }
    }

    private fun saveAccount(account: GoogleAccount) {
        val current = _accounts.value.toMutableList()
        val index = current.indexOfFirst { it.email.equals(account.email, ignoreCase = true) }
        if (index >= 0) {
            val existing = current[index]
            current[index] = account.copy(
                displayName = existing.displayName ?: account.displayName,
                avatarPath = existing.avatarPath ?: account.avatarPath
            )
        } else {
            current.add(account)
        }
        persist(current, account.email)
    }

    fun selectAccount(email: String) {
        val account = _accounts.value.firstOrNull { it.email == email }
        if (account != null) {
            prefs.edit().putString(KEY_SELECTED_ACCOUNT, email).apply()
            _selectedAccount.value = account
        }
    }

    fun updateDisplayName(email: String, displayName: String) {
        val current = _accounts.value.map {
            if (it.email.equals(email, ignoreCase = true)) {
                it.copy(displayName = displayName.ifBlank { null })
            } else it
        }
        persist(current, _selectedAccount.value?.email)
    }

    fun updateAvatar(email: String, avatarPath: String?) {
        val current = _accounts.value.map {
            if (it.email.equals(email, ignoreCase = true)) {
                it.copy(avatarPath = avatarPath)
            } else it
        }
        persist(current, _selectedAccount.value?.email)
    }

    fun removeAccount(email: String) {
        val current = _accounts.value.filterNot { it.email.equals(email, ignoreCase = true) }
        val newSelected = if (_selectedAccount.value?.email.equals(email, ignoreCase = true)) {
            current.firstOrNull()?.email
        } else {
            _selectedAccount.value?.email
        }
        persist(current, newSelected)
    }

    private fun persist(list: List<GoogleAccount>, selectedEmail: String?) {
        val array = JSONArray()
        for (acc in list) {
            val obj = JSONObject()
            obj.put("email", acc.email)
            obj.put("credentialString", acc.credentialString)
            obj.put("displayName", acc.displayName ?: JSONObject.NULL)
            obj.put("avatarPath", acc.avatarPath ?: JSONObject.NULL)
            obj.put("androidId", acc.androidId)
            obj.put("masterToken", acc.masterToken)
            array.put(obj)
        }
        prefs.edit()
            .putString(KEY_ACCOUNTS, array.toString())
            .putString(KEY_SELECTED_ACCOUNT, selectedEmail)
            .apply()

        _accounts.value = list
        _selectedAccount.value = list.firstOrNull { it.email == selectedEmail }
    }

    private fun parseQueryString(query: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                map[key] = value
            }
        }
        return map
    }

    companion object {
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_SELECTED_ACCOUNT = "selected_account"
    }
}
