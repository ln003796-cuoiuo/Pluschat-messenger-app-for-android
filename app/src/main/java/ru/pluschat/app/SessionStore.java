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
    public static void save(Context context, String accessToken, String refreshToken) throws Exception {
        prefs(context).edit().putString("access_token", accessToken)
            .putString("refresh_token", refreshToken).apply();
    }
    public static void clear(Context context) {
        try { prefs(context).edit().clear().apply(); } catch (Exception ignored) {}
    }
}
