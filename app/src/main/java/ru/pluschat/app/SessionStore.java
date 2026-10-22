package ru.pluschat.app;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public final class SessionStore {
    private static final String FILE = "pluschat_secure_session";
    private SessionStore() {}

    private static SharedPreferences prefs(Context context) throws Exception {
        MasterKey key = new MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
        return EncryptedSharedPreferences.create(context, FILE, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }

    public static String token(Context context) {
        try { return prefs(context).getString("access_token", ""); }
        catch (Exception e) { return ""; }
    }
    public static String deviceKey(Context context) {
        try {
            SharedPreferences p = prefs(context);
            String key = p.getString("device_key", "");
            if (key.matches("[a-fA-F0-9]{64}")) return key;
            byte[] bytes = new byte[32];
            new java.security.SecureRandom().nextBytes(bytes);
            StringBuilder out = new StringBuilder(64);
            for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            key = out.toString();
            p.edit().putString("device_key", key).apply();
            return key;
        } catch (Exception e) { return ""; }
    }

    public static void save(Context context, String accessToken, String refreshToken) throws Exception {
        prefs(context).edit().putString("access_token", accessToken)
            .putString("refresh_token", refreshToken).apply();
    }
    public static String cryptoGet(Context context, String key) {
        try { return prefs(context).getString("crypto_" + key, ""); }
        catch (Exception e) { return ""; }
    }
    public static void cryptoPut(Context context, String key, String value) throws Exception {
        prefs(context).edit().putString("crypto_" + key, value).apply();
    }

    public static void rememberAccount(Context context, String accessToken, String refreshToken, String label) throws Exception {
        if (accessToken == null || accessToken.isEmpty()) return;
        String key = android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(accessToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)), android.util.Base64.NO_WRAP | android.util.Base64.URL_SAFE);
        SharedPreferences p = prefs(context);
        org.json.JSONArray accounts;
        try { accounts = new org.json.JSONArray(p.getString("account_index", "[]")); }
        catch (Exception e) { accounts = new org.json.JSONArray(); }
        boolean found = false;
        for (int i = 0; i < accounts.length(); i++) {
            org.json.JSONObject item = accounts.optJSONObject(i);
            if (item != null && key.equals(item.optString("key"))) {
                item.put("label", label == null || label.isEmpty() ? "Аккаунт" : label);
                found = true;
            }
        }
        if (!found) accounts.put(new org.json.JSONObject().put("key", key).put("label", label == null || label.isEmpty() ? "Аккаунт" : label));
        p.edit().putString("account_index", accounts.toString())
            .putString("account_token_" + key, accessToken)
            .putString("account_refresh_" + key, refreshToken == null ? "" : refreshToken).apply();
    }
    public static org.json.JSONArray accounts(Context context) {
        try { return new org.json.JSONArray(prefs(context).getString("account_index", "[]")); }
        catch (Exception e) { return new org.json.JSONArray(); }
    }
    public static boolean activateAccount(Context context, String key) {
        try {
            SharedPreferences p = prefs(context);
            String token = p.getString("account_token_" + key, "");
            String refresh = p.getString("account_refresh_" + key, "");
            if (token.isEmpty()) return false;
            save(context, token, refresh);
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean appLockEnabled(Context context) {
        try { return prefs(context).getBoolean("app_lock_enabled", false); }
        catch (Exception e) { return false; }
    }
    public static void setAppLockEnabled(Context context, boolean enabled) {
        try { prefs(context).edit().putBoolean("app_lock_enabled", enabled).apply(); }
        catch (Exception ignored) {}
    }

    public static boolean isChatHidden(Context context, String chatId) {
        return "1".equals(cryptoGet(context, "hidden_chat_" + chatId));
    }
    public static void setChatHidden(Context context, String chatId, boolean hidden) throws Exception {
        if (hidden) cryptoPut(context, "hidden_chat_" + chatId, "1");
        else cryptoRemove(context, "hidden_chat_" + chatId);
    }

    public static void cryptoRemove(Context context, String key) {
        try { prefs(context).edit().remove("crypto_" + key).apply(); }
        catch (Exception ignored) {}
    }

    public static void clear(Context context) {
        // Keep device binding and E2EE private keys across logout. Deleting crypto keys
        // while the server still pins the old public identity would make this device
        // unable to register again or decrypt its existing messages.
        try {
            prefs(context).edit().remove("access_token").remove("refresh_token").apply();
        } catch (Exception ignored) {}
    }
}
