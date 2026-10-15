# Pluschat for Android

Native Android messenger client. The app communicates only with the Pluschat gateway:

`https://xn--n1aabel.xn--80avljg2a1c.xn--p1ai/api/v1/`

The gateway API is versioned and documented in `docs/API_CONTRACT.md`. The app does not connect directly to the main host. Server-side endpoints must be implemented before real registration, OTP delivery, login, or messaging can work.

## Build

GitHub Actions builds a debug APK on pushes and pull requests. Download it from the workflow run's **Artifacts** section.

## Security

Never store database credentials or server secrets in this Android repository or APK. Use HTTPS, short-lived access tokens, refresh-token rotation, server-side authorization, and rate limits. OTP delivery requires server-side SMS/email providers.