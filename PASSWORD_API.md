# Password recovery API

Copy `.env.example` values into the backend's ignored `.env` file and replace the placeholders with a real Brevo API key and a verified sender address. Do not put these values in the Expo `.env` file or source code. The backend reads them as `app.email.brevo.*` configuration.

The database schema is migrated by the existing Hibernate `ddl-auto=update` setting. It creates `password_challenges`; OTP hashes and reset-token hashes are stored, never the OTP or raw reset token.

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/password/change` | Bearer session | Verify current password and change it |
| POST | `/api/password/change/request-otp` | Bearer session | Send OTP to the signed-in account email when current password is forgotten |
| POST | `/api/password/forgot/request` | Public | Request recovery OTP; response does not reveal account existence |
| POST | `/api/password/otp/verify` | Public | Verify challenge ID and six-digit OTP; returns a short-lived reset token |
| POST | `/api/password/reset` | Public | Consume the reset token and update password |

Successful password change or reset invalidates all existing app sessions. Recovery codes expire after ten minutes, are single-use, allow at most five wrong attempts, and requests are limited by a 60-second per-email cooldown and five requests per IP in 15 minutes. A reset authorization token expires after ten minutes.

Signup and login APIs are unchanged. Email OTP only proves control of the account's registered email address at recovery time; the current application does not have a separate email-verification flow.
