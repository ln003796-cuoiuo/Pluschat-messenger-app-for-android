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
    private String page = "auth", registrationStage = "email", phoneChallenge = "", emailChallenge = "", currentChatId = "", currentChatTitle = "";
    private EditText phoneField, codeField, emailField, emailCodeField, firstNameField, lastNameField, usernameField, passwordField, bioField, birthField, educationField, identifierField, loginPasswordField, messageField, searchField;
    private ArrayList<JSONObject> chats = new ArrayList<>();
    private boolean lightMode = false;
    private android.net.Uri selectedAvatar;
    private final Handler chatHandler = new Handler(Looper.getMainLooper());
    private Runnable chatPoll;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showPage(SessionStore.token(this).isEmpty() ? "auth" : "home");
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
        b.setPadding(16, 12, 16, 12);
        b.setMinHeight(76);
        b.setBackground(shape(panel, 20));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.setMargins(4, 5, 4, 5);
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
        boolean authFlow = target.equals("auth") || target.equals("register") || target.equals("twofa");
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
        if (!authFlow) root.addView(header, new LinearLayout.LayoutParams(-1, dp(72)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = column();
        body.setPadding(dp(20), dp(16), dp(20), dp(24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        boolean signedIn = !SessionStore.token(this).isEmpty()
                && !target.equals("auth") && !target.equals("register")
                && !target.equals("twofa");
        if (signedIn) {
            LinearLayout nav = new LinearLayout(this);
            nav.setOrientation(LinearLayout.HORIZONTAL);
            nav.setGravity(Gravity.CENTER);
            nav.setPadding(dp(8), dp(7), dp(8), dp(7));
            nav.setBackgroundColor(lightMode ? Color.WHITE : 0xff0f1219);
            nav.addView(navItem("⌂", "Чаты", "home", target.equals("home") || target.equals("chat") || target.equals("search")));
            nav.addView(navItem("♙", "Контакты", "contacts", target.equals("contacts")));
            nav.addView(navItem("✦", "Сообщества", "communities", target.equals("communities")));
            nav.addView(navItem("☺", "Профиль", "profile", target.equals("profile") || target.equals("settings") || target.equals("permissions") || target.equals("privacy") || target.equals("appearance") || target.equals("chat_info") || target.startsWith("twofa_")));
            root.addView(nav, new LinearLayout.LayoutParams(-1, dp(72)));
        }
        setContentView(root);
        if (target.equals("auth")) authScreen();
        else if (target.equals("register")) registerScreen();
        else if (target.equals("home")) homeScreen();
        else if (target.equals("chat")) chatScreen();
        else if (target.equals("search")) searchScreen();
        else if (target.equals("contacts")) contactsScreen();
        else if (target.equals("notifications")) notificationsScreen();
        else if (target.equals("profile")) profileScreen();
        else if (target.equals("settings")) settingsScreen();
        else if (target.equals("privacy")) privacyScreen();
        else if (target.equals("appearance")) appearanceScreen();
        else if (target.equals("chat_info")) chatInfoScreen();
        else if (target.equals("twofa")) twoFactorScreen();
        else if (target.equals("twofa_setup")) twoFactorSetupScreen();
        else if (target.equals("permissions")) permissionsScreen();
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

    private void title(String s) { TextView t=text(s, 25, true, lightMode ? Color.BLACK : white); t.setPadding(0, 5, 0, 5); body.addView(t); }
    private void subtitle(String s) { TextView t = text(s, 14, false, muted); t.setPadding(0, 4, 0, 12); body.addView(t); }
    private void authScreen() {
        title("Войти"); subtitle("Продолжите общение с близкими");
        TextView emailLabel = text("Электронная почта или телефон", 12, true, muted);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2); labelParams.setMargins(0, dp(28), 0, dp(2)); body.addView(emailLabel, labelParams);
        identifierField = input("name@example.com", false); body.addView(identifierField);
        TextView passwordLabel = text("Пароль", 12, true, muted);
        LinearLayout.LayoutParams passwordLabelParams = new LinearLayout.LayoutParams(-1, -2); passwordLabelParams.setMargins(0, dp(10), 0, dp(2)); body.addView(passwordLabel, passwordLabelParams);
        loginPasswordField = input("Введите пароль", true); body.addView(loginPasswordField);
        TextView forgot = text("Забыли пароль?", 12, true, 0xffb7adff); forgot.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        forgot.setPadding(0, dp(6), 0, dp(12)); forgot.setOnClickListener(v -> message("Восстановление пароля будет доступно после подключения серверного API.")); body.addView(forgot);
        body.addView(button("Войти", this::login));
        TextView divider = text("или продолжить с", 12, false, muted); divider.setGravity(Gravity.CENTER); divider.setPadding(0, dp(16), 0, dp(8)); body.addView(divider);
        body.addView(button("✉   Получить код на почту", () -> message("Вход по одноразовому коду будет подключён вместе с серверным API.")));
        TextView signup = text("Нет аккаунта?  Создать", 13, true, 0xffb7adff); signup.setGravity(Gravity.CENTER); signup.setPadding(0, dp(22), 0, dp(12)); signup.setOnClickListener(v -> { registrationStage = "email"; showPage("register"); }); body.addView(signup);
    }
    private void registerScreen() {
        title("Регистрация");
        if (registrationStage.equals("email")) {
            subtitle("Это займёт всего пару минут");
            emailField = input("Электронная почта", false); body.addView(emailField);
            body.addView(button("Отправить код на почту", () -> {
                String email = value(emailField);
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { message("Введи корректный email."); return; }
                request("POST", "auth/registration/email/start", ApiClient.json("email", email), false, (data,error)-> {
                    emailChallenge = data.optString("challenge_id", "");
                    if (emailChallenge.isEmpty()) { message("Сервер не вернул challenge_id."); return; }
                    registrationStage = "email_code"; showPage("register");
                });
            }));
        } else if (registrationStage.equals("email_code")) {
            subtitle("Шаг 2 из 3 · Введи код из письма");
            emailCodeField = input("Код подтверждения", false); body.addView(emailCodeField);
            body.addView(button("Подтвердить почту", () -> request("POST", "auth/registration/email/verify",
                ApiClient.json("challenge_id", emailChallenge, "code", value(emailCodeField)), false, (data,error)-> {
                    registrationStage = "profile"; showPage("register");
                })));
            body.addView(button("Изменить email", () -> { registrationStage = "email"; showPage("register"); }));
        } else {
            subtitle("Шаг 3 из 3 · Данные профиля. Email уже подтверждён.");
            body.addView(button("Выбрать аватарку", this::pickAvatar));
            if (selectedAvatar != null) subtitle("Аватар выбран. Он загрузится после создания аккаунта.");
            firstNameField = input("Имя", false); body.addView(firstNameField);
            lastNameField = input("Фамилия", false); body.addView(lastNameField);
            usernameField = input("@username", false); body.addView(usernameField);
            passwordField = input("Пароль (не менее 10 символов)", true); body.addView(passwordField);
            bioField = input("Описание профиля (необязательно)", false); body.addView(bioField);
            birthField = input("Дата рождения YYYY-MM-DD (необязательно)", false); body.addView(birthField);
            educationField = input("Учёба / работа (необязательно)", false); body.addView(educationField);
            body.addView(button("Создать аккаунт и войти", this::completeRegistration));
            body.addView(button("Назад", () -> { registrationStage = "email_code"; showPage("register"); }));
        }
        body.addView(button("Отмена", () -> showPage("auth")));
    }
    private void completeRegistration() {
        String fn=value(firstNameField), ln=value(lastNameField), un=value(usernameField), pw=value(passwordField);
        if (fn.isEmpty() || ln.isEmpty() || un.isEmpty() || pw.length()<10) { message("Заполни имя, фамилию, username и пароль от 10 символов."); return; }
        JSONObject payload=ApiClient.json("challenge_id", phoneChallenge, "email_challenge_id", emailChallenge,
            "email", emailField == null ? "" : value(emailField), "password", pw, "first_name", fn, "last_name", ln,
            "username", un.replaceFirst("^@", ""), "bio", value(bioField), "birth_date", value(birthField), "education", value(educationField));
        request("POST", "auth/registration/complete", payload, false, (data,error)-> {
            if (!saveSession(data)) return;
            if (selectedAvatar != null) {
                ApiClient.uploadFile(this, selectedAvatar, "me/avatar", SessionStore.token(this), (upload, uploadError) -> {
                    if (uploadError != null) message("Аккаунт создан, но аватар не загрузился: " + uploadError);
                    showPage("home");
                });
            } else showPage("home");
        });
    }
    private void login() {
        String id=value(identifierField), pw=value(loginPasswordField);
        if(id.isEmpty()||pw.isEmpty()){message("Введи телефон/email и пароль.");return;}
        request("POST","auth/login",ApiClient.json("identifier",id,"password",pw),false,(data,error)->{
            if(data.optBoolean("requires_2fa",false)){ pending2fa=data.optString("challenge_id",""); showPage("twofa"); return; }
            if(saveSession(data)) showPage("home");
        });
    }
    private String pending2fa="";
    private void twoFactorScreen() {
        title("Дополнительная защита"); subtitle("Введи одноразовый код второго фактора.");
        EditText code=input("Код 2FA",false); body.addView(code);
        body.addView(button("Подтвердить и войти",()->request("POST","auth/2fa/verify",ApiClient.json("challenge_id",pending2fa,"code",value(code)),false,(data,error)->{if(saveSession(data))showPage("home");})));
    }
    private boolean saveSession(JSONObject data) {
        String access=data.optString("access_token",""), refresh=data.optString("refresh_token","");
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
                message("Двухэтапная аутентификация включена.");showPage("settings");
            });
        }));
        body.addView(button("Отключить 2FA",()->request("POST","auth/2fa/disable",new JSONObject(),true,(data,error)->{message("2FA отключена.");showPage("settings");})));
        body.addView(button("Назад",()->showPage("settings")));
    }

    private void homeScreen() {
        body.setPadding(18, 18, 18, 22);
        LinearLayout titleRow = row();
        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);
        titleStack.addView(text("Чаты", 29, true, white));
        titleStack.addView(text("Твои разговоры в одном месте", 12, false, muted));
        titleRow.addView(titleStack, new LinearLayout.LayoutParams(0, -2, 1));
        TextView newChat = text("+", 28, false, Color.WHITE);
        newChat.setGravity(Gravity.CENTER);
        newChat.setBackground(shape(0xff6f5bea, 18));
        titleRow.addView(newChat, new LinearLayout.LayoutParams(48, 48));
        newChat.setOnClickListener(v -> showPage("search"));
        body.addView(titleRow);
        LinearLayout searchRow = row();
        searchRow.setPadding(14, 0, 12, 0);
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
        searchField.setPadding(10, 0, 0, 0);
        searchRow.addView(searchField, new LinearLayout.LayoutParams(0, 52, 1));
        TextView go = text("→", 22, true, 0xffb9adff);
        go.setGravity(Gravity.CENTER);
        searchRow.addView(go, new LinearLayout.LayoutParams(34, 52));
        searchRow.setOnClickListener(v -> showPage("search"));
        go.setOnClickListener(v -> showPage("search"));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, 52);
        searchParams.setMargins(0, 18, 0, 16);
        body.addView(searchRow, searchParams);

        LinearLayout filters = row();
        filters.setPadding(0, 0, 0, 12);
        filters.addView(filterChip("Все", true), new LinearLayout.LayoutParams(0, 38, 1));
        filters.addView(filterChip("Личные", false), new LinearLayout.LayoutParams(0, 38, 1));
        filters.addView(filterChip("Группы", false), new LinearLayout.LayoutParams(0, 38, 1));
        body.addView(filters);

        LinearLayout sectionRow = row();
        sectionRow.addView(text("Недавние", 18, true, white), new LinearLayout.LayoutParams(0, -2, 1));
        TextView refresh = text("Обновить ↻", 12, true, 0xffb9adff);
        refresh.setPadding(8, 8, 0, 8);
        refresh.setOnClickListener(v -> loadChats());
        sectionRow.addView(refresh);
        body.addView(sectionRow);

        chatList = new LinearLayout(this);
        chatList.setOrientation(LinearLayout.VERTICAL);
        body.addView(chatList, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout quick = row();
        quick.setPadding(14, 14, 14, 14);
        quick.setBackground(shape(0xff151925, 18));
        TextView groupIcon = text("✦", 23, true, 0xffb9adff);
        groupIcon.setGravity(Gravity.CENTER);
        quick.addView(groupIcon, new LinearLayout.LayoutParams(40, 44));
        LinearLayout quickText = new LinearLayout(this);
        quickText.setOrientation(LinearLayout.VERTICAL);
        quickText.setPadding(10, 0, 0, 0);
        quickText.addView(text("Сообщества", 14, true, white));
        quickText.addView(text("Группы и каналы", 11, false, muted));
        quick.addView(quickText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = text("›", 26, false, muted);
        quick.addView(arrow);
        quick.setOnClickListener(v -> openCommunity(false));
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(-1, -2);
        qp.setMargins(0, 18, 0, 10);
        body.addView(quick, qp);
        loadChats();
    }

    private View filterChip(String label, boolean selected) {
        TextView chip = text(label, 12, true, selected ? 0xffe2dcff : 0xffa1aabd);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(shape(selected ? 0xff292443 : 0xff171b24, 19));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -1);
        p.setMargins(3, 0, 3, 0);
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
        card.setPadding(10, 16, 10, 16);
        card.setBackground(shape(surface, 19));
        TextView glyph = text(symbol, 28, true, ink);
        glyph.setGravity(Gravity.CENTER);
        card.addView(glyph, new LinearLayout.LayoutParams(-1, 31));
        TextView name = text(title, 14, true, 0xff2d293b);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, 7, 0, 0);
        card.addView(name);
        TextView caption = text(detail, 12, false, 0xff777186);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(0, 2, 0, 0);
        card.addView(caption);
        card.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.setMargins(4, 0, 4, 0);
        card.setLayoutParams(p);
        return card;
    }
    private void loadChats() {
        if (chatList == null || !"home".equals(page)) return;
        chatList.removeAllViews();
        TextView loading = text("Загружаем переписки…", 14, false, 0xff89839a);
        loading.setPadding(4, 18, 4, 18);
        chatList.addView(loading);
        request("GET", "chats", null, true, (data, error) -> {
            if (!"home".equals(page) || chatList == null) return;
            chats.clear();
            JSONArray arr = data.optJSONArray("chats");
            if (arr == null) arr = data.optJSONArray("data");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject chat = arr.optJSONObject(i);
                if (chat != null) chats.add(chat);
            }
            chatList.removeAllViews();
            if (chats.isEmpty()) {
                LinearLayout empty = new LinearLayout(this);
                empty.setOrientation(LinearLayout.VERTICAL);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(18, 25, 18, 25);
                empty.setBackground(shape(0xff151925, 22));
                TextView icon = text("✉", 34, true, 0xffb9adff);
                icon.setGravity(Gravity.CENTER);
                empty.addView(icon);
                TextView label = text("Здесь будет твоя история", 17, true, 0xfff5f7fb);
                label.setGravity(Gravity.CENTER);
                label.setPadding(0, 8, 0, 4);
                empty.addView(label);
                TextView hint = text("Найди человека и начни первый разговор.", 13, false, 0xff98a2b5);
                hint.setGravity(Gravity.CENTER);
                empty.addView(hint);
                empty.setOnClickListener(v -> showPage("search"));
                chatList.addView(empty);
            } else {
                for (JSONObject chat : chats) {
                    String id = chat.optString("id", chat.optString("chat_id", ""));
                    String name = chat.optString("title", chat.optString("name", "Чат"));
                    String preview = chat.optString("last_message", "Открыть переписку");
                    LinearLayout item = row();
                    item.setPadding(12, 13, 12, 13);
                    item.setBackground(shape(0xff131720, 19));
                    LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, -2);
                    itemParams.setMargins(0, 4, 0, 4);
                    TextView avatar = text(name.isEmpty() ? "П" : name.substring(0, 1).toUpperCase(), 19, true, 0xffd7d0ff);
                    avatar.setGravity(Gravity.CENTER);
                    avatar.setBackground(shape(0xff292443, 30));
                    item.addView(avatar, new LinearLayout.LayoutParams(50, 50));
                    LinearLayout details = new LinearLayout(this);
                    details.setOrientation(LinearLayout.VERTICAL);
                    details.setPadding(13, 0, 0, 0);
                    details.addView(text(name, 15, true, 0xfff5f7fb));
                    TextView previewText = text(preview, 12, false, 0xff98a2b5);
                    previewText.setMaxLines(1);
                    previewText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    details.addView(previewText);
                    item.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
                    TextView arrow = text("›", 26, false, 0xff7d8799);
                    item.addView(arrow);
                    item.setOnClickListener(v -> {
                        currentChatId = id;
                        currentChatTitle = name;
                        showPage("chat");
                    });
                    chatList.addView(item, itemParams);
                }
            }
        });
    }

    private void chatScreen() {
        title(currentChatTitle.isEmpty()?"Переписка":currentChatTitle);
        body.addView(button("← К чатам",()->showPage("home")));
        body.addView(button("Информация о чате  ›",()->showPage("chat_info")));
        LinearLayout history = new LinearLayout(this);
        history.setOrientation(LinearLayout.VERTICAL);
        history.setPadding(0, 10, 0, 10);
        body.addView(history, new LinearLayout.LayoutParams(-1, -2));
        messageField=input("Сообщение…",false);body.addView(messageField);
        body.addView(button("Отправить",()->{
            String msg=value(messageField);if(msg.trim().isEmpty())return;
            String clientId=java.util.UUID.randomUUID().toString();
            request("POST","chats/"+currentChatId+"/messages",ApiClient.json("client_message_id",clientId,"type","text","body",msg),true,(data,error)->{messageField.setText("");refreshChatHistory(history);});
        }));
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
        if (currentChatId.isEmpty()) {
            history.removeAllViews();
            history.addView(text("Открой чат из списка.", 14, false, muted));
            return;
        }
        request("GET","chats/"+currentChatId+"/messages?limit=50",null,true,(data,error)->{
            if (!"chat".equals(page)) return;
            JSONArray arr=data.optJSONArray("messages");
            if(arr==null)arr=data.optJSONArray("data");
            history.removeAllViews();
            if(arr==null || arr.length()==0) {
                TextView empty=text("Пока нет сообщений.\nНапиши первым 👋",15,false,muted);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(18, 30, 18, 30);
                history.addView(empty);
                return;
            }
            for(int i=0;i<arr.length();i++) {
                JSONObject m=arr.optJSONObject(i);
                if(m==null) continue;
                boolean mine=m.optBoolean("is_mine", false);
                LinearLayout bubble=new LinearLayout(this);
                bubble.setOrientation(LinearLayout.VERTICAL);
                bubble.setPadding(14, 10, 14, 10);
                bubble.setBackground(shape(mine ? 0xff5142a6 : 0xff171b24, 18));
                TextView sender=text(m.optString("sender_name", mine ? "Вы" : "Участник"),12,true,mine ? 0xffe5dcff : 0xffb9adff);
                TextView content=text(m.optString("body",""),15,false,white);
                content.setMaxWidth((int)(getResources().getDisplayMetrics().widthPixels * 0.72f));
                bubble.addView(sender);
                bubble.addView(content);
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);
                params.setMargins(0,4,0,4);
                params.gravity=mine ? Gravity.RIGHT : Gravity.LEFT;
                history.addView(bubble,params);
            }
        });
    }
    private void searchScreen() {
        title("Найти людей");subtitle("Ищи по username, имени или фамилии.");
        EditText q=input("@username или имя",false);body.addView(q);
        LinearLayout results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);body.addView(results);
        body.addView(button("Поиск",()->{
            results.removeAllViews();String query=value(q);
            request("GET","users/search?q="+android.net.Uri.encode(query),null,true,(data,error)->{
                JSONArray arr=data.optJSONArray("users");if(arr==null)arr=data.optJSONArray("data");
                if(arr==null||arr.length()==0){results.addView(text("Пользователи не найдены или API ещё не подключён.",14,false,muted));return;}
                for(int i=0;i<arr.length();i++){JSONObject u=arr.optJSONObject(i);if(u==null)continue;
                    String uid=u.optString("id"), name=u.optString("first_name","")+" "+u.optString("last_name",""), uname=u.optString("username","");
                    results.addView(button(name+"  @"+uname,()->request("POST","chats",ApiClient.json("type","direct","user_id",uid),true,(chatData,apiError)->{currentChatId=chatData.optString("id",chatData.optString("chat_id",""));currentChatTitle=name;showPage("chat");})));
                }
            });
        }));
        body.addView(button("Назад",()->showPage("home")));
    }

    private void contactsScreen() {
        title("Контакты"); subtitle("Люди, с которыми ты можешь быстро начать переписку.");
        LinearLayout results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);body.addView(results);
        request("GET","contacts",null,true,(data,error)->{
            JSONArray arr=data.optJSONArray("contacts");if(arr==null)arr=data.optJSONArray("data");
            if(arr==null||arr.length()==0){results.addView(text("Контактов пока нет. Найди пользователя по username.",14,false,muted));return;}
            for(int i=0;i<arr.length();i++){JSONObject u=arr.optJSONObject(i);if(u==null)continue;
                String id=u.optString("id",u.optString("user_id",""));
                String name=u.optString("first_name","")+" "+u.optString("last_name","")+"  @"+u.optString("username","");
                results.addView(button(name,()->request("POST","chats",ApiClient.json("type","direct","user_id",id),true,(chatData,apiError)->{currentChatId=chatData.optString("id",chatData.optString("chat_id",""));currentChatTitle=name;showPage("chat");})));
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
        title("Мой профиль");subtitle("Редактирование профиля будет работать через GET/PATCH me.");
        EditText name=input("Имя",false), surname=input("Фамилия",false), username=input("@username",false), bio=input("Описание",false), birth=input("Дата рождения YYYY-MM-DD",false), edu=input("Учёба / работа",false);
        body.addView(button("Выбрать аватарку", this::pickAvatar));
        if (selectedAvatar != null) subtitle("Выбран файл аватарки.");
        body.addView(button("Загрузить выбранную аватарку", () -> { if (selectedAvatar == null) { message("Сначала выбери изображение."); return; } ApiClient.uploadFile(this, selectedAvatar, "me/avatar", SessionStore.token(this), (data,error) -> { if(error!=null)message(error);else message("Аватар обновлён."); }); }));
        body.addView(name);body.addView(surname);body.addView(username);body.addView(bio);body.addView(birth);body.addView(edu);
        request("GET","me",null,true,(data,error)->{JSONObject u=data.optJSONObject("user");if(u==null)u=data;name.setText(u.optString("first_name",""));surname.setText(u.optString("last_name",""));username.setText(u.optString("username",""));bio.setText(u.optString("bio",""));birth.setText(u.optString("birth_date",""));edu.setText(u.optString("education",""));});
        body.addView(button("Сохранить профиль",()->request("PATCH","me",ApiClient.json("first_name",value(name),"last_name",value(surname),"username",value(username).replaceFirst("^@",""),"bio",value(bio),"birth_date",value(birth),"education",value(edu)),true,(data,error)->message("Профиль сохранён."))));
        body.addView(button("Назад",()->showPage("home")));
    }
    private void settingsScreen() {
        title("Настройки");
        subtitle("Аккаунт · безопасность · приватность · оформление");
        LinearLayout account = row();
        account.setPadding(14, 14, 14, 14);
        account.setBackground(shape(0xff171b24, 18));
        TextView avatar = text("К", 23, true, 0xffe2dcff);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(shape(0xff292443, 40));
        account.addView(avatar, new LinearLayout.LayoutParams(48, 48));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(12, 0, 0, 0);
        info.addView(text("Мой профиль", 15, true, white));
        info.addView(text("@username · Управление аккаунтом", 11, false, muted));
        account.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        account.setOnClickListener(v -> showPage("profile"));
        body.addView(account);
        body.addView(button("Внешний вид  ›", () -> showPage("appearance")));
        body.addView(button("Приватность и безопасность  ›", () -> showPage("privacy")));
        body.addView(button("Уведомления", () -> showPage("notifications")));
        body.addView(button("Настроить двухэтапную аутентификацию", () -> showPage("twofa_setup")));
        body.addView(button("Активные устройства", () -> message("Управление сессиями появится после подключения API устройств.")));
        body.addView(button("Разрешения Android", () -> showPage("permissions")));
        body.addView(button("Выйти из аккаунта", this::logout));
        body.addView(button("Назад", () -> showPage("home")));
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
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(42, 42);
            p.setMargins(0, 10, 12, 16);
            swatches.addView(dot, p);
        }
        body.addView(swatches);
        body.addView(text("Размер текста", 14, true, white));
        body.addView(text("Стандартный · оптимизирован для чтения на телефоне", 12, false, muted));
        body.addView(button("Назад к настройкам", () -> showPage("settings")));
    }

    private void privacyScreen() {
        title("Приватность и безопасность");
        subtitle("Ты контролируешь видимость своего профиля.");
        String[][] options = {
            {"Номер телефона", "Никто"},
            {"Дата рождения", "Только вы"},
            {"Добавление в группы", "Мои контакты"},
            {"Предпросмотр сообщений", "Скрывать содержимое"},
            {"Активные устройства", "Управление сессиями"}
        };
        for (String[] option : options) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(15, 13, 15, 13);
            item.setBackground(shape(0xff131720, 15));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.setMargins(0, 4, 0, 4);
            item.addView(text(option[0], 14, true, white));
            item.addView(text(option[1], 12, false, muted));
            TextView chevron = text("›", 23, false, muted);
            chevron.setGravity(Gravity.CENTER);
            item.addView(chevron, new LinearLayout.LayoutParams(-1, 24));
            item.setOnClickListener(v -> message("Настройка «" + option[0] + "» будет подключена к серверным правилам приватности."));
            body.addView(item, p);
        }
        body.addView(button("Двухэтапная аутентификация", () -> showPage("twofa_setup")));
        body.addView(button("Разрешения Android", () -> showPage("permissions")));
        body.addView(button("Назад к настройкам", () -> showPage("settings")));
    }

    private void chatInfoScreen() {
        title(currentChatTitle.isEmpty() ? "Информация о чате" : currentChatTitle);
        subtitle("Настройки и материалы переписки");
        body.addView(button("Медиа и файлы", () -> message("Раздел медиа появится после подключения загрузок файлов.")));
        body.addView(button("Поиск в переписке", () -> showPage("search")));
        body.addView(button("Уведомления", () -> message("Настройки уведомлений чата появятся отдельно.")));
        body.addView(button("Очистить историю", () -> message("История на сервере не изменена.")));
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

        }
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
