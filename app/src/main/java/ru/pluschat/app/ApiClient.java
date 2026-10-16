package ru.pluschat.app;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ApiClient {
    public interface Callback { void onResult(JSONObject body, String error); }
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private ApiClient() {}

    public static void request(String method, String path, JSONObject payload, String token, Callback callback) {
        IO.execute(() -> {
            HttpURLConnection connection = null;
            JSONObject response = new JSONObject();
            String error = null;
            try {
                String base = BuildConfig.API_BASE_URL;
                URL url = new URL(base + path.replaceFirst("^/", ""));
                connection = (HttpURLConnection) url.openConnection();
                String actualMethod = method.equals("PATCH") ? "POST" : method;
                connection.setRequestMethod(actualMethod);
                if (method.equals("PATCH")) connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                if (token != null && !token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
                if (payload != null && !method.equals("GET")) {
                    connection.setDoOutput(true);
                    byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                    try (OutputStream os = connection.getOutputStream()) { os.write(bytes); }
                }
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
                if (stream != null) {
                    StringBuilder text = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line; while ((line = reader.readLine()) != null) text.append(line);
                    }
                    if (text.length() > 0) response = new JSONObject(text.toString());
                }
                if (code < 200 || code >= 300) {
                    JSONObject e = response.optJSONObject("error");
                    error = e != null ? e.optString("message", e.optString("code", "Request failed (" + code + ")")) : response.optString("message", response.optString("error", "Request failed (" + code + ")"));
                }
            } catch (Exception ex) {
                error = "Не удалось подключиться к основному серверу. Проверь интернет, HTTPS и адрес API.";
            } finally {
                if (connection != null) connection.disconnect();
            }
            JSONObject result = response;
            String failure = error;
            MAIN.post(() -> callback.onResult(result, failure));
        });
    }


    public static void uploadFile(android.content.Context context, android.net.Uri uri, String path, String token, Callback callback) {
        IO.execute(() -> {
            HttpURLConnection connection = null;
            JSONObject response = new JSONObject();
            String error = null;
            String boundary = "PluschatBoundary" + System.currentTimeMillis();
            try {
                connection = (HttpURLConnection) new URL(BuildConfig.API_BASE_URL + path.replaceFirst("^/", "")).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(30000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                if (token != null && !token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
                String mime = context.getContentResolver().getType(uri);
                if (mime == null) mime = "application/octet-stream";
                try (OutputStream os = connection.getOutputStream();
                     java.io.InputStream in = context.getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new java.io.IOException("Cannot read selected file");
                    os.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"avatar\"; filename=\"avatar-upload\"\r\nContent-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    byte[] buffer = new byte[8192]; int n;
                    while ((n = in.read(buffer)) != -1) os.write(buffer, 0, n);
                    os.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
                if (stream != null) {
                    StringBuilder text = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line; while ((line = reader.readLine()) != null) text.append(line);
                    }
                    if (text.length() > 0) response = new JSONObject(text.toString());
                }
                if (code < 200 || code >= 300) {
                    JSONObject e = response.optJSONObject("error");
                    error = e != null ? e.optString("message", "Upload failed (" + code + ")") : "Upload failed (" + code + ")";
                }
            } catch (Exception ex) {
                error = "Не удалось загрузить изображение. Проверь подключение и формат файла.";
            } finally {
                if (connection != null) connection.disconnect();
            }
            JSONObject result = response; String failure = error;
            MAIN.post(() -> callback.onResult(result, failure));
        });
    }

    public static JSONObject json(Object... pairs) {
        JSONObject object = new JSONObject();
        try { for (int i = 0; i + 1 < pairs.length; i += 2) object.put(String.valueOf(pairs[i]), pairs[i + 1]); }
        catch (Exception ignored) {}
        return object;
    }
}
