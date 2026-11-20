package com.reiux.lumen.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.reiux.lumen.data.model.GoogleAccount
import com.reiux.lumen.data.network.GoogleAuthClient
import com.reiux.lumen.util.LumenLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

class AccountRepository(
    context: Context,
    private val authClient: GoogleAuthClient = GoogleAuthClient(),
    sharedPreferencesOverride: SharedPreferences? = null
) {
    // Keep reference to both so we can expose isSecureStorageAvailable
    private val prefsPair = if (sharedPreferencesOverride != null) {
        Pair(sharedPreferencesOverride, true)
    } else {
        createEncryptedSharedPreferences(context)
    }
    
    private val prefs: SharedPreferences = prefsPair.first

    private val _isSecureStorageAvailable = MutableStateFlow(prefsPair.second)
    val isSecureStorageAvailable: StateFlow<Boolean> = _isSecureStorageAvailable.asStateFlow()

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
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    try {
                        val obj = array.getJSONObject(i)
                        val email = obj.getString("email")
                        val cred = obj.getString("credentialString")
                        val displayName = if (!obj.isNull("displayName")) {
                            obj.optString("displayName", "").takeIf { it != "null" && it.isNotEmpty() }
                        } else null
                        val avatarPath = if (!obj.isNull("avatarPath")) {
                            obj.optString("avatarPath", "").takeIf { it != "null" && it.isNotEmpty() }
                        } else null
                        list.add(
                            GoogleAccount(
                                email = email,
                                credentialString = cred,
                                displayName = displayName,
                                avatarPath = avatarPath,
                                androidId = obj.optString("androidId", ""),
                                masterToken = obj.optString("masterToken", "")
                            )
                        )
                    } catch (itemErr: Exception) {
                        LumenLogger.w("Storage") { "Skipping malformed account record at index $i: ${itemErr.javaClass.simpleName}" }
                    }
                }
            } catch (e: Exception) {
                LumenLogger.w("Storage") { "Failed to parse stored accounts JSON: ${e.javaClass.simpleName}" }
            }
        }
        _accounts.value = list
        _selectedAccount.value = list.firstOrNull { it.email.equals(selectedEmail, ignoreCase = true) } ?: list.firstOrNull()
    }

    suspend fun addAccountFromOAuthToken(oauthToken: String): Result<GoogleAccount> {
        return runCatching {
            val account = authClient.exchangeOAuthToken(oauthToken)
            authClient.invalidateCache(account.email)
            saveAccount(account)
            account
        }
    }

    fun importCredentialString(credential: String): Result<GoogleAccount> {
        return runCatching {
            val map = parseQueryString(credential.trim())
            val email = map["Email"]?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Missing Email in credential")
            val token = map["Token"]?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Missing Token in credential")
            val androidId = map["androidId"]?.trim() ?: ""

            val sanitizedEntries = LinkedHashMap<String, String>()
            for ((rawKey, rawValue) in map) {
                val canonicalKey = ALLOWED_CREDENTIAL_KEYS_BY_LOWER[rawKey.lowercase()] ?: continue
                val cleanValue = rawValue.replace(Regex("[\\r\\n\\u0000]"), "").trim()
                if (cleanValue.isNotEmpty()) {
                    sanitizedEntries[canonicalKey] = cleanValue
                }
            }
            sanitizedEntries["Email"] = email
            sanitizedEntries["Token"] = token
            if (androidId.isNotEmpty()) {
                sanitizedEntries["androidId"] = androidId
            }

            val sanitizedCredential = sanitizedEntries.entries.joinToString("&") { (k, v) ->
                java.net.URLEncoder.encode(k, "UTF-8") + "=" + java.net.URLEncoder.encode(v, "UTF-8")
            }

            val account = GoogleAccount(
                email = email,
                credentialString = sanitizedCredential,
                androidId = androidId,
                masterToken = token
            )
            authClient.invalidateCache(email)
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
        val account = _accounts.value.firstOrNull { it.email.equals(email, ignoreCase = true) }
        if (account != null) {
            prefs.edit().putString(KEY_SELECTED_ACCOUNT, account.email).apply()
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
        authClient.invalidateCache(email)
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
        _selectedAccount.value = list.firstOrNull { it.email.equals(selectedEmail, ignoreCase = true) }
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
        private const val LEGACY_PREFS_NAME = "lumen_accounts"

        private val ALLOWED_CREDENTIAL_KEYS = listOf(
            "androidId",
            "app",
            "callerPkg",
            "callerSig",
            "client_sig",
            "device_country",
            "Email",
            "google_play_services_version",
            "lang",
            "oauth2_foreground",
            "operatorCountry",
            "sdk_version",
            "service",
            "source",
            "Token",
            "accountType",
            "has_permission"
        )
        private val ALLOWED_CREDENTIAL_KEYS_BY_LOWER = ALLOWED_CREDENTIAL_KEYS.associateBy { it.lowercase() }

        internal fun createEncryptedSharedPreferences(context: Context): Pair<SharedPreferences, Boolean> {
            val unencryptedPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)

            return try {
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

                val encryptedPrefs = EncryptedSharedPreferences.create(
                    "lumen_accounts_encrypted",
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )

                migrateLegacyAccountsIfNeeded(unencryptedPrefs, encryptedPrefs, context)
                Pair(encryptedPrefs, true)
            } catch (e: Exception) {
                LumenLogger.w("Storage") { "EncryptedSharedPreferences initialization failed (${e.javaClass.simpleName}: ${e.message})" }
                Pair(InMemorySharedPreferences(), false)
            }
        }

        internal fun migrateLegacyAccountsIfNeeded(
            unencryptedPrefs: SharedPreferences,
            encryptedPrefs: SharedPreferences,
            context: Context? = null
        ) {
            val legacyJson = unencryptedPrefs.getString(KEY_ACCOUNTS, null)
            val legacySelected = unencryptedPrefs.getString(KEY_SELECTED_ACCOUNT, null)

            if (!legacyJson.isNullOrEmpty()) {
                if (!encryptedPrefs.contains(KEY_ACCOUNTS)) {
                    val editor = encryptedPrefs.edit()
                    editor.putString(KEY_ACCOUNTS, legacyJson)
                    if (legacySelected != null) {
                        editor.putString(KEY_SELECTED_ACCOUNT, legacySelected)
                    }
                    val success = editor.commit()
                    val verifyJson = encryptedPrefs.getString(KEY_ACCOUNTS, null)

                    if (success && verifyJson == legacyJson) {
                        LumenLogger.i("Storage") { "Successfully migrated lumen_accounts to EncryptedSharedPreferences" }
                        unencryptedPrefs.edit().clear().commit()
                        if (context != null) {
                            try {
                                context.deleteSharedPreferences(LEGACY_PREFS_NAME)
                            } catch (_: Exception) {}
                        }
                    } else {
                        LumenLogger.w("Storage") { "EncryptedSharedPreferences migration failed or data mismatch. Leaving legacy data intact." }
                    }
                } else {
                    unencryptedPrefs.edit().clear().commit()
                    if (context != null) {
                        try {
                            context.deleteSharedPreferences(LEGACY_PREFS_NAME)
                        } catch (_: Exception) {}
                    }
                }
            } else if (context != null) {
                try {
                    context.deleteSharedPreferences(LEGACY_PREFS_NAME)
                } catch (_: Exception) {}
            }
        }
    }
}

internal class InMemorySharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = HashMap(data)
    override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = data[key] as? Set<String> ?: defValues
    override fun getInt(key: String, defValue: Int): Int = data[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = data[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = data[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = data.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    private inner class EditorImpl : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private val removes = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { if (value == null) { removes.add(key); changes.remove(key) } else { changes[key] = value; removes.remove(key) } }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = apply { if (values == null) { removes.add(key); changes.remove(key) } else { changes[key] = values; removes.remove(key) } }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { changes[key] = value; removes.remove(key) }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { changes[key] = value; removes.remove(key) }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { changes[key] = value; removes.remove(key) }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { changes[key] = value; removes.remove(key) }
        override fun remove(key: String): SharedPreferences.Editor = apply { removes.add(key); changes.remove(key) }
        override fun clear(): SharedPreferences.Editor = apply { clear = true; changes.clear(); removes.clear() }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) data.clear()
            for (key in removes) data.remove(key)
            for ((key, value) in changes) data[key] = value
        }
    }
}
