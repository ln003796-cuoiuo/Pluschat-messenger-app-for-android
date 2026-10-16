# Pluschat для Android

Нативный Android-клиент Pluschat на Java. Интерфейс приводится к макету Figma:

https://www.figma.com/design/K1DkOFipvcBO19qrmWnZGG

Приложение использует HTTPS API основного хоста:

`https://xn--80avljg2a1c.xn--p1ai/api/v1/`

## Сборка APK

Открой проект в Android Studio, дождись синхронизации Gradle и выбери **Build → Build APK(s)**. Debug APK после успешной сборки находится по пути:

`app/build/outputs/apk/debug/app-debug.apk`

В проекте намеренно нет GitHub Actions. Для сборки нужны JDK 17, Android SDK 35 и доступ к Maven/Google репозиториям для загрузки зависимостей.

## Важно

- Не добавляй пароль базы данных или серверные секреты в APK.
- Реальные регистрация, подтверждение email и сообщения требуют доступного API на хостинге.
- UI-код и успешная компиляция сами по себе не подтверждают работу API или сквозного шифрования.
