package com.reiux.lumen.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.reiux.lumen.data.model.AccountBlock
import com.reiux.lumen.data.model.AuthFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class FakeSharedPreferences : SharedPreferences {
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

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    fun putDirect(key: String, value: Any?) {
        data[key] = value
    }

    private inner class EditorImpl : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private val removes = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            if (value == null) removes.add(key) else { changes[key] = value; removes.remove(key) }
            return this
        }

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            if (values == null) removes.add(key) else { changes[key] = values; removes.remove(key) }
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            changes[key] = value
            removes.remove(key)
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            changes[key] = value
            removes.remove(key)
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            changes[key] = value
            removes.remove(key)
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            changes[key] = value
            removes.remove(key)
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            removes.add(key)
            changes.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            changes.clear()
            removes.clear()
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) {
                data.clear()
            }
            for (key in removes) {
                data.remove(key)
            }
            for ((key, value) in changes) {
                data[key] = value
            }
        }
    }
}

class RepositoryAndSecurityTest {

    @Test
    fun testAccountRepositoryStorageAndOperations() {
        val dummyContext = DummyContext()
        val fakePrefs = dummyContext.getSharedPreferences("lumen_accounts_encrypted", Context.MODE_PRIVATE) as FakeSharedPreferences
        val repo = AccountRepository(
            context = dummyContext,
            sharedPreferencesOverride = fakePrefs
        )

        assertEquals(0, repo.accounts.value.size)
        assertNull(repo.selectedAccount.value)

        val credential = "Email=test.user%40gmail.com&Token=master_token_123&androidId=android_id_456"
        val result = repo.importCredentialString(credential)
        assertTrue(result.isSuccess)

        val account = result.getOrNull()
        assertNotNull(account)
        assertEquals("test.user@gmail.com", account?.email)
        assertEquals("master_token_123", account?.masterToken)
        assertEquals("android_id_456", account?.androidId)

        assertEquals(1, repo.accounts.value.size)
        assertEquals("test.user@gmail.com", repo.selectedAccount.value?.email)

        repo.updateDisplayName("test.user@gmail.com", "Test User Display Name")
        assertEquals("Test User Display Name", repo.selectedAccount.value?.displayName)

        val reloadedRepo = AccountRepository(context = dummyContext, sharedPreferencesOverride = fakePrefs)
        assertEquals(1, reloadedRepo.accounts.value.size)
        assertEquals("test.user@gmail.com", reloadedRepo.accounts.value.first().email)
        assertEquals("master_token_123", reloadedRepo.accounts.value.first().masterToken)
        assertEquals("Test User Display Name", reloadedRepo.accounts.value.first().displayName)

        repo.removeAccount("test.user@gmail.com")
        assertEquals(0, repo.accounts.value.size)
        assertNull(repo.selectedAccount.value)
    }

    @Test
    fun testLegacyAccountMigration() {
        val dummyContext = DummyContext()
        val legacyPrefs = dummyContext.getSharedPreferences("lumen_accounts", Context.MODE_PRIVATE) as FakeSharedPreferences
        val encryptedPrefs = dummyContext.getSharedPreferences("lumen_accounts_encrypted", Context.MODE_PRIVATE) as FakeSharedPreferences

        val legacyArray = JSONArray().apply {
            put(JSONObject().apply {
                put("email", "migrated.user@gmail.com")
                put("credentialString", "Email=migrated.user%40gmail.com&Token=migrated_token")
                put("masterToken", "migrated_token")
                put("androidId", "migrated_android_id")
            })
        }
        legacyPrefs.putDirect("accounts", legacyArray.toString())
        legacyPrefs.putDirect("selected_account", "migrated.user@gmail.com")

        AccountRepository.migrateLegacyAccountsIfNeeded(legacyPrefs, encryptedPrefs)

        val migratedRepo = AccountRepository(context = dummyContext, sharedPreferencesOverride = encryptedPrefs)
        assertEquals("migrated.user@gmail.com", migratedRepo.accounts.value.first().email)
        assertEquals("migrated.user@gmail.com", migratedRepo.selectedAccount.value?.email)
        assertFalse(legacyPrefs.contains("accounts"))
    }

    @Test
    fun testSettingsRepositoryFolderTogglesAndStaleBlocks() {
        val dummyContext = DummyContext()
        val fakePrefs = dummyContext.getSharedPreferences("lumen_settings", Context.MODE_PRIVATE) as FakeSharedPreferences
        val settingsRepo = SettingsRepository(
            context = dummyContext,
            sharedPreferencesOverride = fakePrefs
        )

        val defaultFolders = settingsRepo.settings.value.folders
        assertTrue(defaultFolders.isNotEmpty())

        val cameraPath = defaultFolders.first().path
        assertTrue(defaultFolders.first().enabled)

        settingsRepo.toggleFolder(cameraPath, false)
        assertFalse(settingsRepo.settings.value.folders.first { it.path == cameraPath }.enabled)

        settingsRepo.setIncludeSubfolders(cameraPath, true)
        assertTrue(settingsRepo.settings.value.folders.first { it.path == cameraPath }.includeSubfolders)

        settingsRepo.setAutoCheckUpdates(false)
        assertFalse(settingsRepo.settings.value.autoCheckUpdates)

        val activeBlock = AccountBlock(
            kind = AuthFailureKind.SIGN_IN_REQUIRED,
            accountEmail = "valid.user@gmail.com"
        )
        settingsRepo.setAccountBlock(activeBlock)

        val staleBlock = AccountBlock(
            kind = AuthFailureKind.ACCOUNT_RESTRICTED,
            accountEmail = "deleted.user@gmail.com"
        )
        settingsRepo.setAccountBlock(staleBlock)

        assertNotNull(settingsRepo.getAccountBlock("valid.user@gmail.com"))
        assertNotNull(settingsRepo.getAccountBlock("deleted.user@gmail.com"))

        settingsRepo.cleanStaleAccountBlocks(setOf("valid.user@gmail.com"))

        assertNotNull(settingsRepo.getAccountBlock("valid.user@gmail.com"))
        assertNull(settingsRepo.getAccountBlock("deleted.user@gmail.com"))
    }

    @Test
    fun testUpdateRepositoryVersionComparison() {
        val updateRepo = UpdateRepository()
        val isNewerMethod = updateRepo.javaClass.getDeclaredMethod("isNewerVersion", String::class.java, String::class.java).apply {
            isAccessible = true
        }

        fun checkNewer(current: String, latest: String): Boolean {
            return isNewerMethod.invoke(updateRepo, current, latest) as Boolean
        }

        assertTrue(checkNewer("1.0.0", "1.1.0"))
        assertTrue(checkNewer("1.1.0", "1.1.1"))
        assertTrue(checkNewer("1.9.9", "2.0.0"))
        
        assertFalse(checkNewer("1.1.1-dev", "1.1.1"))
        assertFalse(checkNewer("1.1.1", "1.1.1"))
        assertFalse(checkNewer("1.2.0", "1.1.1"))
    }

    private class DummyContext : ContextWrapper(null) {
        private val prefsMap = mutableMapOf<String, FakeSharedPreferences>()
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            return prefsMap.getOrPut(name) { FakeSharedPreferences() }
        }
        override fun getApplicationContext(): Context = this
    }
}
