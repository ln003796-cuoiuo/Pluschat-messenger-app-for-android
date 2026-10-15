# Pluschat Gateway API contract v1

**Gateway base:** `https://xn--n1aabel.xn--80avljg2a1c.xn--p1ai/api/v1/`

The Android client calls only this gateway. The gateway validates inputs and forwards authorized operations to the main host over HTTPS. The APK must never contain database credentials or gateway-to-main-host secrets.

## Common rules
- JSON UTF-8 requests/responses, except multipart uploads.
- Authenticated requests use `Authorization: Bearer <access_token>`.
- Error format: `{"error":{"code":"...","message":"..."}}`. The gateway must accept `X-HTTP-Method-Override: PATCH` on POST requests for PATCH operations, because the Android client uses this compatibility path.
- Use TLS, rate limits, OTP expiry/attempt limits, password hashing, refresh-token rotation, authorization checks on every object, and audit sensitive changes.
- Registration completion returns session tokens; the app enters the account immediately without a second login.
- Optional profile fields may be omitted or empty. Derive age from birth date.
- Ask for Android permissions only when the related feature is used. Declining a permission must not block text messaging.

## Authentication and registration
- `POST auth/registration/email/start` — `{email}`; sends a verification code and returns `{challenge_id, expires_in}`. A verified phone challenge ID may optionally be supplied for users who choose to add a phone.
- `POST auth/registration/email/verify` — `{challenge_id, code}`.
- Optional phone verification: `POST auth/registration/start` — `{phone}`; sends SMS and returns `{challenge_id, expires_in}`; then `POST auth/registration/verify-phone` — `{challenge_id, code}`. SMS requires a configured provider.
- `POST auth/registration/complete` — `{challenge_id?, email_challenge_id, email, password, first_name, last_name, username, bio?, birth_date?, education?, avatar_upload_id?}`; requires verified email, and verified phone only if a phone challenge is supplied. Returns `{user, access_token, refresh_token, expires_in}`.
- `POST auth/login` — `{identifier, password}` where identifier is phone or email; returns tokens or `{requires_2fa:true, challenge_id}`.
- `POST auth/2fa/verify` — `{challenge_id, code}`; returns session tokens.
- `POST auth/token/refresh` — `{refresh_token}`; rotates refresh token.
- `POST auth/logout` — revokes current session.
- `POST auth/password/forgot`, `POST auth/password/reset` — recovery flow with verified contact and short-lived token.
- `POST auth/2fa/setup`, `POST auth/2fa/confirm`, `POST auth/2fa/disable`, `POST auth/2fa/recovery-codes/regenerate` — authenticator-based TOTP and hashed one-use recovery codes. Never use email/SMS as the only factor and label it as strong 2FA; they can be account recovery/verification methods.

## Profile, devices, contacts, privacy
- `GET me`, `PATCH me`, `POST me/avatar` (multipart image upload).
- `GET users/search?q=<query>`, `GET users/{user_id}`.
- `GET contacts`, `POST contacts`, `DELETE contacts/{user_id}`.
- `GET devices`, `DELETE devices/{device_id}`; list/revoke sessions.
- `GET me/privacy`, `PATCH me/privacy`; visibility values for phone, email, birth date, avatar, online status, last seen, who can message/add to groups.
- `GET users/blocked`, `POST users/{user_id}/block`, `DELETE users/{user_id}/block`.
- `GET notifications`, `POST notifications/{id}/read`, `POST notifications/read-all`.

## Chats and messages
- `GET chats?cursor=...`, `POST chats` with `{type:"direct", user_id}`.
- `GET chats/{chat_id}`, `PATCH chats/{chat_id}/settings`, `POST chats/{chat_id}/mute`.
- `GET chats/{chat_id}/messages?before=...&limit=50`.
- `POST chats/{chat_id}/messages` with `{client_message_id, type:"text", body}`; deduplicate by client_message_id.
- `POST messages/{message_id}/read`, `PATCH messages/{message_id}`, `DELETE messages/{message_id}?scope=me|all`.
- `POST messages/{message_id}/reactions`, `DELETE messages/{message_id}/reactions/{reaction}`.
- `GET chats/{chat_id}/search?q=...`, `GET chats/{chat_id}/media`.
- Future attachments: `POST uploads` (multipart, authorized, size/type checked); then reference returned upload ID in a message.

## Groups and topics
- `GET groups`, `POST groups` with `{title, description?, visibility}`.
- `GET groups/{id}`, `PATCH groups/{id}`, `DELETE groups/{id}`.
- `GET groups/{id}/members`, `POST groups/{id}/members`, `DELETE groups/{id}/members/{user_id}`.
- `PATCH groups/{id}/members/{user_id}/role`, `PATCH groups/{id}/permissions`, `GET groups/{id}/audit-log`.
- `GET groups/{id}/topics`, `POST groups/{id}/topics`, `PATCH groups/{id}/topics/{topic_id}`, `DELETE groups/{id}/topics/{topic_id}`.
- `POST groups/{id}/invite-links`, `DELETE groups/{id}/invite-links/{link_id}`.
- Enforce owner/admin/member roles on the server; admins must not get access to private direct messages.

## Channels and posts
- `GET channels`, `POST channels` with `{title, description?, visibility}`.
- `GET channels/{id}`, `PATCH channels/{id}`, `DELETE channels/{id}`.
- `GET channels/{id}/subscribers`, `POST channels/{id}/subscribe`, `DELETE channels/{id}/subscribe`.
- `GET channels/{id}/posts`, `POST channels/{id}/posts`, `PATCH channels/{id}/posts/{post_id}`, `DELETE channels/{id}/posts/{post_id}`.
- `POST channels/{id}/admins`, `DELETE channels/{id}/admins/{user_id}`, `GET channels/{id}/stats`.
- Comments and reactions are optional channel features controlled by owner settings.

## Client behavior
- Do not claim an action succeeded before server confirmation.
- If the gateway is unavailable, show a useful connection error and preserve drafts.
- Use pagination for history and lists; deduplicate messages using client_message_id.
- Use push notifications when server-side push is configured; while app is open, refresh messages using polling until a realtime service exists.
- This document is the integration contract. The PHP gateway currently implements email-code registration, password login, token refresh/revocation, profile read/update, user search, direct chat creation, and text-message history/send. Import `database/schema.sql`, deploy the PHP gateway, and configure `config.php` from `config.example.php`. Email delivery must work through PHP `mail()` on the host. SMS, avatar uploads, push delivery, and 2FA still require provider/endpoint setup.
