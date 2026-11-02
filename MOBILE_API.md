# Mobile bank-account API

All bank-account operations require `Authorization: Bearer <app-session-token>`. The API uses the mobile number already stored on the signed-in account; `POST /api/bank-accounts/send-otp` only accepts that same number. No Setu Account Aggregator consent or balance-data endpoints are implemented in this build.

## Verify the registered phone

`POST /api/bank-accounts/send-otp`

```json
{"mobile":"9876543210","challengeId":null}
```

The backend starts an SMS challenge through Twilio Verify when configured and returns a challenge ID, expiry, resend wait, and delivery mode. It never returns the OTP. To resend, submit the current challenge ID after the resend wait.

`POST /api/bank-accounts/verify-otp`

```json
{"challengeId":"<challenge-id>","otp":"123456"}
```

Challenges expire after five minutes and allow at most five incorrect attempts. A successfully verified challenge is single use for linking an account.

## Link and verify a bank account

`POST /api/bank-accounts/link`

```json
{
  "challengeId":"<verified-challenge-id>",
  "bankName":"HDFC Bank",
  "accountHolderName":"Example User",
  "accountNumber":"1234567890123456",
  "confirmAccountNumber":"1234567890123456"
}
```

The backend stores only the masked account number and encrypts the account-holder name using the configured 32-byte `BANK_DATA_ENCRYPTION_KEY`. Linking is disabled unless a Cashfree bank-verification provider is configured.

`POST /api/bank-accounts/verify`

```json
{"accountId":"<linked-account-id>","accountNumber":"1234567890123456","ifsc":"HDFC0000240"}
```

The account number and IFSC are sent to Cashfree over HTTPS for verification; IFSC is not stored. Sandbox responses remain `PENDING` and never verify a real account. Provider setup and approval are required for production verification.

Other authenticated routes:

- `GET /api/bank-accounts/my-accounts` lists the current user's linked accounts.
- `DELETE /api/bank-accounts/{accountId}` removes an account owned by the current user.
- `GET /api/bank-accounts/balance` is unavailable until FIU approval and a consent-backed data-fetch flow are implemented.

The Wallet UI reports provider readiness and does not treat pending or sandbox results as verified accounts.