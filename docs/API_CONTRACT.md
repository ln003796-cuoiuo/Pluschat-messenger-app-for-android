# Pluschat Gateway API contract (v1)

Base URL: `https://xn--n1aabel.xn--80avljg2a1c.xn--p1ai/api/v1/`

The Android client sends requests only to this gateway. It must validate requests and forward authorized operations to the main host over HTTPS. Do not treat a device ID as proof of identity. Never embed gateway-to-main-host secrets in the APK.

All requests/responses use JSON UTF-8. Errors should use `{ "error": { "code": "...", "message": "..." } }`. Authenticated requests use `Authorization: Bearer <access_token>`. Use TLS, rate limits, OTP expiry/attempt limits, password hashing, access checks on every chat/message, and refresh-token rotation server-side.

## Authentication
- `POST auth/registration/start` — `{phone}`; returns `challenge_id` and delivery metadata.
- `POST auth/registration/verify-phone` — `{challenge_id, code}`.
- `POST auth/registration/email/start` — `{challenge_id, email}`.
- `POST auth/registration/email/verify` — `{challenge_id, code}`.
- `POST auth/registration/complete` — `{challenge_id, email, password, first_name, last_name, username, bio, birth_date, education, avatar_upload_id}`; returns `{user, access_token, refresh_token, expires_in}` after both contacts are verified.
- `POST auth/login` — `{identifier, password}`; returns either authenticated session or `{requires_2fa:true, challenge_id}`.
- `POST auth/2fa/verify` — `{challenge_id, code}`; returns session tokens.
- `POST auth/token/refresh` — `{refresh_token}`; rotates refresh token and returns new tokens.
- `POST auth/logout` — authenticated; revokes current refresh/session.
- `POST auth/password/forgot` and `POST auth/password/reset` — secure reset flow.

## Profile and discovery
- `GET me`, `PATCH me`, `POST me/avatar`.
- `GET users/search?q=<username-or-name>`.
- `GET contacts`, `POST contacts`, `DELETE contacts/{user_id}`, `GET users/{user_id}`.

## Chats and messages
- `GET chats?cursor=...`, `POST chats` with `{type:"direct", user_id}`.
- `GET chats/{chat_id}/messages?before=...&limit=50`.
- `POST chats/{chat_id}/messages` with `{client_message_id, type:"text", body}`; server deduplicates by client_message_id.
- `POST messages/{message_id}/read`.
- `PATCH messages/{message_id}`, `DELETE messages/{message_id}?scope=me|all`.
- `GET chats/{chat_id}/search?q=...`.

## Groups and channels (planned)
- `POST groups`, `GET groups/{id}`, `PATCH groups/{id}`, `POST groups/{id}/members`, `DELETE groups/{id}/members/{user_id}`.
- `POST groups/{id}/topics`, `PATCH groups/{id}/topics/{topic_id}`.
- `POST channels`, `PATCH channels/{id}`, `POST channels/{id}/posts`, `GET channels/{id}/posts`.

## Client expectations
- The client must not claim an action succeeded until the server confirms it.
- When the gateway is unavailable, show a useful connection error and preserve form drafts.
- Registration completion must return tokens so the app can enter the account immediately without asking the user to log in again.
- Profile fields for age/date of birth, education/work, avatar and bio are optional except fields explicitly required by server policy. Age should be derived from birth date.
- Runtime permissions are requested only when the related feature is used. Declining a permission must not prevent ordinary text messaging.

This contract is the client/server integration target; server endpoints and SMS/email providers still need to be implemented on hosting.