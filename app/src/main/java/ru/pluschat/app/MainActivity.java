package ru.pluschat.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.content.Context;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private final int bg = Color.rgb(11, 13, 18), panel = Color.rgb(19, 23, 32), white = Color.rgb(245, 247, 251), muted = Color.rgb(152, 162, 181), accent = Color.rgb(136, 117, 255);
    private LinearLayout root, body, chatList;
    private String page = "auth", emailChallenge = "", currentChatId = "", currentChatTitle = "", currentPeerId = "";
    private EditText codeField, emailField, emailCodeField, confirmPasswordField, firstNameField, lastNameField, usernameField, passwordField, bioField, birthField, educationField, identifierField, loginPasswordField, messageField, searchField;
    private String registrationEmail = "";
    private ArrayList<JSONObject> chats = new ArrayList<>();
    private boolean lightMode = false;
    private android.net.Uri selectedAvatar;
    private byte[] pendingDecryptedFile;
    private String pendingDecryptedName = "download.bin";
    private String pendingDecryptedMime = "application/octet-stream";
    private final Handler chatHandler = new Handler(Looper.getMainLooper());
    private Runnable chatPoll;
    private android.media.MediaRecorder voiceRecorder;
    private java.io.File voiceTempFile;
    private long voiceStartedAt;
    private boolean recordingVoice = false;
    private boolean appUnlockedThisForeground = false;
    private boolean lockPromptInProgress = false;
    private boolean enablingAppLock = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SessionStore.token(this).isEmpty()) {
            showPage("welcome");
        } else if (SessionStore.appLockEnabled(this)) {
            showPage("app_locked");
            authenticateAppLock();
        } else {
            appUnlockedThisForeground = true;
            showPage("home");
            registerCryptoKeys(null);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (SessionStore.token(this).isEmpty()) return;
        if (SessionStore.appLockEnabled(this) && !appUnlockedThisForeground && !lockPromptInProgress) {
            if (!"app_locked".equals(page)) showPage("app_locked");
            authenticateAppLock();
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (!lockPromptInProgress && SessionStore.appLockEnabled(this)) appUnlockedThisForeground = false;
    }

    private void authenticateAppLock() {
        if (android.os.Build.VERSION.SDK_INT < 28) {
            message("Для биометрической блокировки требуется Android 9 или новее.");
            return;
        }
        if (lockPromptInProgress) return;
        lockPromptInProgress = true;
        android.hardware.biometrics.BiometricPrompt prompt =
            new android.hardware.biometrics.BiometricPrompt.Builder(this)
                .setTitle("Плюс Чат")
                .setSubtitle("Подтверди личность, чтобы открыть переписки")
                .setDescription("Переписки скрыты до успешной проверки.")
                .setNegativeButton("Отмена", getMainExecutor(), (dialog, which) -> {
                    lockPromptInProgress = false;
                    appUnlockedThisForeground = false;
                    if (enablingAppLock) {
                        enablingAppLock = false;
                        SessionStore.setAppLockEnabled(MainActivity.this, false);
                        showPage("settings");
                    } else showPage("app_locked");
                }).build();
        prompt.authenticate(new android.os.CancellationSignal(), getMainExecutor(), new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult result) {
                lockPromptInProgress = false;
                appUnlockedThisForeground = true;
                if (enablingAppLock) {
                    SessionStore.setAppLockEnabled(MainActivity.this, true);
                    enablingAppLock = false;
                    message("Биометрическая блокировка включена.");
                }
                showPage("home");
                registerCryptoKeys(null);
            }
            @Override public void onAuthenticationFailed() {
                message("Биометрия не распознана. Попробуй ещё раз.");
            }
            @Override public void onAuthenticationError(int code, CharSequence errString) {
                lockPromptInProgress = false;
                appUnlockedThisForeground = false;
                if (enablingAppLock) {
                    enablingAppLock = false;
                    SessionStore.setAppLockEnabled(MainActivity.this, false);
                    showPage("settings");
                } else if (SessionStore.appLockEnabled(MainActivity.this)) showPage("app_locked");
            }
        });
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String value, int size, boolean bold, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(20), dp(18), dp(20), dp(24)); return l;
    }
    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }
    private EditText input(String hint, boolean secret) {
        EditText e = new EditText(this); e.setSingleLine(!hint.equals("Описание профиля (необязательно)"));
        e.setHint(hint); e.setTextColor(lightMode ? 0xff282438 : white);
        e.setHintTextColor(lightMode ? 0xff8c869b : muted); e.setTextSize(15);
        e.setPadding(dp(16), dp(8), dp(16), dp(8)); e.setBackground(shape(lightMode ? 0xfff0eef7 : panel, 16));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, secret ? dp(56) : -2); p.setMargins(0, dp(5), 0, dp(5)); e.setLayoutParams(p);
        if (secret) e.setInputType(129);
        return e;
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label); b.setTextColor(lightMode ? 0xff241b42 : 0xfff5f7fb); b.setAllCaps(false); b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setGravity(Gravity.CENTER);
        b.setPadding(dp(20), dp(12), dp(20), dp(12)); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setStateListAnimator(null); b.setElevation(0); b.setBackground(shape(lightMode ? 0xffd9d0ff : 0xff6f5bea, 16));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.height = dp(56); p.setMargins(0, dp(6), 0, dp(6)); b.setLayoutParams(p);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private Button tile(String symbol, String label, String detail, Runnable action) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        b.setText(symbol + "   " + label + "\n" + "       " + detail);
        b.setTextColor(white);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        b.setMinHeight(76);
        b.setBackground(shape(panel, 20));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.setMargins(dp(4), dp(5), dp(4), dp(5));
        b.setLayoutParams(p);
        return b;
    }
    private void addSection(String value) {
        TextView label = text(value, 17, true, lightMode ? Color.BLACK : white);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(18), 0, dp(8));
        body.addView(label, p);
    }
    private void showPage(String target) {
        if (chatPoll != null) { chatHandler.removeCallbacks(chatPoll); chatPoll = null; }
        page = target;
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(lightMode ? Color.rgb(245,245,247) : bg);
        getWindow().setStatusBarColor(lightMode ? Color.WHITE : 0xff0b0d12);
        getWindow().setNavigationBarColor(lightMode ? Color.WHITE : 0xff0b0d12);
        if (android.os.Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(lightMode ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        boolean authFlow = target.equals("welcome") || target.equals("auth") || target.equals("register") || target.equals("verify_email") || target.equals("twofa") || target.equals("twofa_setup") || target.equals("forgot_password");
        boolean conversation = target.equals("chat");
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(10), dp(20), dp(10));
        header.setBackground(shape(lightMode ? Color.WHITE : 0xff0f1219, 0));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.pluschat_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        logoParams.setMargins(0, 0, dp(12), 0);
        header.addView(logo, logoParams);
        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        TextView brand = text("Pluschat", 21, true, lightMode ? Color.BLACK : white);
        TextView tagline = text("Твои люди. Твои разговоры.", 12, false, muted);
        brandText.addView(brand);
        brandText.addView(tagline);
        header.addView(brandText, new LinearLayout.LayoutParams(0, -2, 1));
        if (!authFlow && !conversation) root.addView(header, new LinearLayout.LayoutParams(-1, dp(72)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = column();
        body.setPadding(dp(20), dp(16), dp(20), dp(24));
        if (!conversation) scroll.addView(body);
        if (conversation) root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        else root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        boolean signedIn = !SessionStore.token(this).isEmpty()
                && !target.equals("auth") && !target.equals("register") && !target.equals("verify_email")
                && !target.equals("twofa") && !target.equals("twofa_setup") && !target.equals("forgot_password");
        if (signedIn && !conversation) {
            LinearLayout nav = new LinearLayout(this);
            nav.setOrientation(LinearLayout.HORIZONTAL);
            nav.setGravity(Gravity.CENTER);
            nav.setPadding(dp(8), dp(7), dp(8), dp(7));
            nav.setBackgroundColor(lightMode ? Color.WHITE : 0xff0f1219);
            nav.addView(navItem("⌂", "Чаты", "home", target.equals("home") || target.equals("chat") || target.equals("search")));
            nav.addView(navItem("♙", "Контакты", "contacts", target.equals("contacts")));
            nav.addView(navItem("◉", "Сообщества", "communities", target.equals("communities")));
            nav.addView(navItem("☺", "Профиль", "profile", target.equals("profile") || target.equals("settings") || target.equals("permissions") || target.equals("privacy") || target.equals("appearance") || target.equals("chat_info") || target.startsWith("twofa_")));
            root.addView(nav, new LinearLayout.LayoutParams(-1, dp(72)));
        }
        setContentView(root);
        if (target.equals("welcome")) welcomeScreen();
        else if (target.equals("app_locked")) appLockedScreen();
        else if (target.equals("auth")) authScreen();
        else if (target.equals("forgot_password")) forgotPasswordScreen();
        else if (target.equals("devices")) devicesScreen();
        else if (target.equals("register")) registerScreen();
        else if (target.equals("verify_email")) verifyEmailScreen();
        else if (target.equals("complete_profile")) completeProfileScreen();
        else if (target.equals("home")) homeScreen();
        else if (target.equals("chat")) chatScreen();
        else if (target.equals("search")) searchScreen();
        else if (target.equals("contacts")) contactsScreen();
        else if (target.equals("calls")) callsScreen();
        else if (target.equals("notifications")) notificationsScreen();
        else if (target.equals("profile")) profileScreen();
        else if (target.equals("settings")) settingsScreen();
        else if (target.equals("hidden_chats")) hiddenChatsScreen();
        else if (target.equals("privacy")) privacyScreen();
        else if (target.equals("appearance")) appearanceScreen();
        else if (target.equals("chat_info")) chatInfoScreen();
        else if (target.equals("chat_search")) chatSearchScreen();
        else if (target.equals("twofa")) twoFactorScreen();
        else if (target.equals("twofa_setup")) twoFactorSetupScreen();
        else if (target.equals("permissions")) permissionsScreen();
    }
    private void appLockedScreen() {
        title("Плюс Чат заблокирован");
        subtitle("Переписки скрыты. Подтверди личность для продолжения.");
        body.addView(button("Разблокировать биометрией", this::authenticateAppLock));
        body.addView(button("Выйти из аккаунта", () -> {
            SessionStore.clear(this);
            appUnlockedThisForeground = false;
            showPage("auth");
        }));
    }

    private View navItem(String symbol, String label, String destination, boolean selected) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(4), dp(5), dp(4), dp(5));
        TextView icon = text(symbol, 24, true, selected ? 0xff8875ff : (lightMode ? 0xff938da3 : 0xff7d8799));
        icon.setGravity(Gravity.CENTER);
        TextView caption = text(label, 11, selected, selected ? 0xffb9adff : (lightMode ? 0xff938da3 : 0xff7d8799));
        caption.setGravity(Gravity.CENTER);
        item.addView(icon);
        item.addView(caption);
        item.setBackground(shape(lightMode ? (selected ? 0xfff0ecff : Color.WHITE) : (selected ? 0xff292443 : 0xff171b24), 15));
        item.setOnClickListener(v -> {
            if (destination.equals("communities")) openCommunity(false);
            else showPage(destination);
        });
        item.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1));
        return item;
    }

    private void title(String s) { TextView t=text(s, 25, true, lightMode ? Color.BLACK : white); t.setPadding(0, dp(5), 0, dp(5)); body.addView(t); }
    private void subtitle(String s) { TextView t = text(s, 14, false, muted); t.setPadding(0, dp(4), 0, dp(12)); body.addView(t); }
    private void welcomeScreen() {
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(24), dp(78), dp(24), dp(24));
        LinearLayout mark = new LinearLayout(this);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(shape(0xff171d2b, 60));
        TextView plus = text("+", 58, false, white);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(shape(0xff292443, 48));
        mark.addView(plus, new LinearLayout.LayoutParams(dp(76), dp(76)));
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(118), dp(118));
        markParams.bottomMargin = dp(26);
        body.addView(mark, markParams);
        TextView brand = text("Pluschat", 34, true, white);
        brand.setGravity(Gravity.CENTER);
        body.addView(brand, new LinearLayout.LayoutParams(-1, dp(44)));
        TextView intro = text("Общение без лишнего.\nЛюди, чаты и ваши правила.", 15, false, muted);
        intro.setGravity(Gravity.CENTER);
        intro.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(-1, dp(48));
        introParams.topMargin = dp(4);
        introParams.bottomMargin = dp(30);
        body.addView(intro, introParams);
        LinearLayout security = new LinearLayout(this);
        security.setOrientation(LinearLayout.HORIZONTAL);
        security.setGravity(Gravity.CENTER_VERTICAL);
        security.setPadding(dp(16), dp(14), dp(16), dp(14));
        security.setBackground(shape(0xff131720, 20));
        TextView star = text("✦", 22, true, 0xff77b9ff);
        star.setGravity(Gravity.CENTER);
        security.addView(star, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout securityText = new LinearLayout(this);
        securityText.setOrientation(LinearLayout.VERTICAL);
        securityText.setPadding(dp(12), 0, 0, 0);
        securityText.addView(text("Приватность — в основе", 14, true, white));
        TextView securityDetail = text("Настройки безопасности всегда под рукой", 11, false, muted);
        securityDetail.setMaxLines(2);
        securityText.addView(securityDetail);
        security.addView(securityText, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams securityParams = new LinearLayout.LayoutParams(-1, dp(84));
        securityParams.topMargin = dp(2);
        securityParams.bottomMargin = dp(44);
        body.addView(security, securityParams);
        body.addView(button("Создать аккаунт", () -> { showPage("register"); }));
        Button loginButton = button("У меня уже есть аккаунт", () -> showPage("auth"));
        loginButton.setBackground(shape(0xff1a2030, 16));
        loginButton.setTextColor(white);
        body.addView(loginButton);
        TextView terms = text("Продолжая, вы принимаете условия сервиса", 10, false, muted);
        terms.setGravity(Gravity.CENTER);
        terms.setPadding(0, dp(18), 0, dp(8));
        body.addView(terms);
    }

    private void authScreen() {
        title("Войти"); subtitle("Продолжите общение с близкими");
        TextView emailLabel = text("Электронная почта", 12, true, muted);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2); labelParams.setMargins(0, dp(28), 0, dp(2)); body.addView(emailLabel, labelParams);
        identifierField = input("name@example.com", false); body.addView(identifierField);
        TextView passwordLabel = text("Пароль", 12, true, muted);
        LinearLayout.LayoutParams passwordLabelParams = new LinearLayout.LayoutParams(-1, -2); passwordLabelParams.setMargins(0, dp(10), 0, dp(2)); body.addView(passwordLabel, passwordLabelParams);
        loginPasswordField = input("Введите пароль", true); body.addView(loginPasswordField);
        TextView forgot = text("Забыли пароль?", 12, true, 0xffb7adff); forgot.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        forgot.setPadding(0, dp(6), 0, dp(12)); forgot.setOnClickListener(v -> showPage("forgot_password")); body.addView(forgot);
        body.addView(button("Войти", this::login));
        TextView divider = text("или продолжить с", 12, false, muted); divider.setGravity(Gravity.CENTER); divider.setPadding(0, dp(16), 0, dp(8)); body.addView(divider);
        body.addView(text("После пароля потребуется одноразовый код двухэтапной защиты.", 12, false, muted));
        TextView signup = text("Нет аккаунта?  Создать", 13, true, 0xffb7adff); signup.setGravity(Gravity.CENTER); signup.setPadding(0, dp(22), 0, dp(12)); signup.setOnClickListener(v -> {  showPage("register"); }); body.addView(signup);
    }
    private void forgotPasswordScreen() {
        title("Восстановление пароля");
        subtitle("Укажи email аккаунта. Если он существует и подтверждён, мы отправим ссылку для смены пароля.");
        EditText email = input("Электронная почта", false);
        email.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        body.addView(email);
        body.addView(button("Отправить ссылку", () -> {
            String address = value(email).trim().toLowerCase(java.util.Locale.ROOT);
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
                message("Введи корректный email.");
                return;
            }
            request("POST", "auth/password/forgot", ApiClient.json("email", address), false, (data, error) -> {
                message(data.optString("message", "Если аккаунт существует, письмо будет отправлено."));
                showPage("auth");
            });
        }));
        body.addView(button("Назад ко входу", () -> showPage("auth")));
    }

    private void devicesScreen() {
        title("Активные устройства");
        subtitle("Здесь можно отозвать потерянное или больше не используемое устройство.");
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list);
        request("GET", "devices", null, true, (data, error) -> {
            JSONArray devices = data.optJSONArray("devices");
            if (devices == null || devices.length() == 0) {
                list.addView(text("Активных устройств не найдено.", 14, false, muted));
                return;
            }
            for (int i = 0; i < devices.length(); i++) {
                JSONObject device = devices.optJSONObject(i);
                if (device == null) continue;
                String id = device.optString("id", "");
                String name = device.optString("device_name", "Устройство");
                String platform = device.optString("platform", "unknown");
                String lastSeen = device.optString("last_seen_at", "");
                list.addView(text(name + " · " + platform + "\nПоследняя активность: " + lastSeen, 13, false, white));
                list.addView(button("Отозвать это устройство", () -> request("POST", "devices/" + id + "/revoke",
                        new JSONObject(), true, (result, revokeError) -> {
                    if (result.optBoolean("ok", false)) {
                        message("Доступ устройства отозван.");
                        showPage("devices");
                    }
                })));
            }
        });
        body.addView(button("Завершить остальные сессии", () -> request("POST", "auth/sessions/revoke-others",
                new JSONObject(), true, (result, error) -> message("Остальные сессии отозваны."))));
        body.addView(button("Завершить ВСЕ сеансы", () -> new android.app.AlertDialog.Builder(this)
                .setTitle("Выйти на всех устройствах?")
                .setMessage("Все активные сеансы, включая этот телефон, будут завершены. Для продолжения потребуется войти снова.")
                .setNegativeButton("Отмена", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Завершить все", (dialog, which) -> request("POST", "auth/sessions/revoke-all",
                        new JSONObject(), true, (result, error) -> {
                            if (result.optBoolean("logged_out_everywhere", false)) {
                                SessionStore.clear(this);
                                message("Все сеансы завершены.");
                                showPage("auth");
                            }
                        }))
                .show()));
        body.addView(button("Назад", () -> showPage("settings")));
    }

    private void registerScreen() {
        title("Создать аккаунт");
        subtitle("Регистрация по электронной почте. SMS и номер телефона не требуются.");
        firstNameField = input("Имя *", false); body.addView(firstNameField);
        lastNameField = input("Фамилия (необязательно)", false); body.addView(lastNameField);
        emailField = input("Электронная почта", false);
        emailField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        body.addView(emailField);
        passwordField = input("Пароль (минимум 10 символов)", true); body.addView(passwordField);
        confirmPasswordField = input("Повторите пароль", true); body.addView(confirmPasswordField);
        birthField = input("Дата рождения · необязательно (ГГГГ-ММ-ДД)", false); body.addView(birthField);
        CheckBox terms = new CheckBox(this);
        terms.setText("Я согласен(на) с условиями сервиса");
        terms.setTextColor(lightMode ? 0xff282438 : white);
        terms.setTextSize(12);
        if (android.os.Build.VERSION.SDK_INT >= 21) terms.setButtonTintList(android.content.res.ColorStateList.valueOf(0xff8875ff));
        body.addView(terms);
        body.addView(button("Создать аккаунт", () -> {
            String email = value(emailField).toLowerCase(java.util.Locale.ROOT);
            String password = value(passwordField);
            String confirm = value(confirmPasswordField);
            String firstName = value(firstNameField);
            String lastName = value(lastNameField);
            String birthDate = value(birthField);
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { message("Введи корректный email."); return; }
            if (firstName.trim().isEmpty()) { message("Имя обязательно для регистрации."); firstNameField.requestFocus(); return; }
            if (password.length() < 10 || password.length() > 200) { message("Пароль должен содержать от 10 до 200 символов."); return; }
            if (!password.equals(confirm)) { message("Пароли не совпадают."); return; }
            if (!birthDate.isEmpty() && !birthDate.matches("\\d{4}-\\d{2}-\\d{2}")) { message("Дата должна быть в формате YYYY-MM-DD."); return; }
            if (!terms.isChecked()) { message("Подтверди согласие с условиями сервиса."); return; }
            JSONObject payload = ApiClient.json("email", email, "password", password,
                "first_name", firstName, "last_name", lastName, "birth_date", birthDate);
            request("POST", "auth/register", payload, false, (data, error) -> {
                if (error != null || !data.optBoolean("ok", false)) return;
                registrationEmail = email;
                message(data.optString("message", "Код подтверждения отправлен по электронной почте."));
                showPage("verify_email");
            });
        }));
        body.addView(button("Уже есть аккаунт — войти", () -> showPage("auth")));
    }

    private void verifyEmailScreen() {
        title("Подтверди почту");
        subtitle("Мы отправили шестизначный код на " + registrationEmail + ". Проверь входящие и папку «Спам».");
        TextView emailLabel = text(registrationEmail, 15, true, lightMode ? 0xff51458b : 0xffc7beff);
        emailLabel.setPadding(0, dp(6), 0, dp(12));
        body.addView(emailLabel);
        emailCodeField = input("Код из письма · 6 цифр", false);
        emailCodeField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        emailCodeField.setLetterSpacing(0.25f);
        emailCodeField.setTextSize(22);
        emailCodeField.setGravity(Gravity.CENTER);
        body.addView(emailCodeField);
        body.addView(button("Подтвердить email", () -> {
            String code = value(emailCodeField).trim();
            if (!code.matches("[0-9]{6}")) { message("Введи шестизначный код из письма."); return; }
            request("POST", "auth/verify", ApiClient.json("email", registrationEmail, "code", code), false, (data, error) -> {
                if (error != null || !data.optBoolean("verified", false)) return;
                message("Почта подтверждена. Теперь войди в аккаунт и заверши профиль.");
                showPage("auth");
                if (identifierField != null) identifierField.setText(registrationEmail);
            });
        }));
        body.addView(button("Отправить код повторно", () -> {
            request("POST", "auth/resend-verification", ApiClient.json("email", registrationEmail), false, (data, error) -> {
                if (error == null) message(data.optString("message", "Если аккаунт ожидает подтверждения, письмо отправлено."));
            });
        }));
        body.addView(button("Назад к регистрации", () -> showPage("register")));
    }

    private void login() {
        String id=value(identifierField), pw=value(loginPasswordField);
        if(id.isEmpty()||pw.isEmpty()){message("Введи email или никнейм и пароль.");return;}
        request("POST","auth/login",ApiClient.json("login",id,"password",pw,"device_key",SessionStore.deviceKey(this),"device_name",android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL,"platform","android"),false,(data,error)->{
            if(data.optBoolean("requires_2fa",false)){ pending2fa=data.optString("challenge_id",""); showPage("twofa"); return; }
            if(data.optBoolean("requires_2fa_setup",false)) {
                if(saveSession(data)) showPage("twofa_setup");
                return;
            }
            if(saveSession(data)) continueAfterLogin(data);
        });
    }
    private String pending2fa="";

    private void continueAfterLogin(JSONObject data) {
        JSONObject user = data.optJSONObject("user");
        if (user != null && !user.optBoolean("profile_completed", true)) {
            showPage("complete_profile");
            return;
        }
        registerCryptoKeys(() -> showPage("home"));
    }

    private void completeProfileScreen() {
        title("Завершение профиля");
        subtitle("Почта подтверждена. Имя и уникальный никнейм обязательны для продолжения.");
        EditText firstName = input("Имя *", false);
        EditText username = input("Никнейм · латиница, цифры и _", false);
        EditText lastName = input("Фамилия (необязательно)", false);
        EditText birth = input("Дата рождения · необязательно (ГГГГ-ММ-ДД)", false);
        body.addView(firstName);
        body.addView(username);
        body.addView(lastName);
        body.addView(birth);
        body.addView(button("Сохранить и продолжить", () -> {
            String nick = value(username).replaceFirst("^@", "");
            String name = value(firstName);
            String birthday = value(birth);
            if (name.isEmpty()) { message("Имя обязательно."); return; }
            if (!nick.matches("[A-Za-z0-9_]{3,32}")) { message("Никнейм должен содержать 3–32 латинских символа, цифры или _."); return; }
            if (!birthday.isEmpty() && !birthday.matches("\\d{4}-\\d{2}-\\d{2}")) { message("Дата должна быть в формате YYYY-MM-DD."); return; }
            request("POST", "auth/profile", ApiClient.json("username", nick, "first_name", name, "last_name", value(lastName), "birth_date", birthday), true, (result, error) -> {
                if (error != null || !result.optBoolean("ok", false)) return;
                registerCryptoKeys(() -> showPage("home"));
            });
        }));
        body.addView(button("Выйти", this::logout));
    }

    private void twoFactorScreen() {
        title("Дополнительная защита"); subtitle("Введи одноразовый код второго фактора.");
        EditText code=input("Код 2FA",false); body.addView(code);
        body.addView(button("Подтвердить и войти",()->request("POST","auth/2fa/verify",ApiClient.json("challenge_id",pending2fa,"code",value(code)),false,(data,error)->{if(saveSession(data))continueAfterLogin(data);})));
    }
    private boolean saveSession(JSONObject data) {
        String access=data.optString("access_token", data.optString("token","")), refresh=data.optString("refresh_token","");
        if(access.isEmpty()){message("Сервер не вернул токен сессии. Проверь контракт API.");return false;}
        try { SessionStore.save(this,access,refresh); return true; }
        catch(Exception e){message("Не удалось безопасно сохранить сессию.");return false;}
    }

    private void twoFactorSetupScreen() {
        title("Двухэтапная аутентификация");
        subtitle("Защита паролем и одноразовым кодом из приложения-аутентификатора.");
        TextView secretView=text("Нажми «Настроить 2FA», чтобы получить секрет для приложения-аутентификатора.",14,false,muted);
        body.addView(secretView);
        EditText code=input("Одноразовый код из приложения",false); body.addView(code);
        final String[] setupId={""};
        body.addView(button("Настроить 2FA",()->request("POST","auth/2fa/setup",new JSONObject(),true,(data,error)->{
            setupId[0]=data.optString("setup_id","");
            String secret=data.optString("secret","");
            String url=data.optString("otpauth_url","");
            secretView.setText("Добавь аккаунт в приложение-аутентификатор. Секрет: "+(secret.isEmpty()?"см. инструкцию сервера":secret)+(url.isEmpty()?"":"\n"+url));
        })));
        body.addView(button("Подтвердить и включить",()->{
            if(setupId[0].isEmpty()){message("Сначала нажми «Настроить 2FA».");return;}
            request("POST","auth/2fa/confirm",ApiClient.json("setup_id",setupId[0],"code",value(code)),true,(data,error)->{
                if (!data.optBoolean("ok", false)) { message("Не удалось включить 2FA. Проверь код и повтори."); return; }
                if (!saveSession(data)) return;
                message("Двухэтапная аутентификация включена.");
                registerCryptoKeys(() -> showPage("home"));
            });
        }));
        body.addView(text("Двухэтапная аутентификация необязательна и включается по желанию.", 12, false, muted));
        body.addView(button("Назад",()->showPage("settings")));
    }

    private void homeScreen() {
        body.setPadding(dp(18), dp(18), dp(18), dp(22));
        LinearLayout titleRow = row();
        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);
        titleStack.addView(text("Чаты", 29, true, white));
        titleStack.addView(text("Твои разговоры в одном месте", 12, false, muted));
        titleRow.addView(titleStack, new LinearLayout.LayoutParams(0, -2, 1));
        TextView searchButton = text("⌕", 24, false, Color.WHITE);
        searchButton.setGravity(Gravity.CENTER);
        searchButton.setBackground(shape(0xff171b24, 15));
        searchButton.setOnClickListener(v -> showPage("search"));
        LinearLayout.LayoutParams searchButtonParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        searchButtonParams.setMargins(0, 0, dp(8), 0);
        titleRow.addView(searchButton, searchButtonParams);
        TextView newChat = text("+", 26, false, Color.WHITE);
        newChat.setGravity(Gravity.CENTER);
        newChat.setBackground(shape(0xff6f5bea, 15));
        titleRow.addView(newChat, new LinearLayout.LayoutParams(dp(38), dp(38)));
        newChat.setOnClickListener(v -> showPage("search"));
        body.addView(titleRow);
        LinearLayout searchRow = row();
        searchRow.setPadding(dp(14), 0, dp(12), 0);
        searchRow.setBackground(shape(0xff171b24, 15));
        TextView searchIcon = text("⌕", 24, false, 0xffa9b2c4);
        searchRow.addView(searchIcon);
        searchField = new EditText(this);
        searchField.setSingleLine(true);
        searchField.setTextSize(14);
        searchField.setHint("Поиск сообщений и людей");
        searchField.setHintTextColor(0xff8994a8);
        searchField.setTextColor(0xfff5f7fb);
        searchField.setBackgroundColor(Color.TRANSPARENT);
        searchField.setPadding(dp(10), 0, 0, 0);
        searchRow.addView(searchField, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView go = text("→", 22, true, 0xffb9adff);
        go.setGravity(Gravity.CENTER);
        searchRow.addView(go, new LinearLayout.LayoutParams(dp(34), dp(52)));
        searchRow.setOnClickListener(v -> showPage("search"));
        go.setOnClickListener(v -> showPage("search"));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(52));
        searchParams.setMargins(0, dp(18), 0, dp(16));
        body.addView(searchRow, searchParams);

        LinearLayout filters = row();
        filters.setPadding(0, 0, 0, dp(12));
        filters.addView(filterChip("Все", true), new LinearLayout.LayoutParams(0, dp(38), 1));
        filters.addView(filterChip("Личные", false), new LinearLayout.LayoutParams(0, dp(38), 1));
        filters.addView(filterChip("Группы", false), new LinearLayout.LayoutParams(0, dp(38), 1));
        body.addView(filters);

        LinearLayout sectionRow = row();
        sectionRow.addView(text("Недавние", 18, true, white), new LinearLayout.LayoutParams(0, -2, 1));
        TextView refresh = text("Обновить ↻", 12, true, 0xffb9adff);
        refresh.setPadding(dp(8), dp(8), 0, dp(8));
        refresh.setOnClickListener(v -> loadChats());
        sectionRow.addView(refresh);
        body.addView(sectionRow);

        chatList = new LinearLayout(this);
        chatList.setOrientation(LinearLayout.VERTICAL);
        body.addView(chatList, new LinearLayout.LayoutParams(-1, -2));

        
        loadChats();
    }

    private View filterChip(String label, boolean selected) {
        TextView chip = text(label, 12, true, selected ? 0xffe2dcff : 0xffa1aabd);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(shape(selected ? 0xff292443 : 0xff171b24, 19));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -1);
        p.setMargins(dp(3), 0, dp(3), 0);
        chip.setLayoutParams(p);
        chip.setOnClickListener(v -> {
            if ("Личные".equals(label)) showPage("contacts");
            else if ("Группы".equals(label)) openCommunity(false);
            else loadChats();
        });
        return chip;
    }
    private View homeShortcut(String symbol, String title, String detail, int surface, int ink, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(10), dp(16), dp(10), dp(16));
        card.setBackground(shape(surface, 19));
        TextView glyph = text(symbol, 28, true, ink);
        glyph.setGravity(Gravity.CENTER);
        card.addView(glyph, new LinearLayout.LayoutParams(-1, 31));
        TextView name = text(title, 14, true, 0xff2d293b);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(7), 0, 0);
        card.addView(name);
        TextView caption = text(detail, 12, false, 0xff777186);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(0, dp(2), 0, 0);
        card.addView(caption);
        card.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.setMargins(dp(4), 0, dp(4), 0);
        card.setLayoutParams(p);
        return card;
    }
    private void loadChats() {
        if (chatList == null || !"home".equals(page)) return;
        chatList.removeAllViews();
        TextView loading = text("Загружаем переписки…", 14, false, 0xff89839a);
        loading.setPadding(dp(4), dp(18), dp(4), dp(18));
        chatList.addView(loading);
        request("GET", "conversations", null, true, (data, error) -> {
            if (!"home".equals(page) || chatList == null) return;
            chats.clear();
            JSONArray arr = data.optJSONArray("conversations");
            if (arr == null) arr = data.optJSONArray("data");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject chat = arr.optJSONObject(i);
                if (chat != null && !SessionStore.isChatHidden(this, chat.optString("id", chat.optString("chat_id", "")))) chats.add(chat);
            }
            chatList.removeAllViews();
            if (chats.isEmpty()) {
                LinearLayout empty = new LinearLayout(this);
                empty.setOrientation(LinearLayout.VERTICAL);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(18), dp(25), dp(18), dp(25));
                empty.setBackground(shape(0xff151925, 22));
                TextView icon = text("✉", 34, true, 0xffb9adff);
                icon.setGravity(Gravity.CENTER);
                empty.addView(icon);
                TextView label = text("Здесь будет твоя история", 17, true, 0xfff5f7fb);
                label.setGravity(Gravity.CENTER);
                label.setPadding(0, dp(8), 0, dp(4));
                empty.addView(label);
                TextView hint = text("Найди человека и начни первый разговор.", 13, false, 0xff98a2b5);
                hint.setGravity(Gravity.CENTER);
                empty.addView(hint);
                empty.setOnClickListener(v -> showPage("search"));
                chatList.addView(empty);
            } else {
                for (JSONObject chat : chats) {
                    String id = chat.optString("id", chat.optString("chat_id", ""));
                    JSONObject peer = chat.optJSONObject("other_user");
                    String name = chat.optString("title", chat.optString("name", peer == null ? "Чат" : (peer.optString("first_name","")+" "+peer.optString("last_name","")).trim()));
                    if (name.isEmpty() || name.equals("Чат")) name = peer == null ? "Чат" : "@"+peer.optString("username","user");
                    String preview = chat.optString("last_message", "Открыть переписку");
                    LinearLayout item = row();
                    item.setPadding(dp(12), dp(13), dp(12), dp(13));
                    item.setBackground(shape(0xff131720, 19));
                    LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, -2);
                    itemParams.setMargins(0, dp(4), 0, dp(4));
                    TextView avatar = text(name.isEmpty() ? "П" : name.substring(0, 1).toUpperCase(), 19, true, 0xffd7d0ff);
                    avatar.setGravity(Gravity.CENTER);
                    avatar.setBackground(shape(0xff292443, 30));
                    item.addView(avatar, new LinearLayout.LayoutParams(dp(52), dp(52)));
                    LinearLayout details = new LinearLayout(this);
                    details.setOrientation(LinearLayout.VERTICAL);
                    details.setPadding(dp(13), 0, 0, 0);
                    details.addView(text(name, 15, true, 0xfff5f7fb));
                    TextView previewText = text(preview, 12, false, 0xff98a2b5);
                    previewText.setMaxLines(1);
                    previewText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    details.addView(previewText);
                    item.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
                    final String chatDisplayName = name;
                    TextView arrow = text("›", 26, false, 0xff7d8799);
                    item.addView(arrow);
                    item.setOnClickListener(v -> {
                        currentChatId = id;
                        currentChatTitle = chatDisplayName;
                        currentPeerId = peer == null ? "" : peer.optString("id", "");
                        showPage("chat");
                    });
                    chatList.addView(item, itemParams);
                }
            }
        });
    }

    private void chatScreen() {
        body.setPadding(0, 0, 0, 0);
        LinearLayout chatHeader = row();
        chatHeader.setPadding(dp(14), dp(10), dp(14), dp(10));
        chatHeader.setBackgroundColor(0xff0f1219);
        TextView back = text("‹", 32, false, white);
        back.setGravity(Gravity.CENTER);
        chatHeader.addView(back, new LinearLayout.LayoutParams(dp(34), dp(48)));
        back.setOnClickListener(v -> showPage("home"));

        TextView avatar = text(currentChatTitle.isEmpty() ? "П" : currentChatTitle.substring(0, 1).toUpperCase(), 17, true, 0xffd7d0ff);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(shape(0xff292443, 28));
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        avatarParams.setMargins(dp(8), 0, dp(10), 0);
        chatHeader.addView(avatar, avatarParams);

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.addView(text(currentChatTitle.isEmpty() ? "Переписка" : currentChatTitle, 15, true, white));
        identity.addView(text("Личная переписка", 11, false, muted));
        identity.setOnClickListener(v -> showPage("chat_info"));
        chatHeader.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));

        TextView call = text("☎", 21, true, 0xffb9adff);
        call.setGravity(Gravity.CENTER);
        chatHeader.addView(call, new LinearLayout.LayoutParams(dp(42), dp(46)));
        call.setOnClickListener(v -> message("Голосовые и видеозвонки ещё не подключены."));
        TextView more = text("⋯", 24, true, white);
        more.setGravity(Gravity.CENTER);
        chatHeader.addView(more, new LinearLayout.LayoutParams(dp(34), dp(46)));
        more.setOnClickListener(v -> showPage("chat_info"));
        body.addView(chatHeader, new LinearLayout.LayoutParams(-1, dp(68)));

        ScrollView historyScroll = new ScrollView(this);
        historyScroll.setFillViewport(true);
        LinearLayout history = new LinearLayout(this);
        history.setOrientation(LinearLayout.VERTICAL);
        history.setPadding(dp(16), dp(14), dp(16), dp(14));
        historyScroll.addView(history);
        body.addView(historyScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composer = row();
        composer.setPadding(dp(12), dp(8), dp(12), dp(8));
        composer.setBackgroundColor(0xff0f1219);
        TextView attach = text("+", 26, false, 0xffb9adff);
        attach.setGravity(Gravity.CENTER);
        composer.addView(attach, new LinearLayout.LayoutParams(dp(42), dp(48)));
        attach.setOnClickListener(v -> pickAttachment());
        TextView voice = text("🎙", 20, true, 0xffb9adff);
        voice.setGravity(Gravity.CENTER);
        composer.addView(voice, new LinearLayout.LayoutParams(dp(42), dp(48)));
        voice.setOnClickListener(v -> {
            if (recordingVoice) stopVoiceRecording();
            else startVoiceRecording(voice);
        });
        TextView stickers = text("☺", 23, true, 0xffb9adff);
        stickers.setGravity(Gravity.CENTER);
        composer.addView(stickers, new LinearLayout.LayoutParams(dp(38), dp(48)));
        stickers.setOnClickListener(v -> showStickerPicker());
        messageField = input("Сообщение…", false);
        messageField.setSingleLine(true);
        messageField.setText(SessionStore.cryptoGet(this, "draft_" + currentChatId));
        messageField.setBackground(shape(0xff171b24, 22));
        messageField.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                SessionStore.cryptoPut(MainActivity.this, "draft_" + currentChatId, value.toString());
            }
            @Override public void afterTextChanged(android.text.Editable value) {}
        });
        messageField.setPadding(dp(15), dp(8), dp(15), dp(8));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        inputParams.setMargins(dp(4), 0, dp(8), 0);
        composer.addView(messageField, inputParams);
        TextView send = text("➤", 20, true, Color.WHITE);
        send.setGravity(Gravity.CENTER);
        send.setBackground(shape(0xff6f5bea, 24));
        composer.addView(send, new LinearLayout.LayoutParams(dp(48), dp(48)));
        send.setOnClickListener(v -> {
            String msg = value(messageField);
            if (msg.isEmpty()) return;
            if (currentChatId.isEmpty() || currentPeerId.isEmpty()) {
                message("Не удалось определить собеседника. Открой переписку заново.");
                return;
            }
            sendEncryptedMessage(msg, history);
        });
        body.addView(composer, new LinearLayout.LayoutParams(-1, -2));

        refreshChatHistory(history);
        chatPoll = new Runnable() {
            @Override public void run() {
                if (!"chat".equals(page)) return;
                refreshChatHistory(history);
                chatHandler.postDelayed(this, 5000);
            }
        };
        chatHandler.postDelayed(chatPoll, 5000);
    }
    private void refreshChatHistory(LinearLayout history) {
        if (currentChatId.isEmpty() || currentPeerId.isEmpty()) {
            history.removeAllViews();
            history.addView(text("Открой переписку из списка чатов, чтобы загрузить ключи шифрования.", 14, false, muted));
            return;
        }
        fetchPeerKeys(currentPeerId, (keys, keyError) -> {
            if (!"chat".equals(page)) return;
            if (keyError != null) {
                history.removeAllViews();
                history.addView(text("Не удалось получить ключи собеседника: " + keyError, 14, false, muted));
                return;
            }
            request("GET","conversations/"+currentChatId+"/messages?limit=50",null,true,(data,error)->{
                if (!"chat".equals(page)) return;
                JSONArray arr=data.optJSONArray("messages");
                if(arr==null)arr=data.optJSONArray("data");
                history.removeAllViews();
                if(arr==null || arr.length()==0) {
                    TextView empty=text("Пока нет сообщений.\nНапиши первым 👋",15,false,muted);
                    empty.setGravity(Gravity.CENTER);
                    empty.setPadding(dp(18), dp(30), dp(18), dp(30));
                    history.addView(empty);
                    return;
                }
                String ownHash = CryptoManager.deviceHash(this);
                for(int i=0;i<arr.length();i++) {
                    JSONObject m=arr.optJSONObject(i);
                    if(m==null) continue;
                    boolean mine = false;
                    String displayedBody;
                    JSONObject remoteKeyForMessage = null;
                    try {
                        JSONObject envelope = new JSONObject(m.optString("body", ""));
                        String[] pair = envelope.optString("key_id").split(":", -1);
                        if (pair.length != 2) throw new IllegalArgumentException("Неверный идентификатор ключа");
                        String ownShort = ownHash.substring(0, 32);
                        mine = pair[0].equals(ownShort);
                        String peerShort = mine ? pair[1] : pair[0];
                        JSONObject peerKey = findPeerKey(keys, peerShort);
                        if (peerKey == null) throw new IllegalArgumentException("Ключ устройства собеседника не найден");
                        if (!CryptoManager.verifyAndPinPeerKey(this, currentPeerId, peerKey)) {
                            throw new IllegalArgumentException("Подпись ключа собеседника недействительна");
                        }
                        remoteKeyForMessage = peerKey;
                        displayedBody = CryptoManager.decrypt(this, currentChatId, envelope.toString(), peerKey.optString("key_id"), peerKey.optString("signed_prekey"));
                    } catch (Exception cryptoError) {
                        displayedBody = "🔒 Не удалось расшифровать это сообщение на этом устройстве.";
                    }
                    JSONObject fileMetadata = null;
                    JSONObject gifMetadata = null;
                    try {
                        JSONObject candidate = new JSONObject(displayedBody);
                        if ("file".equals(candidate.optString("type")) || "voice".equals(candidate.optString("type"))) fileMetadata = candidate;
                        else if ("gif".equals(candidate.optString("type"))) gifMetadata = candidate;
                        else if ("attachment_pending".equals(candidate.optString("type"))) displayedBody = "📎 Вложение отправляется…";
                    } catch (Exception ignored) {}
                    final JSONObject attachmentMeta = fileMetadata;
                    final JSONObject selectedGif = gifMetadata;
                    final JSONObject attachmentPeerKey = remoteKeyForMessage;
                    LinearLayout bubble=new LinearLayout(this);
                    bubble.setOrientation(LinearLayout.VERTICAL);
                    bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
                    bubble.setBackground(shape(mine ? 0xff5142a6 : 0xff171b24, 18));
                    TextView sender=text(mine ? "Вы" : currentChatTitle,12,true,mine ? 0xffe5dcff : 0xffb9adff);
                    String contentText = displayedBody;
                    if (attachmentMeta != null) {
                        if ("voice".equals(attachmentMeta.optString("type"))) {
                            long seconds = Math.max(1, attachmentMeta.optLong("duration_ms", 0) / 1000);
                            contentText = "▶ Голосовое сообщение · " + seconds + " сек.\\nНажми, чтобы прослушать";
                        } else {
                            long bytes = attachmentMeta.optLong("byte_size", 0);
                            contentText = "📎 " + attachmentMeta.optString("name", "Зашифрованный файл")
                                    + "\\n" + (bytes >= 1024 * 1024 ? String.format(java.util.Locale.ROOT, "%.1f МБ", bytes / 1048576.0)
                                    : Math.max(1, bytes / 1024) + " КБ") + "\\nНажми, чтобы скачать и расшифровать";
                        }
                    }
                    TextView content=text(contentText,15,false,white);
                    content.setMaxWidth((int)(getResources().getDisplayMetrics().widthPixels * 0.72f));
                    if (selectedGif != null && isGiphyUrl(selectedGif.optString("url"))) {
                        android.webkit.WebView gifView = new android.webkit.WebView(this);
                        gifView.getSettings().setJavaScriptEnabled(false);
                        gifView.getSettings().setAllowFileAccess(false);
                        gifView.getSettings().setAllowContentAccess(false);
                        gifView.setBackgroundColor(Color.TRANSPARENT);
                        gifView.setOverScrollMode(View.OVER_SCROLL_NEVER);
                        String gifUrl = selectedGif.optString("url").replace("&", "&amp;").replace("\"", "&quot;");
                        gifView.loadDataWithBaseURL("https://media.giphy.com/", "<html><body style='margin:0;background:transparent'><img style='max-width:100%;height:auto' src=\"" + gifUrl + "\"></body></html>", "text/html", "UTF-8", null);
                        bubble.addView(sender);
                        bubble.addView(gifView, new LinearLayout.LayoutParams(dp(220), dp(170)));
                    } else {
                        bubble.addView(sender);
                        bubble.addView(content);
                    }
                    if (attachmentMeta != null && attachmentPeerKey != null) {
                        final String downloadConversationId = currentChatId;
                        if ("voice".equals(attachmentMeta.optString("type"))) {
                            content.setOnClickListener(v -> playEncryptedVoice(downloadConversationId,
                                    attachmentMeta.optString("attachment_id"),
                                    attachmentPeerKey.optString("key_id"), attachmentPeerKey.optString("signed_prekey")));
                        } else {
                            content.setOnClickListener(v -> downloadEncryptedAttachment(downloadConversationId,
                                    attachmentMeta.optString("attachment_id"), attachmentMeta.optString("name", "download.bin"),
                                    attachmentMeta.optString("mime_type", "application/octet-stream"),
                                    attachmentPeerKey.optString("key_id"), attachmentPeerKey.optString("signed_prekey")));
                        }
                    }
                    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);
                    params.setMargins(0,dp(4),0,dp(4));
                    params.gravity=mine ? Gravity.RIGHT : Gravity.LEFT;
                    history.addView(bubble,params);
                }
            });
        });
    }

    private interface PeerKeysCallback { void onResult(JSONArray keys, String error); }

    private void fetchPeerKeys(String peerId, PeerKeysCallback callback) {
        ApiClient.request("GET", "security/keys/" + android.net.Uri.encode(peerId), null, SessionStore.token(this),
            (data, error) -> {
                JSONArray keys = data.optJSONArray("keys");
                if (error != null || keys == null || keys.length() == 0) {
                    callback.onResult(keys, error != null ? error : "У собеседника ещё не зарегистрирован ключ шифрования.");
                } else callback.onResult(keys, null);
            });
    }

    private JSONObject findPeerKey(JSONArray keys, String shortId) {
        for (int i = 0; i < keys.length(); i++) {
            JSONObject key = keys.optJSONObject(i);
            if (key != null && key.optString("key_id").startsWith(shortId)) return key;
        }
        return null;
    }

    private void registerCryptoKeys(Runnable onSuccess) {
        try {
            JSONObject payload = CryptoManager.ensureLocalKeys(this).toJson();
            ApiClient.request("POST", "security/keys", payload, SessionStore.token(this), (data, error) -> {
                if (error != null || !data.optBoolean("ok", false)) {
                    message("Не удалось подключить ключи шифрования. " + (error == null ? data.optString("error", "") : error));
                    return;
                }
                if (onSuccess != null) onSuccess.run();
            });
        } catch (Exception e) {
            message("Не удалось создать ключи шифрования на устройстве.");
        }
    }

    private void sendEncryptedMessage(String plaintext, LinearLayout history) {
        registerCryptoKeys(() -> fetchPeerKeys(currentPeerId, (keys, error) -> {
            if (error != null) { message(error); return; }
            try {
                JSONObject peerKey = keys.optJSONObject(0);
                if (peerKey == null || !CryptoManager.verifyAndPinPeerKey(this, currentPeerId, peerKey)) {
                    message("Подпись ключа собеседника не прошла проверку.");
                    return;
                }
                JSONObject envelope = CryptoManager.encrypt(this, currentChatId, plaintext, peerKey.optString("key_id"), peerKey.optString("signed_prekey"));
                String clientId = java.util.UUID.randomUUID().toString();
                JSONObject payload = ApiClient.json("client_message_id", clientId, "type", "text", "body", envelope.toString());
                ApiClient.request("POST", "conversations/" + currentChatId + "/messages", payload, SessionStore.token(this), (data, sendError) -> {
                    if (sendError != null || !data.optBoolean("ok", false)) {
                        message("Сообщение не отправлено: " + (sendError == null ? data.optString("error", "ошибка API") : sendError));
                        return;
                    }
                    messageField.setText("");
                    SessionStore.cryptoRemove(MainActivity.this, "draft_" + currentChatId);
                    refreshChatHistory(history);
                });
            } catch (Exception cryptoError) {
                message("Не удалось зашифровать сообщение. Обнови ключи и попробуй снова.");
            }
        }));
    }
    private void searchScreen() {
        title("Найти людей");subtitle("Ищи по username, имени или фамилии.");
        EditText q=input("@username или имя",false);body.addView(q);
        LinearLayout results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);body.addView(results);
        body.addView(button("Поиск",()->{
            results.removeAllViews();String query=value(q);
            if(query.trim().length()<2){results.addView(text("Введите минимум 2 символа для поиска.",14,false,muted));return;}
            request("GET","search?q="+android.net.Uri.encode(query.trim()),null,true,(data,error)->{
                JSONArray arr=data.optJSONArray("users");if(arr==null)arr=data.optJSONArray("data");
                if(arr==null||arr.length()==0){results.addView(text("Пользователи не найдены. Проверь запрос или попробуй username.",14,false,muted));return;}
                for(int i=0;i<arr.length();i++){JSONObject u=arr.optJSONObject(i);if(u==null)continue;
                    String uid=u.optString("id"), name=u.optString("first_name","")+" "+u.optString("last_name",""), uname=u.optString("username","");
                    results.addView(button(name+"  @"+uname,()->request("POST","conversations",ApiClient.json("user_id",uid),true,(chatData,apiError)->{currentChatId=chatData.optString("conversation_id",chatData.optString("id",chatData.optString("chat_id","")));currentChatTitle=name;currentPeerId=uid;showPage("chat");})));
                }
            });
        }));
        body.addView(button("Назад",()->showPage("home")));
    }

    private void callsScreen() {
        title("Звонки");
        subtitle("Аудио- и видеозвонки Pluschat");
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(22), dp(28), dp(22), dp(24));
        card.setBackground(shape(0xff131720, 22));
        TextView callIcon = text("◉", 38, true, 0xff8875ff);
        callIcon.setGravity(Gravity.CENTER);
        card.addView(callIcon, new LinearLayout.LayoutParams(-1, dp(58)));
        TextView emptyTitle = text("Здесь будут ваши звонки", 17, true, white);
        emptyTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams emptyTitleParams = new LinearLayout.LayoutParams(-1, -2);
        emptyTitleParams.topMargin = dp(12);
        card.addView(emptyTitle, emptyTitleParams);
        TextView emptyDetail = text("Выберите контакт, чтобы начать разговор.", 13, false, muted);
        emptyDetail.setGravity(Gravity.CENTER);
        emptyDetail.setPadding(0, dp(8), 0, dp(18));
        card.addView(emptyDetail);
        card.addView(button("Найти контакт", () -> showPage("contacts")));
        body.addView(card);
    }

    private void contactsScreen() {
        title("Контакты"); subtitle("Люди из твоих существующих переписок.");
        LinearLayout results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);body.addView(results);
        request("GET","conversations",null,true,(data,error)->{
            JSONArray arr=data.optJSONArray("conversations");if(arr==null)arr=data.optJSONArray("data");
            if(arr==null||arr.length()==0){results.addView(text("Контактов пока нет. Найди пользователя по username.",14,false,muted));return;}
            for(int i=0;i<arr.length();i++){JSONObject conversation=arr.optJSONObject(i);if(conversation==null)continue;
                JSONObject peer=conversation.optJSONObject("other_user");
                if(peer==null)continue;
                String id=peer.optString("id","");
                String name=(peer.optString("first_name","")+" "+peer.optString("last_name","")).trim();
                if(name.isEmpty())name="@"+peer.optString("username","user");
                final String contactDisplayName = name;
                final String contactConversationId = conversation.optString("id","");
                String title=contactDisplayName+"  @"+peer.optString("username","");
                results.addView(button(title,()->{currentChatId=contactConversationId;currentChatTitle=contactDisplayName;currentPeerId=id;showPage("chat");}));
            }
        });
        body.addView(button("Найти пользователя",()->showPage("search")));
        body.addView(button("Назад",()->showPage("home")));
    }
    private void notificationsScreen() {
        title("Уведомления"); subtitle("Новые сообщения, упоминания и события сообществ.");
        LinearLayout results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);body.addView(results);
        request("GET","notifications",null,true,(data,error)->{
            JSONArray arr=data.optJSONArray("notifications");if(arr==null)arr=data.optJSONArray("data");
            if(arr==null||arr.length()==0){results.addView(text("Новых уведомлений нет.",14,false,muted));return;}
            for(int i=0;i<arr.length();i++){JSONObject n=arr.optJSONObject(i);if(n==null)continue;
                String id=n.optString("id","");String title=n.optString("title",n.optString("body","Уведомление"));
                results.addView(button(title,()->request("POST","notifications/"+id+"/read",new JSONObject(),true,(notificationData,apiError)->message("Отмечено как прочитанное."))));
            }
            body.addView(button("Отметить все прочитанным",()->request("POST","notifications/read-all",new JSONObject(),true,(notificationData,apiError)->message("Готово."))));
        });
        body.addView(button("Назад",()->showPage("home")));
    }

    private void profileScreen() {
        title("Мой профиль");
        subtitle("Имя, фамилия и дата рождения сохраняются в основном профиле.");
        EditText name=input("Имя",false);
        EditText surname=input("Фамилия",false);
        EditText birth=input("Дата рождения YYYY-MM-DD",false);
        body.addView(button("Выбрать аватарку", this::pickAvatar));
        if (selectedAvatar != null) subtitle("Выбрано изображение для аватарки.");
        body.addView(button("Загрузить выбранную аватарку", () -> {
            if (selectedAvatar == null) { message("Сначала выбери изображение."); return; }
            ApiClient.uploadFile(this, selectedAvatar, "me/avatar", SessionStore.token(this), (data,error) -> {
                if(error!=null) message(error);
                else message("Сервер принял запрос на обновление аватара.");
            });
        }));
        body.addView(name);
        body.addView(surname);
        body.addView(birth);
        request("GET","auth/me",null,true,(data,error)->{
            JSONObject u=data.optJSONObject("user");
            if(u==null)u=data;
            name.setText(u.optString("first_name",""));
            surname.setText(u.optString("last_name",""));
            birth.setText(u.optString("birth_date",""));
        });
        body.addView(button("Сохранить профиль",()->{
            String birthDate=value(birth);
            if(!birthDate.isEmpty() && !birthDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                message("Дата должна быть в формате YYYY-MM-DD.");
                return;
            }
            request("POST","auth/profile",ApiClient.json(
                "first_name",value(name),
                "last_name",value(surname),
                "birth_date",birthDate
            ),true,(data,error)->message("Профиль сохранён."));
        }));
        body.addView(button("Назад",()->showPage("home")));
    }
    private void settingsScreen() {
        title("Настройки");
        subtitle("Аккаунт · безопасность · приватность · оформление");
        LinearLayout account = row();
        account.setPadding(dp(14), dp(14), dp(14), dp(14));
        account.setBackground(shape(0xff171b24, 18));
        TextView avatar = text("К", 23, true, 0xffe2dcff);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(shape(0xff292443, 40));
        account.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, 0, 0);
        info.addView(text("Мой профиль", 15, true, white));
        info.addView(text("@username · Управление аккаунтом", 11, false, muted));
        account.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        account.setOnClickListener(v -> showPage("profile"));
        body.addView(account);
        body.addView(button("Внешний вид  ›", () -> showPage("appearance")));
        body.addView(button("Скрытые чаты", () -> showPage("hidden_chats")));
        body.addView(button("Приватность и безопасность  ›", () -> showPage("privacy")));
        body.addView(button("Уведомления", () -> showPage("notifications")));
        body.addView(text("Двухэтапная аутентификация обязательна. Изменить или отключить её без повторной проверки личности нельзя.", 12, false, muted));
        Switch biometricLock = new Switch(this);
        biometricLock.setText("Блокировка приложения биометрией");
        biometricLock.setTextColor(white);
        biometricLock.setChecked(SessionStore.appLockEnabled(this));
        biometricLock.setPadding(0, dp(12), 0, dp(12));
        biometricLock.setOnCheckedChangeListener((view, enabled) -> {
            if (enabled) {
                if (android.os.Build.VERSION.SDK_INT < 28) {
                    biometricLock.setChecked(false);
                    message("Для биометрической блокировки требуется Android 9 или новее.");
                    return;
                }
                enablingAppLock = true;
                authenticateAppLock();
            } else {
                SessionStore.setAppLockEnabled(this, false);
                appUnlockedThisForeground = true;
                message("Биометрическая блокировка отключена.");
            }
        });
        body.addView(biometricLock);
        body.addView(button("Активные устройства", () -> showPage("devices")));
        body.addView(button("Разрешения Android", () -> showPage("permissions")));
        body.addView(button("Выйти из аккаунта", this::logout));
        body.addView(button("Назад", () -> showPage("home")));
    }

    private void hiddenChatsScreen() {
        title("Скрытые чаты");
        subtitle("Скрытые только на этом устройстве; история не удаляется.");
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list);
        request("GET", "conversations", null, true, (data, error) -> {
            if (error != null) { message("Не удалось загрузить список чатов."); return; }
            JSONArray arr = data.optJSONArray("conversations");
            if (arr == null) arr = data.optJSONArray("data");
            boolean found = false;
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject chat = arr.optJSONObject(i);
                if (chat == null) continue;
                String id = chat.optString("id", chat.optString("chat_id", ""));
                if (id.isEmpty() || !SessionStore.isChatHidden(this, id)) continue;
                found = true;
                JSONObject peer = chat.optJSONObject("other_user");
                String name = chat.optString("title", chat.optString("name", peer == null ? "Чат " + id : peer.optString("first_name", "Чат")));
                list.addView(button(name + " — вернуть в список", () -> {
                    try {
                        SessionStore.setChatHidden(this, id, false);
                        message("Чат снова отображается.");
                        showPage("hidden_chats");
                    } catch (Exception e) { message("Не удалось изменить настройку."); }
                }));
            }
            if (!found) list.addView(text("Нет скрытых чатов.", 14, false, muted));
        });
        body.addView(button("Назад к настройкам", () -> showPage("settings")));
    }

    private void appearanceScreen() {
        title("Внешний вид");
        subtitle("Подстрой Pluschat под себя.");
        body.addView(text("ТЕМА", 11, true, muted));
        body.addView(button(lightMode ? "Включить тёмную тему" : "Включить светлую тему", () -> {
            lightMode = !lightMode;
            showPage("appearance");
        }));
        body.addView(text("АКЦЕНТ", 11, true, muted));
        LinearLayout swatches = row();
        int[] colors = {0xff6f5bea, 0xff5b9cff, 0xff36b995, 0xffe77aa5};
        for (int color : colors) {
            View dot = new View(this);
            dot.setBackground(shape(color, 24));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(42), dp(42));
            p.setMargins(0, dp(10), dp(12), dp(16));
            swatches.addView(dot, p);
        }
        body.addView(swatches);
        body.addView(text("Размер текста", 14, true, white));
        body.addView(text("Стандартный · оптимизирован для чтения на телефоне", 12, false, muted));
        body.addView(button("Назад к настройкам", () -> showPage("settings")));
    }

    private void privacyScreen() {
        title("Приватность и безопасность");
        subtitle("Эти параметры сохраняются на основном сервере Pluschat.");
        body.addView(text("Кто может видеть профиль", 14, true, white));
        Spinner visibility = new Spinner(this);
        String[] labels = {"Все пользователи", "Мои контакты", "Никто"};
        String[] values = {"everyone", "contacts", "nobody"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        visibility.setAdapter(adapter);
        body.addView(visibility);

        Switch showBirth = new Switch(this);
        showBirth.setText("Показывать дату рождения");
        showBirth.setTextColor(white);
        body.addView(showBirth);
        Switch showLocal = new Switch(this);
        showLocal.setText("Показывать местную группу");
        showLocal.setTextColor(white);
        body.addView(showLocal);
        Switch shareLocation = new Switch(this);
        shareLocation.setText("Разрешить использование кода района");
        shareLocation.setTextColor(white);
        body.addView(shareLocation);
        Switch notifications = new Switch(this);
        notifications.setText("Получать уведомления");
        notifications.setTextColor(white);
        body.addView(notifications);

        EditText area = input("Код района (необязательно)", false);
        body.addView(area);
        final boolean[] loaded = {false};
        request("GET", "settings", null, true, (data, error) -> {
            JSONObject settings = data.optJSONObject("settings");
            if (settings == null) return;
            String selected = settings.optString("profile_visibility", "everyone");
            for (int i = 0; i < values.length; i++) if (values[i].equals(selected)) visibility.setSelection(i);
            showBirth.setChecked(settings.optInt("show_birth_date", 0) == 1);
            showLocal.setChecked(settings.optInt("show_local_group", 0) == 1);
            shareLocation.setChecked(settings.optInt("location_sharing", 0) == 1);
            notifications.setChecked(settings.optInt("notifications_enabled", 1) == 1);
            area.setText(settings.optString("area_code", ""));
            loaded[0] = true;
        });
        visibility.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (loaded[0]) request("POST", "settings", ApiClient.json("profile_visibility", values[position]), true,
                        (data, error) -> { if (data.optBoolean("ok", false)) message("Видимость профиля сохранена."); });
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        showBirth.setOnCheckedChangeListener((buttonView, checked) -> {
            if (loaded[0]) request("POST", "settings", ApiClient.json("show_birth_date", checked), true,
                    (data, error) -> { if (data.optBoolean("ok", false)) message("Настройка даты рождения сохранена."); });
        });
        showLocal.setOnCheckedChangeListener((buttonView, checked) -> {
            if (loaded[0]) request("POST", "settings", ApiClient.json("show_local_group", checked), true,
                    (data, error) -> { if (data.optBoolean("ok", false)) message("Настройка местной группы сохранена."); });
        });
        shareLocation.setOnCheckedChangeListener((buttonView, checked) -> {
            if (loaded[0]) request("POST", "settings", ApiClient.json("location_sharing", checked), true,
                    (data, error) -> { if (data.optBoolean("ok", false)) message("Настройка района сохранена."); });
        });
        notifications.setOnCheckedChangeListener((buttonView, checked) -> {
            if (loaded[0]) request("POST", "settings", ApiClient.json("notifications_enabled", checked), true,
                    (data, error) -> { if (data.optBoolean("ok", false)) message("Настройки уведомлений сохранены."); });
        });
        body.addView(button("Сохранить код района", () -> {
            request("POST", "settings", ApiClient.json("area_code", value(area)), true,
                    (data, error) -> message(data.optBoolean("ok", false) ? "Код района сохранён." : "Не удалось сохранить код района."));
        }));
        body.addView(text("Двухэтапная аутентификация обязательна для доступа к перепискам.", 12, false, muted));
        body.addView(button("Активные устройства", () -> showPage("devices")));
        body.addView(button("Разрешения Android", () -> showPage("permissions")));
        body.addView(button("Назад к настройкам", () -> showPage("settings")));
    }

    private void chatSearchScreen() {
        title("Поиск в переписке");
        subtitle("Поиск выполняется на устройстве по расшифрованным сообщениям.");
        EditText query = input("Слово или фраза", false);
        body.addView(query);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        body.addView(results);
        body.addView(button("Искать", () -> {
            String term = value(query).trim().toLowerCase(java.util.Locale.ROOT);
            results.removeAllViews();
            if (term.isEmpty()) { message("Введи текст для поиска."); return; }
            if (currentChatId.isEmpty() || currentPeerId.isEmpty()) { message("Не удалось определить переписку."); return; }
            final String conversationId = currentChatId;
            fetchPeerKeys(currentPeerId, (keys, keyError) -> {
                if (keyError != null) { message(keyError); return; }
                request("GET", "conversations/" + conversationId + "/messages?limit=100", null, true, (data, error) -> {
                    JSONArray messages = data.optJSONArray("messages");
                    if (messages == null || messages.length() == 0) {
                        results.addView(text("Сообщений не найдено.", 14, false, muted));
                        return;
                    }
                    String ownShort = CryptoManager.deviceHash(this).substring(0, 32);
                    int found = 0;
                    for (int i = 0; i < messages.length(); i++) {
                        JSONObject item = messages.optJSONObject(i);
                        if (item == null) continue;
                        try {
                            JSONObject envelope = new JSONObject(item.optString("body", ""));
                            String[] pair = envelope.optString("key_id").split(":", -1);
                            if (pair.length != 2) continue;
                            String peerShort = pair[0].equals(ownShort) ? pair[1] : pair[0];
                            JSONObject peerKey = findPeerKey(keys, peerShort);
                            if (peerKey == null || !CryptoManager.verifyAndPinPeerKey(this, currentPeerId, peerKey)) continue;
                            String plain = CryptoManager.decrypt(this, conversationId, envelope.toString(),
                                    peerKey.optString("key_id"), peerKey.optString("signed_prekey"));
                            if (plain.toLowerCase(java.util.Locale.ROOT).contains(term)) {
                                String snippet = plain.length() > 140 ? plain.substring(0, 140) + "…" : plain;
                                results.addView(button(snippet, () -> showPage("chat")));
                                found++;
                            }
                        } catch (Exception ignored) {}
                    }
                    if (found == 0) results.addView(text("Совпадений в последних 100 сообщениях нет.", 14, false, muted));
                });
            });
        }));
        body.addView(button("Назад в переписку", () -> showPage("chat")));
    }

    private void clearCurrentChatHistory() {
        if (currentChatId.isEmpty()) { message("Переписка не выбрана."); return; }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Скрыть историю?")
                .setMessage("Сообщения будут скрыты только у тебя. У собеседника история останется.")
                .setNegativeButton("Отмена", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Скрыть", (dialog, which) -> loadMessageIds(currentChatId, null, new ArrayList<>(), ids -> {
                    if (ids.isEmpty()) { message("История уже пуста."); return; }
                    hideMessageIds(currentChatId, ids, 0, 0);
                }))
                .show();
    }

    private interface MessageIdsCallback { void onResult(ArrayList<String> ids); }

    private void loadMessageIds(String conversationId, String before, ArrayList<String> ids, MessageIdsCallback callback) {
        String path = "conversations/" + conversationId + "/messages?limit=100" + (before == null ? "" : "&before=" + android.net.Uri.encode(before));
        ApiClient.request("GET", path, null, SessionStore.token(this), (data, error) -> {
            JSONArray messages = data.optJSONArray("messages");
            if (error != null || messages == null) { message(error == null ? "Не удалось загрузить историю." : error); callback.onResult(new ArrayList<>()); return; }
            long oldestId = Long.MAX_VALUE;
            for (int i = 0; i < messages.length(); i++) {
                JSONObject item = messages.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "");
                if (!id.isEmpty()) {
                    ids.add(id);
                    try { oldestId = Math.min(oldestId, Long.parseLong(id)); } catch (NumberFormatException ignored) {}
                }
            }
            String nextBefore = oldestId == Long.MAX_VALUE ? null : String.valueOf(oldestId);
            if (messages.length() >= 100 && nextBefore != null && !nextBefore.equals(before)) {
                loadMessageIds(conversationId, nextBefore, ids, callback);
            } else callback.onResult(ids);
        });
    }

    private void hideMessageIds(String conversationId, ArrayList<String> ids, int index, int failures) {
        if (index >= ids.size()) {
            message(failures == 0 ? "История скрыта только у тебя." : "История скрыта частично. Не удалось скрыть: " + failures);
            if (conversationId.equals(currentChatId) && "chat".equals(page)) {
                LinearLayout history = historyView();
                if (history != null) refreshChatHistory(history);
            }
            return;
        }
        ApiClient.request("POST", "messages/" + ids.get(index) + "/delete",
                ApiClient.json("for_everyone", false), SessionStore.token(this), (data, error) ->
                    hideMessageIds(conversationId, ids, index + 1, failures + (error == null && data.optBoolean("ok", false) ? 0 : 1)));
    }

    private void chatInfoScreen() {
        title(currentChatTitle.isEmpty() ? "Информация о чате" : currentChatTitle);
        subtitle("Настройки и материалы переписки");
        body.addView(button("Медиа и файлы", () -> {
            message("Вложения зашифрованы и отображаются карточками в переписке.");
            showPage("chat");
        }));
        body.addView(button("Поиск в переписке", () -> showPage("chat_search")));
        body.addView(button("Скрыть чат", () -> {
            if (currentChatId.isEmpty()) { message("Не удалось определить чат."); return; }
            try {
                SessionStore.setChatHidden(this, currentChatId, true);
                message("Чат скрыт на этом устройстве. Управлять им можно в настройках.");
                showPage("home");
            } catch (Exception e) { message("Не удалось сохранить настройку скрытого чата."); }
        }));
        body.addView(button("Уведомления", () -> showPage("notifications")));
        body.addView(button("Очистить историю у меня", this::clearCurrentChatHistory));
        body.addView(button("Назад в переписку", () -> showPage("chat")));
    }

    private void permissionsScreen() {
        title("Разрешения");
        subtitle("Запрашиваем доступ только когда он нужен. Можно отказать — обычные текстовые чаты продолжат работать.");
        body.addView(button("Камера — фото профиля и будущая видеосвязь",()->askPermission(android.Manifest.permission.CAMERA,8101)));
        body.addView(button("Микрофон — голосовые сообщения и будущие звонки",()->askPermission(android.Manifest.permission.RECORD_AUDIO,8102)));
        body.addView(button("Контакты — поиск знакомых",()->askPermission(android.Manifest.permission.READ_CONTACTS,8103)));
        body.addView(button("Геолокация — отправка местоположения",()->askPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION,8104)));
        if (android.os.Build.VERSION.SDK_INT >= 33) body.addView(button("Уведомления о сообщениях",()->askPermission(android.Manifest.permission.POST_NOTIFICATIONS,8105)));
        body.addView(button("Фото и файлы",()->message("Для выбора аватарки и вложений используется системный выбор файлов Android без полного доступа ко всей галерее.")));
        body.addView(button("Открыть системные настройки Плюс Чат",()->{
            try { startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName()))); }
            catch(Exception ignored) {}
        }));
        body.addView(button("Назад",()->showPage("settings")));
    }
    private void askPermission(String permission, int requestCode) {
        if (android.os.Build.VERSION.SDK_INT < 23 || checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            message("Разрешение уже доступно."); return;
        }
        requestPermissions(new String[]{permission}, requestCode);
    }

    private void pickAvatar() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(android.content.Intent.createChooser(intent, "Выбрать аватарку"), 7101);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 7101 && resultCode == RESULT_OK && data != null) {
            selectedAvatar = data.getData();
            message("Изображение выбрано.");
        } else if (requestCode == 7102 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            sendEncryptedAttachment(data.getData());
        } else if (requestCode == 7103 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try (java.io.OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out == null || pendingDecryptedFile == null) throw new java.io.IOException("Cannot write destination");
                out.write(pendingDecryptedFile);
                pendingDecryptedFile = null;
                message("Файл расшифрован и сохранён.");
            } catch (Exception e) {
                message("Не удалось сохранить расшифрованный файл.");
            }
        }
    }



    private boolean isGiphyUrl(String value) {
        try {
            android.net.Uri uri = android.net.Uri.parse(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && (host.equalsIgnoreCase("giphy.com") || host.toLowerCase(java.util.Locale.ROOT).endsWith(".giphy.com"));
        } catch (Exception ignored) { return false; }
    }

    private void showStickerPicker() {
        String[] stickers = {"😀","😂","🥹","😍","🥳","😎","🤔","😭","❤️","🔥","👍","👎","🎉","💯","🤝","🫶","🐱","🐶","🌸","✨"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("Эмодзи и стикеры")
                .setItems(stickers, (dialog, which) -> {
                    LinearLayout history = historyView();
                    if (history == null) { message("Открой чат перед отправкой."); return; }
                    sendEncryptedMessage(stickers[which], history);
                })
                .setNeutralButton("Поиск GIF", (dialog, which) -> showGifSearch())
                .setPositiveButton("3D-иконки Thiings", (dialog, which) -> {
                    try { startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.thiings.co/things"))); }
                    catch (Exception e) { message("Не удалось открыть коллекцию Thiings."); }
                })
                .show();
    }

    private void showGifSearch() {
        EditText query = input("Что найти в GIF?", false);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Поиск GIF через GIPHY")
                .setView(query)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Найти", (dialog, which) -> {
                    String q = value(query);
                    if (q.isEmpty()) { message("Введи запрос для поиска GIF."); return; }
                    request("GET", "gifs/search?q=" + android.net.Uri.encode(q) + "&limit=20", null, true, (data, error) -> {
                        if (error != null) return;
                        JSONArray items = data.optJSONArray("data");
                        if (items == null || items.length() == 0) { message("GIF не найдены или GIPHY не настроен."); return; }
                        ArrayList<JSONObject> results = new ArrayList<>();
                        ArrayList<String> labels = new ArrayList<>();
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.optJSONObject(i);
                            if (item == null || !isGiphyUrl(item.optString("url"))) continue;
                            results.add(item);
                            String title = item.optString("title", "GIF").trim();
                            labels.add(title.isEmpty() ? "GIF " + (i + 1) : title);
                        }
                        if (results.isEmpty()) { message("В ответе GIPHY нет доступных GIF."); return; }
                        new android.app.AlertDialog.Builder(this).setTitle("Выбери GIF")
                                .setItems(labels.toArray(new String[0]), (d, selected) -> {
                                    JSONObject item = results.get(selected);
                                    try {
                                        JSONObject gif = ApiClient.json("type", "gif", "title", item.optString("title", "GIF"),
                                                "url", item.optString("url"), "provider", "giphy");
                                        LinearLayout history = historyView();
                                        if (history == null) { message("Открой чат перед отправкой."); return; }
                                        sendEncryptedMessage(gif.toString(), history);
                                    } catch (Exception e) { message("Не удалось подготовить GIF."); }
                                }).show();
                    });
                }).show();
    }

    private void startVoiceRecording(TextView control) {
        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 7201);
            message("Разреши доступ к микрофону, затем нажми запись ещё раз.");
            return;
        }
        try {
            voiceTempFile = java.io.File.createTempFile("pluschat-voice-", ".m4a", getCacheDir());
            voiceRecorder = android.os.Build.VERSION.SDK_INT >= 31 ? new android.media.MediaRecorder(this) : new android.media.MediaRecorder();
            voiceRecorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
            voiceRecorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
            voiceRecorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
            voiceRecorder.setAudioSamplingRate(44100);
            voiceRecorder.setAudioEncodingBitRate(96000);
            voiceRecorder.setMaxDuration(60000);
            voiceRecorder.setOutputFile(voiceTempFile.getAbsolutePath());
            voiceRecorder.setOnInfoListener((recorder, what, extra) -> {
                if (what == android.media.MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stopVoiceRecording();
            });
            voiceRecorder.prepare();
            voiceRecorder.start();
            voiceStartedAt = android.os.SystemClock.elapsedRealtime();
            recordingVoice = true;
            control.setText("■");
            message("Запись идёт. Нажми ■ для отправки (максимум 60 секунд).");
        } catch (Exception e) {
            releaseVoiceRecorder();
            message("Не удалось начать запись голосового сообщения.");
        }
    }

    private void stopVoiceRecording() {
        if (!recordingVoice || voiceRecorder == null || voiceTempFile == null) return;
        long duration = Math.max(1, android.os.SystemClock.elapsedRealtime() - voiceStartedAt);
        try {
            voiceRecorder.stop();
            voiceRecorder.reset();
            voiceRecorder.release();
            voiceRecorder = null;
            recordingVoice = false;
            byte[] bytes = java.nio.file.Files.readAllBytes(voiceTempFile.toPath());
            if (bytes.length < 32) { message("Запись слишком короткая."); return; }
            sendEncryptedVoice(bytes, duration);
        } catch (Exception e) {
            message("Запись не удалось завершить. Попробуй ещё раз.");
        } finally {
            recordingVoice = false;
            releaseVoiceRecorder();
            if (voiceTempFile != null) voiceTempFile.delete();
            voiceTempFile = null;
        }
    }

    private void releaseVoiceRecorder() {
        if (voiceRecorder != null) {
            try { voiceRecorder.reset(); } catch (Exception ignored) {}
            try { voiceRecorder.release(); } catch (Exception ignored) {}
            voiceRecorder = null;
        }
        recordingVoice = false;
    }

    private void sendEncryptedVoice(byte[] plaintext, long durationMs) {
        if (currentChatId.isEmpty() || currentPeerId.isEmpty()) { message("Сначала открой личную переписку."); return; }
        final String conversationId = currentChatId;
        final String peerId = currentPeerId;
        registerCryptoKeys(() -> fetchPeerKeys(peerId, (keys, keyError) -> {
            if (keyError != null) { message(keyError); return; }
            try {
                JSONObject peerKey = keys.optJSONObject(0);
                if (peerKey == null || !CryptoManager.verifyAndPinPeerKey(this, peerId, peerKey)) {
                    message("Не удалось проверить ключ собеседника."); return;
                }
                byte[] encryptedFile = CryptoManager.encryptFile(this, conversationId, plaintext,
                        peerKey.optString("key_id"), peerKey.optString("signed_prekey"));
                JSONObject pending = ApiClient.json("type", "attachment_pending");
                JSONObject envelope = CryptoManager.encrypt(this, conversationId, pending.toString(),
                        peerKey.optString("key_id"), peerKey.optString("signed_prekey"));
                JSONObject messagePayload = ApiClient.json("client_message_id", java.util.UUID.randomUUID().toString(),
                        "type", "file", "body", envelope.toString());
                ApiClient.request("POST", "conversations/" + conversationId + "/messages", messagePayload,
                        SessionStore.token(this), (created, createError) -> {
                    JSONObject savedMessage = created.optJSONObject("message");
                    String messageId = savedMessage == null ? "" : savedMessage.optString("id", "");
                    if (createError != null || !created.optBoolean("ok", false) || messageId.isEmpty()) {
                        message("Не удалось создать зашифрованное голосовое сообщение."); return;
                    }
                    ApiClient.uploadEncryptedFile(encryptedFile, "messages/" + messageId + "/attachments",
                            SessionStore.token(this), "voice", durationMs, (uploaded, uploadError) -> {
                        JSONObject attachment = uploaded.optJSONObject("attachment");
                        String attachmentId = attachment == null ? "" : attachment.optString("id", "");
                        if (uploadError != null || !uploaded.optBoolean("ok", false) || attachmentId.isEmpty()) {
                            message("Не удалось загрузить зашифрованное аудио."); return;
                        }
                        try {
                            JSONObject metadata = ApiClient.json("type", "voice", "attachment_id", attachmentId,
                                    "name", "voice.m4a", "mime_type", "audio/mp4", "byte_size", plaintext.length,
                                    "duration_ms", durationMs);
                            JSONObject finalEnvelope = CryptoManager.encrypt(this, conversationId, metadata.toString(),
                                    peerKey.optString("key_id"), peerKey.optString("signed_prekey"));
                            ApiClient.request("POST", "messages/" + messageId + "/edit",
                                    ApiClient.json("body", finalEnvelope.toString()), SessionStore.token(this), (edited, editError) -> {
                                if (editError != null || !edited.optBoolean("ok", false)) {
                                    message("Аудио загружено, но карточку сообщения не удалось обновить."); return;
                                }
                                message("Голосовое сообщение отправлено и зашифровано.");
                                if (conversationId.equals(currentChatId) && "chat".equals(page)) {
                                    LinearLayout history = historyView();
                                    if (history != null) refreshChatHistory(history);
                                }
                            });
                        } catch (Exception e) { message("Не удалось подготовить карточку голосового сообщения."); }
                    });
                });
            } catch (Exception e) { message("Не удалось зашифровать голосовое сообщение."); }
        }));
    }

    private void playEncryptedVoice(String conversationId, String attachmentId, String peerDeviceHash, String peerSignedPrekey) {
        ApiClient.downloadBytes("attachments/" + android.net.Uri.encode(attachmentId), SessionStore.token(this), (encrypted, error) -> {
            if (error != null || encrypted == null) { message("Не удалось скачать голосовое сообщение."); return; }
            final java.io.File[] tempHolder = new java.io.File[1];
            try {
                byte[] plain = CryptoManager.decryptFile(this, conversationId, encrypted, peerDeviceHash, peerSignedPrekey);
                final java.io.File playbackFile = java.io.File.createTempFile("pluschat-play-", ".m4a", getCacheDir());
                tempHolder[0] = playbackFile;
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(playbackFile)) { out.write(plain); }
                android.media.MediaPlayer player = new android.media.MediaPlayer();
                player.setDataSource(playbackFile.getAbsolutePath());
                player.setOnCompletionListener(mp -> { mp.release(); tempFileCleanup(playbackFile); });
                player.setOnErrorListener((mp, what, extra) -> { mp.release(); tempFileCleanup(playbackFile); message("Не удалось воспроизвести аудио."); return true; });
                player.prepare();
                player.start();
                message("Воспроизведение голосового сообщения.");
            } catch (Exception e) {
                if (tempHolder[0] != null) tempHolder[0].delete();
                message("Не удалось расшифровать или воспроизвести голосовое сообщение.");
            }
        });
    }

    private void tempFileCleanup(java.io.File file) { if (file != null) file.delete(); }

    private void pickAttachment() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(android.content.Intent.createChooser(intent, "Выбрать файл для шифрования"), 7102);
    }

    private String displayName(android.net.Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) return value.replace('\n', '_').replace('\r', '_');
                }
            }
        } catch (Exception ignored) {}
        return "encrypted-file.bin";
    }

    private byte[] readAttachmentBytes(android.net.Uri uri) throws Exception {
        try (java.io.InputStream in = getContentResolver().openInputStream(uri);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            if (in == null) throw new java.io.IOException("Cannot read file");
            byte[] buffer = new byte[8192]; int n; int total = 0;
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > 25 * 1024 * 1024) throw new java.io.IOException("File exceeds 25 MB");
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    private void sendEncryptedAttachment(android.net.Uri uri) {
        if (currentChatId.isEmpty() || currentPeerId.isEmpty()) {
            message("Сначала открой личную переписку.");
            return;
        }
        final String conversationId = currentChatId;
        final String peerId = currentPeerId;
        final String originalName = displayName(uri);
        String mime = getContentResolver().getType(uri);
        final String originalMime = mime == null ? "application/octet-stream" : mime;
        final byte[] plaintext;
        try {
            plaintext = readAttachmentBytes(uri);
            if (plaintext.length == 0) { message("Пустые файлы отправлять нельзя."); return; }
        } catch (Exception e) {
            message("Файл не удалось прочитать. Максимальный размер — 25 МБ.");
            return;
        }
        registerCryptoKeys(() -> fetchPeerKeys(peerId, (keys, keyError) -> {
            if (keyError != null) { message(keyError); return; }
            try {
                JSONObject peerKey = keys.optJSONObject(0);
                if (peerKey == null || !CryptoManager.verifyAndPinPeerKey(this, peerId, peerKey)) {
                    message("Не удалось проверить ключ собеседника.");
                    return;
                }
                byte[] encryptedFile = CryptoManager.encryptFile(this, conversationId, plaintext,
                        peerKey.optString("key_id"), peerKey.optString("signed_prekey"));
                JSONObject pending = ApiClient.json("type", "attachment_pending");
                JSONObject envelope = CryptoManager.encrypt(this, conversationId, pending.toString(),
                        peerKey.optString("key_id"), peerKey.optString("signed_prekey"));
                JSONObject messagePayload = ApiClient.json("client_message_id", java.util.UUID.randomUUID().toString(),
                        "type", "file", "body", envelope.toString());
                ApiClient.request("POST", "conversations/" + conversationId + "/messages", messagePayload,
                        SessionStore.token(this), (created, createError) -> {
                    JSONObject savedMessage = created.optJSONObject("message");
                    String messageId = savedMessage == null ? "" : savedMessage.optString("id", "");
                    if (createError != null || !created.optBoolean("ok", false) || messageId.isEmpty()) {
                        message("Не удалось создать зашифрованное сообщение для вложения.");
                        return;
                    }
                    ApiClient.uploadEncryptedFile(encryptedFile, "messages/" + messageId + "/attachments",
                            SessionStore.token(this), (uploaded, uploadError) -> {
                        JSONObject attachment = uploaded.optJSONObject("attachment");
                        String attachmentId = attachment == null ? "" : attachment.optString("id", "");
                        if (uploadError != null || !uploaded.optBoolean("ok", false) || attachmentId.isEmpty()) {
                            ApiClient.request("POST", "messages/" + messageId + "/delete",
                                    ApiClient.json("for_everyone", true), SessionStore.token(this), (ignored, deleteError) -> {});
                            message("Не удалось загрузить зашифрованный файл.");
                            return;
                        }
                        try {
                            JSONObject metadata = ApiClient.json("type", "file", "attachment_id", attachmentId,
                                    "name", originalName, "mime_type", originalMime, "byte_size", plaintext.length);
                            JSONObject finalEnvelope = CryptoManager.encrypt(this, conversationId, metadata.toString(),
                                    peerKey.optString("key_id"), peerKey.optString("signed_prekey"));
                            ApiClient.request("POST", "messages/" + messageId + "/edit",
                                    ApiClient.json("body", finalEnvelope.toString()), SessionStore.token(this), (edited, editError) -> {
                                if (editError != null || !edited.optBoolean("ok", false)) {
                                    message("Файл загружен в зашифрованном виде, но карточку вложения не удалось обновить.");
                                    return;
                                }
                                message("Файл зашифрован и отправлен.");
                                if (conversationId.equals(currentChatId) && "chat".equals(page)) {
                                    LinearLayout history = historyView();
                                    if (history != null) refreshChatHistory(history);
                                }
                            });
                        } catch (Exception e) {
                            message("Не удалось подготовить карточку вложения.");
                        }
                    });
                });
            } catch (Exception e) {
                message("Не удалось зашифровать файл на устройстве.");
            }
        }));
    }

    private LinearLayout historyView() {
        if (body != null && body.getChildCount() > 1) {
            for (int i = 0; i < body.getChildCount(); i++) {
                View child = body.getChildAt(i);
                if (child instanceof ScrollView) {
                    View inner = ((ScrollView) child).getChildAt(0);
                    if (inner instanceof LinearLayout) return (LinearLayout) inner;
                }
            }
        }
        return null;
    }

    private void downloadEncryptedAttachment(String conversationId, String attachmentId, String name, String mime,
                                             String peerDeviceHash, String peerSignedPrekey) {
        ApiClient.downloadBytes("attachments/" + android.net.Uri.encode(attachmentId), SessionStore.token(this), (encrypted, error) -> {
            if (error != null || encrypted == null) { message(error == null ? "Не удалось скачать файл." : error); return; }
            try {
                byte[] plaintext = CryptoManager.decryptFile(this, conversationId, encrypted, peerDeviceHash, peerSignedPrekey);
                pendingDecryptedFile = plaintext;
                pendingDecryptedName = name == null || name.isEmpty() ? "download.bin" : name;
                pendingDecryptedMime = mime == null || mime.isEmpty() ? "application/octet-stream" : mime;
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                intent.setType(pendingDecryptedMime);
                intent.putExtra(android.content.Intent.EXTRA_TITLE, pendingDecryptedName);
                startActivityForResult(intent, 7103);
            } catch (Exception e) {
                message("Не удалось расшифровать файл. Проверь ключи и целостность вложения.");
            }
        });
    }

    private void openCommunity(boolean channel) { android.content.Intent intent = new android.content.Intent(this, CommunityActivity.class); intent.putExtra("channel", channel); startActivity(intent); }

    private void logout() {
        String token = SessionStore.token(this);
        SessionStore.clear(this);
        showPage("auth");
        if (!token.isEmpty()) ApiClient.request("POST", "auth/logout", new JSONObject(), token, (data, error) -> {});
    }
    private void request(String method,String path,JSONObject payload,boolean auth,ApiClient.Callback callback) {
        String token=auth?SessionStore.token(this):"";
        ApiClient.request(method,path,payload,token,(data,error)->{
            if(error!=null){message(error);return;}
            callback.onResult(data,error);
        });
    }
    private String value(EditText e){return e==null?"":e.getText().toString().trim();}
    private void message(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
