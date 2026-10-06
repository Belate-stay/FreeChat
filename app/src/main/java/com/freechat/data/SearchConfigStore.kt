package com.freechat.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Separate preferences, excluded from cloud/device backups and never exposed to SettingsBridge. */
class SearchConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("freechat_search_local", Context.MODE_PRIVATE)
    private val alias = "freechat_search_api_key_v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun load(): SearchConfig {
        val provider = runCatching { SearchProvider.valueOf(prefs.getString("provider", "FREE")!!) }.getOrDefault(SearchProvider.FREE)
        val encrypted = prefs.getString("secret", "").orEmpty()
        val secret = if (encrypted.isBlank()) "" else runCatching {
            val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }.getOrDefault("")
        return SearchConfig(provider, prefs.getString("endpoint", "").orEmpty(), secret)
    }
    fun save(config: SearchConfig) {
        require(config.validationError() == null)
        val encrypted = if (config.apiKey.isBlank()) "" else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            Base64.encodeToString(cipher.iv + cipher.doFinal(config.apiKey.trim().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        }
        check(prefs.edit().putString("provider", config.provider.name).putString("endpoint", config.endpoint.trim())
            .putString("secret", encrypted).commit()) { "Could not save search settings" }
    }
}
