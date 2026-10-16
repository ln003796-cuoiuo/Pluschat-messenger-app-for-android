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
    public static void clear(Context context) {
        try { SharedPreferences p = prefs(context); String device = p.getString("device_key", ""); p.edit().clear().putString("device_key", device).apply(); } catch (Exception ignored) {}
    }
}
