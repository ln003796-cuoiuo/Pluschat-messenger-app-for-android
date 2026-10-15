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
    private final int bg = Color.rgb(10, 12, 20), panel = Color.rgb(25, 29, 43), white = Color.rgb(246, 247, 255), muted = Color.rgb(157, 166, 190), accent = Color.rgb(170, 153, 255);
    private LinearLayout root, body, chatList;
    private String page = "auth", registrationStage = "phone", phoneChallenge = "", emailChallenge = "", currentChatId = "", currentChatTitle = "";
    private EditText phoneField, codeField, emailField, emailCodeField, firstNameField, lastNameField, usernameField, passwordField, bioField, birthField, educationField, identifierField, loginPasswordField, messageField, searchField;
    private ArrayList<JSONObject> chats = new ArrayList<>();
    private boolean lightMode = true;
    private android.net.Uri selectedAvatar;
    private final Handler chatHandler = new Handler(Looper.getMainLooper());
    private Runnable chatPoll;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showPage(SessionStore.token(this).isEmpty() ? "auth" : "home");
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    private TextView text(String value, int size, boolean bold, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(20, 18, 20, 18); return l;
    }
    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }
    private EditText input(String hint, boolean secret) {
        EditText e = new EditText(this); e.setSingleLine(!hint.equals("Описание профиля (необязательно)"));
        e.setHint(hint); e.setTextColor(lightMode ? 0xff282438 : white);
        e.setHintTextColor(lightMode ? 0xff8c869b : muted); e.setTextSize(15);
        e.setPadding(14, 8, 14, 8); e.setBackground(shape(lightMode ? 0xfff0eef7 : panel, 16));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, secret ? 54 : -2); p.setMargins(0, 5, 0, 5); e.setLayoutParams(p);
        if (secret) e.setInputType(129);
        return e;
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label); b.setTextColor(0xff241b42); b.setAllCaps(false); b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setGravity(Gravity.CENTER);
        b.setPadding(18, 0, 18, 0); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setStateListAnimator(null); b.setElevation(0); b.setBackground(shape(0xffd9d0ff, 18));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, 52); p.setMargins(0, 6, 0, 6); b.setLayoutParams(p);
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
        p.setMargins(0, 18, 0, 8);
        body.addView(label, p);
    }
    private void showPage(String target) {
        if (chatPoll != null) { chatHandler.removeCallbacks(chatPoll); chatPoll = null; }
        page = target;
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(lightMode ? Color.rgb(245,245,247) : bg);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(20, 12, 20, 12);
        header.setBackground(shape(lightMode ? Color.WHITE : Color.rgb(19, 19, 24), 0));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.pluschat_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(44, 44);
        logoParams.setMargins(0, 0, 12, 0);
        header.addView(logo, logoParams);
        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        TextView brand = text("Плюс Чат", 21, true, lightMode ? Color.BLACK : white);
        TextView tagline = text("Твои люди. Твои разговоры.", 12, false, muted);
        brandText.addView(brand);
        brandText.addView(tagline);
        header.addView(brandText, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header, new LinearLayout.LayoutParams(-1, 76));
        ScrollView scroll = new ScrollView(this); body = column(); scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
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
        else if (target.equals("twofa")) twoFactorScreen();
        else if (target.equals("twofa_setup")) twoFactorSetupScreen();
        else if (target.equals("permissions")) permissionsScreen();
    }
    private void title(String s) { body.addView(text(s, 25, true, lightMode ? Color.BLACK : white)); }
    private void subtitle(String s) { TextView t = text(s, 14, false, muted); t.setPadding(0, 4, 0, 12); body.addView(t); }
    private void authScreen() {
        title("С возвращением"); subtitle("Войди по номеру телефона или электронной почте.");
        identifierField = input("Телефон или email", false); body.addView(identifierField);
        loginPasswordField = input("Пароль", true); body.addView(loginPasswordField);
        body.addView(button("Войти", this::login));
        body.addView(button("Создать аккаунт", () -> { registrationStage = "phone"; showPage("register"); }));
        body.addView(button("Не получается войти?", () -> message("Восстановление пароля будет доступно после подключения серверного API.")));
        subtitle("Шлюз: " + BuildConfig.API_BASE_URL);
    }
    private void registerScreen() {
        title("Создание аккаунта");
        if (registrationStage.equals("phone")) {
            subtitle("1 из 3 · Номер телефона");
            phoneField = input("Номер телефона с кодом страны", false); body.addView(phoneField);
            body.addView(button("Отправить SMS-код", () -> {
                String phone = value(phoneField); if (phone.isEmpty()) { message("Введи номер телефона."); return; }
                request("POST", "auth/registration/start", ApiClient.json("phone", phone), false, (data,error)-> {
                    phoneChallenge = data.optString("challenge_id", "");
                    if (phoneChallenge.isEmpty()) { message("Сервер не вернул challenge_id."); return; }
                    registrationStage = "phone_code"; showPage("register");
                });
            }));
        } else if (registrationStage.equals("phone_code")) {
            subtitle("2 из 3 · Подтверждение номера");
            codeField = input("Код из SMS", false); body.addView(codeField);
            body.addView(button("Подтвердить номер", () -> request("POST", "auth/registration/verify-phone", ApiClient.json("challenge_id", phoneChallenge, "code", value(codeField)), false, (data,error)-> {
                registrationStage = "email"; showPage("register");
            })));
            body.addView(button("Назад", () -> { registrationStage = "phone"; showPage("register"); }));
        } else if (registrationStage.equals("email")) {
            subtitle("3 из 3 · Подтверждение почты");
            emailField = input("Электронная почта", false); body.addView(emailField);
            body.addView(button("Отправить код на почту", () -> {
                String email = value(emailField); if (email.isEmpty()) { message("Введи email."); return; }
                request("POST", "auth/registration/email/start", ApiClient.json("challenge_id", phoneChallenge, "email", email), false, (data,error)-> {
                    emailChallenge = data.optString("challenge_id", phoneChallenge);
                    registrationStage = "email_code"; showPage("register");
                });
            }));
        } else if (registrationStage.equals("email_code")) {
            subtitle("Введи код из письма.");
            emailCodeField = input("Код из email", false); body.addView(emailCodeField);
            body.addView(button("Подтвердить почту", () -> request("POST", "auth/registration/email/verify", ApiClient.json("challenge_id", emailChallenge, "code", value(emailCodeField)), false, (data,error)-> {
                registrationStage = "profile"; showPage("register");
            })));
        } else {
            subtitle("Данные аккаунта. Дата рождения, учёба и описание — необязательно.");
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
            body.addView(button("Назад", () -> { registrationStage = "email"; showPage("register"); }));
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
        body.setPadding(20, 18, 20, 24);

        LinearLayout welcome = new LinearLayout(this);
        welcome.setOrientation(LinearLayout.VERTICAL);
        welcome.setPadding(22, 22, 22, 22);
        GradientDrawable welcomeBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff30245e, 0xff6751b8, 0xff927ce8});
        welcomeBg.setCornerRadius(28);
        welcome.setBackground(welcomeBg);
        TextView eyebrow = text("ТВОЁ ПРОСТРАНСТВО", 11, true, 0xffd8ceff);
        eyebrow.setLetterSpacing(0.12f);
        welcome.addView(eyebrow);
        TextView greeting = text("Общение ближе.", 27, true, Color.WHITE);
        greeting.setPadding(0, 8, 0, 3);
        welcome.addView(greeting);
        TextView welcomeHint = text("Люди, чаты и сообщества — в одном месте.", 14, false, 0xffe7e1ff);
        welcomeHint.setMaxLines(2);
        welcome.addView(welcomeHint);
        LinearLayout.LayoutParams welcomeParams = new LinearLayout.LayoutParams(-1, -2);
        welcomeParams.setMargins(0, 0, 0, 18);
        body.addView(welcome, welcomeParams);

        LinearLayout searchRow = row();
        searchRow.setPadding(15, 0, 14, 0);
        searchRow.setBackground(shape(0xfff0eef7, 17));
        TextView searchIcon = text("⌕", 25, false, 0xff766b91);
        searchRow.addView(searchIcon);
        searchField = new EditText(this);
        searchField.setSingleLine(true);
        searchField.setTextSize(15);
        searchField.setHint("Поиск людей и переписок");
        searchField.setHintTextColor(0xff89839a);
        searchField.setTextColor(0xff242033);
        searchField.setBackgroundColor(Color.TRANSPARENT);
        searchField.setPadding(10, 0, 0, 0);
        searchRow.addView(searchField, new LinearLayout.LayoutParams(0, 54, 1));
        TextView go = text("→", 22, true, 0xff6550b2);
        go.setGravity(Gravity.CENTER);
        searchRow.addView(go, new LinearLayout.LayoutParams(34, 54));
        searchRow.setOnClickListener(v -> showPage("search"));
        go.setOnClickListener(v -> showPage("search"));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, 54);
        searchParams.setMargins(0, 0, 0, 24);
        body.addView(searchRow, searchParams);

        TextView quickTitle = text("Быстрый доступ", 20, true, 0xff211d30);
        body.addView(quickTitle);
        TextView quickSub = text("Всё нужное — в пару касаний", 13, false, 0xff888298);
        quickSub.setPadding(0, 3, 0, 12);
        body.addView(quickSub);

        LinearLayout shortcuts = row();
        shortcuts.setGravity(Gravity.TOP);
        shortcuts.addView(homeShortcut("✦", "Группы", "Общие чаты", 0xffeee9ff, 0xff6349b5, () -> openCommunity(false)));
        shortcuts.addView(homeShortcut("◉", "Каналы", "Новости", 0xffffecdf, 0xffb76a35, () -> openCommunity(true)));
        shortcuts.addView(homeShortcut("♧", "Контакты", "Твои люди", 0xffdff5ec, 0xff278565, () -> showPage("contacts")));
        shortcuts.addView(homeShortcut("♢", "События", "Оповещения", 0xffe4efff, 0xff3d70b8, () -> showPage("notifications")));
        LinearLayout.LayoutParams shortcutsParams = new LinearLayout.LayoutParams(-1, -2);
        shortcutsParams.setMargins(-4, 0, -4, 22);
        body.addView(shortcuts, shortcutsParams);

        LinearLayout sectionRow = row();
        TextView chatsTitle = text("Сообщения", 22, true, 0xff211d30);
        sectionRow.addView(chatsTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView refresh = text("Обновить  ↻", 13, true, 0xff6954b8);
        refresh.setPadding(8, 10, 0, 10);
        refresh.setOnClickListener(v -> loadChats());
        sectionRow.addView(refresh);
        body.addView(sectionRow);
        TextView chatHint = text("Твои последние разговоры", 13, false, 0xff888298);
        chatHint.setPadding(0, 2, 0, 12);
        body.addView(chatHint);

        chatList = new LinearLayout(this);
        chatList.setOrientation(LinearLayout.VERTICAL);
        body.addView(chatList, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout account = row();
        account.setPadding(15, 12, 15, 12);
        account.setBackground(shape(0xfff0eef7, 20));
        TextView accountIcon = text("☺", 25, true, 0xff6550b2);
        accountIcon.setGravity(Gravity.CENTER);
        account.addView(accountIcon, new LinearLayout.LayoutParams(42, 42));
        LinearLayout accountText = new LinearLayout(this);
        accountText.setOrientation(LinearLayout.VERTICAL);
        accountText.setPadding(12, 0, 0, 0);
        accountText.addView(text("Твой аккаунт", 15, true, 0xff29243a));
        accountText.addView(text("Профиль и настройки приватности", 12, false, 0xff817b91));
        account.addView(accountText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = text("›", 28, false, 0xff766b91);
        account.addView(arrow);
        account.setOnClickListener(v -> showPage("profile"));
        LinearLayout.LayoutParams accountParams = new LinearLayout.LayoutParams(-1, -2);
        accountParams.setMargins(0, 22, 0, 12);
        body.addView(account, accountParams);
        body.addView(button("Настройки", () -> showPage("settings")));
        body.addView(button("Выйти из аккаунта", this::logout));
        loadChats();
    }

    private View homeShortcut(String symbol, String title, String detail, int surface, int ink, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(4, 12, 4, 12);
        card.setBackground(shape(surface, 19));
        TextView glyph = text(symbol, 24, true, ink);
        glyph.setGravity(Gravity.CENTER);
        card.addView(glyph, new LinearLayout.LayoutParams(-1, 31));
        TextView name = text(title, 12, true, 0xff2d293b);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, 7, 0, 0);
        card.addView(name);
        TextView caption = text(detail, 10, false, 0xff777186);
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
                empty.setBackground(shape(0xfff4f1fa, 22));
                TextView icon = text("✉", 34, true, 0xff735bc3);
                icon.setGravity(Gravity.CENTER);
                empty.addView(icon);
                TextView label = text("Здесь будет твоя история", 17, true, 0xff29243a);
                label.setGravity(Gravity.CENTER);
                label.setPadding(0, 8, 0, 4);
                empty.addView(label);
                TextView hint = text("Найди человека и начни первый разговор.", 13, false, 0xff817b91);
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
                    item.setBackground(shape(Color.WHITE, 19));
                    LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, -2);
                    itemParams.setMargins(0, 4, 0, 4);
                    TextView avatar = text(name.isEmpty() ? "П" : name.substring(0, 1).toUpperCase(), 19, true, 0xff654db3);
                    avatar.setGravity(Gravity.CENTER);
                    avatar.setBackground(shape(0xffeee9ff, 30));
                    item.addView(avatar, new LinearLayout.LayoutParams(50, 50));
                    LinearLayout details = new LinearLayout(this);
                    details.setOrientation(LinearLayout.VERTICAL);
                    details.setPadding(13, 0, 0, 0);
                    details.addView(text(name, 15, true, 0xff272236));
                    TextView previewText = text(preview, 12, false, 0xff858096);
                    previewText.setMaxLines(1);
                    previewText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    details.addView(previewText);
                    item.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
                    TextView arrow = text("›", 26, false, 0xffa09aae);
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
                bubble.setBackground(shape(mine ? 0xff55458f : panel, 18));
                TextView sender=text(m.optString("sender_name", mine ? "Вы" : "Участник"),12,true,mine ? 0xffe5dcff : accent);
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
        title("Настройки");subtitle("Аккаунт · безопасность · приватность · оформление");
        body.addView(button(lightMode?"Включить тёмную тему":"Включить светлую тему",()->{lightMode=!lightMode;showPage("settings");}));
        body.addView(button("Настроить двухэтапную аутентификацию",()->showPage("twofa_setup")));
        body.addView(button("Активные устройства",()->message("Управление сессиями появится после подключения API устройств.")));
        body.addView(button("Разрешения Android",()->showPage("permissions")));
        body.addView(button("Назад",()->showPage("home")));
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
