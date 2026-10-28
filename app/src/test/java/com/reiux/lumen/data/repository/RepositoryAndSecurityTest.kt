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
            if (value == null) {
                removes.add(key)
                changes.remove(key)
            } else {
                changes[key] = value
                removes.remove(key)
            }
            return this
        }

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            if (values == null) {
                removes.add(key)
                changes.remove(key)
            } else {
                changes[key] = values
                removes.remove(key)
            }
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

class FailingCommitFakeSharedPreferences : SharedPreferences by FakeSharedPreferences() {
    private val underlying = FakeSharedPreferences()
    
    override fun edit(): SharedPreferences.Editor = FailingEditor(underlying.edit())
    override fun contains(key: String): Boolean = underlying.contains(key)
    override fun getString(key: String, defValue: String?): String? = underlying.getString(key, defValue)
    
    private inner class FailingEditor(private val realEditor: SharedPreferences.Editor) : SharedPreferences.Editor by realEditor {
        override fun commit(): Boolean {
            // Apply still happens (to simulate an editor that applies but fails commit, or just fails)
            realEditor.apply()
            return false // Force failure
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

        val reloadedRepo = AccountRepository(
            context = dummyContext,
            sharedPreferencesOverride = fakePrefs
        )
        assertEquals(1, reloadedRepo.accounts.value.size)
        assertEquals("test.user@gmail.com", reloadedRepo.accounts.value.first().email)
        assertEquals("master_token_123", reloadedRepo.accounts.value.first().masterToken)
        assertEquals("android_id_456", reloadedRepo.accounts.value.first().androidId)
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

        val legacyJsonRaw = "[{\"email\":\"migrated.user@gmail.com\",\"credentialString\":\"Email=migrated.user%40gmail.com&Token=migrated_token\",\"masterToken\":\"migrated_token\",\"androidId\":\"migrated_android_id\"}]"
        legacyPrefs.putDirect("accounts", legacyJsonRaw)
        legacyPrefs.putDirect("selected_account", "migrated.user@gmail.com")

        AccountRepository.migrateLegacyAccountsIfNeeded(legacyPrefs, encryptedPrefs)

        assertTrue(encryptedPrefs.contains("accounts"))
        assertEquals("migrated.user@gmail.com", encryptedPrefs.getString("selected_account", null))
        assertFalse(legacyPrefs.contains("accounts"))

        val migratedRepo = AccountRepository(
            context = dummyContext,
            sharedPreferencesOverride = encryptedPrefs
        )
        assertEquals(1, migratedRepo.accounts.value.size)
        assertEquals("migrated.user@gmail.com", migratedRepo.accounts.value.first().email)
        assertEquals("migrated_token", migratedRepo.accounts.value.first().masterToken)
        assertEquals("migrated.user@gmail.com", migratedRepo.selectedAccount.value?.email)
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

        assertTrue(settingsRepo.settings.value.autoCheckUpdates)
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

    @Test
    fun testMigrationLeavesLegacyDataIntactOnCommitFailure() {
        val dummyContext = DummyContext()
        val legacyPrefs = dummyContext.getSharedPreferences("lumen_accounts", Context.MODE_PRIVATE) as FakeSharedPreferences
        
        // Setup legacy data
        val legacyJsonRaw = "[{\"email\":\"fail.user@gmail.com\",\"credentialString\":\"Email=fail\",\"masterToken\":\"t\",\"androidId\":\"a\"}]"
        legacyPrefs.putDirect("accounts", legacyJsonRaw)
        legacyPrefs.putDirect("selected_account", "fail.user@gmail.com")

        // Setup failing encrypted prefs
        val encryptedPrefs = FailingCommitFakeSharedPreferences()

        AccountRepository.migrateLegacyAccountsIfNeeded(legacyPrefs, encryptedPrefs)

        // Legacy data should still be there because commit() returned false
        assertTrue(legacyPrefs.contains("accounts"))
        assertEquals(legacyJsonRaw, legacyPrefs.getString("accounts", null))
        assertEquals("fail.user@gmail.com", legacyPrefs.getString("selected_account", null))
    }

    @Test
    fun testMigrationClearsLegacyDataOnlyOnSuccess() {
        val dummyContext = DummyContext()
        val legacyPrefs = dummyContext.getSharedPreferences("lumen_accounts", Context.MODE_PRIVATE) as FakeSharedPreferences
        val encryptedPrefs = dummyContext.getSharedPreferences("lumen_accounts_encrypted", Context.MODE_PRIVATE) as FakeSharedPreferences

        val legacyJsonRaw = "[{\"email\":\"success.user@gmail.com\",\"credentialString\":\"Email=success\",\"masterToken\":\"t\",\"androidId\":\"a\"}]"
        legacyPrefs.putDirect("accounts", legacyJsonRaw)
        
        AccountRepository.migrateLegacyAccountsIfNeeded(legacyPrefs, encryptedPrefs)

        // Ensure legacy is cleared
        assertFalse(legacyPrefs.contains("accounts"))
        
        // Ensure encrypted has the EXACT same JSON
        assertTrue(encryptedPrefs.contains("accounts"))
        assertEquals(legacyJsonRaw, encryptedPrefs.getString("accounts", null))
    }

    @Test
    fun testInMemorySharedPreferencesDoesNotPersist() {
        val dummyContext = DummyContext()
        
        // Instance 1
        val repo1 = AccountRepository(context = dummyContext, sharedPreferencesOverride = InMemorySharedPreferences())
        val credential = "Email=inmemory%40gmail.com&Token=mem_token&androidId=mem_id"
        repo1.importCredentialString(credential)
        
        assertEquals(1, repo1.accounts.value.size)
        assertEquals("inmemory@gmail.com", repo1.accounts.value.first().email)
        
        // Instance 2 with fresh InMemorySharedPreferences
        val repo2 = AccountRepository(context = dummyContext, sharedPreferencesOverride = InMemorySharedPreferences())
        assertEquals(0, repo2.accounts.value.size)
    }

    @Test
    fun testSettingsRepositoryAccountBlockPipesRoundTrip() {
        val dummyContext = DummyContext()
        val fakePrefs = dummyContext.getSharedPreferences("lumen_settings", Context.MODE_PRIVATE) as FakeSharedPreferences
        val repo = SettingsRepository(context = dummyContext, sharedPreferencesOverride = fakePrefs)

        val block = AccountBlock(
            kind = AuthFailureKind.SIGN_IN_REQUIRED,
            timestamp = 1000L,
            accountEmail = "pipe@gmail.com",
            retryAfterTimestamp = null,
            rawDetail = "error|with|pipes"
        )
        
        repo.setAccountBlock(block)
        val readBlock = repo.getAccountBlock("pipe@gmail.com")
        
        assertNotNull(readBlock)
        assertEquals(AuthFailureKind.SIGN_IN_REQUIRED, readBlock?.kind)
        assertEquals("error|with|pipes", readBlock?.rawDetail)
    }

    @Test
    fun testSettingsRepositoryOldPipeFormatParsesCorrectly() {
        val dummyContext = DummyContext()
        val fakePrefs = dummyContext.getSharedPreferences("lumen_settings", Context.MODE_PRIVATE) as FakeSharedPreferences
        
        // Write old pipe format directly
        fakePrefs.putDirect("account_block_old@gmail.com", "RATE_LIMITED|1700000000000|1700000600000|some detail")
        
        val repo = SettingsRepository(context = dummyContext, sharedPreferencesOverride = fakePrefs)
        
        // Use a fixed nowMillis to avoid expiration logic messing with the test
        // 1700000500000 is before the retryAfterTimestamp 1700000600000
        val block = repo.getAccountBlock("old@gmail.com")
        // Note: the test requested testing parsing. If getAccountBlock() internally uses System.currentTimeMillis(), 
        // it might return null if expired. RATE_LIMITED expires if now >= retryAfterTimestamp.
        // Let's write a SIGN_IN_REQUIRED pipe format instead, since SIGN_IN_REQUIRED does not auto-expire based on time.
        
        fakePrefs.putDirect("account_block_old2@gmail.com", "SIGN_IN_REQUIRED|1700000000000|-1|some detail")
        
        val block2 = repo.getAccountBlock("old2@gmail.com")
        assertNotNull(block2)
        assertEquals(AuthFailureKind.SIGN_IN_REQUIRED, block2?.kind)
        assertEquals(1700000000000L, block2?.timestamp)
        assertNull(block2?.retryAfterTimestamp)
        assertEquals("some detail", block2?.rawDetail)
        
        // For the rate limited test, let's just make the retryAfter in the far future to ensure it parses.
        fakePrefs.putDirect("account_block_old3@gmail.com", "RATE_LIMITED|1700000000000|3000000600000|some detail")
        val block3 = repo.getAccountBlock("old3@gmail.com")
        assertNotNull(block3)
        assertEquals(AuthFailureKind.RATE_LIMITED, block3?.kind)
        assertEquals(3000000600000L, block3?.retryAfterTimestamp)
        assertEquals("some detail", block3?.rawDetail)
    }

    private class DummyContext : ContextWrapper(null) {
        private val prefsMap = mutableMapOf<String, FakeSharedPreferences>()
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            return prefsMap.getOrPut(name) { FakeSharedPreferences() }
        }
        override fun getApplicationContext(): Context = this
    }
}
