# Phone & email OTP setup

SafePath supports **phone (SMS)** and **email** verification via Supabase Auth. If SMS fails, users can verify with email instead (no Twilio required for email).

## Step 1 — Enable Phone provider in Supabase

1. Open [Supabase Dashboard](https://supabase.com/dashboard)
2. Select your project
3. Go to **Authentication** → **Providers** → **Phone**
4. Toggle **Enable Phone provider** ON
5. Save

## Step 2 — Connect Twilio (SMS)

Supabase needs an SMS provider. Twilio is the default.

### Create a Twilio account

1. Sign up at [twilio.com](https://www.twilio.com/try-twilio)
2. From the Twilio Console, copy:
   - **Account SID**
   - **Auth Token**
3. Create either:
   - A **Messaging Service** (recommended), or
   - Buy a phone number with SMS capability

### Add Twilio to Supabase

Back in **Authentication** → **Providers** → **Phone**:

| Field | Value |
|-------|--------|
| SMS provider | Twilio |
| Twilio Account SID | From Twilio Console |
| Twilio Auth Token | From Twilio Console |
| Twilio Message Service SID | From Messaging Service (or leave blank if using a single number) |

Save changes.

### Twilio trial notes

- Trial accounts can only SMS **verified recipient numbers**. Add your test number under Twilio → **Phone Numbers** → **Verified Caller IDs**.
- Trial messages are prefixed with *"Sent from your Twilio trial account"*.
- For production in Sri Lanka, upgrade Twilio and ensure your sender supports international SMS to `+94`.

## Step 3 — Confirm auth settings

In **Authentication** → **Settings**:

- **Enable phone signups** should be ON
- Rate limits: default is 1 OTP per 60 seconds per number

## Step 4 — Apply database migrations

Run these if not already applied:

```
supabase/migrations/20260709120000_add_phone_verification.sql
supabase/migrations/20260709130000_family_circle.sql
```

## Step 5 — Test from terminal

Replace with your project URL, anon key, and phone number:

```bash
curl -X POST "https://YOUR_PROJECT.supabase.co/auth/v1/otp" \
  -H "apikey: YOUR_ANON_KEY" \
  -H "Authorization: Bearer YOUR_ANON_KEY" \
  -H "Content-Type: application/json" \
  -d '{"phone":"+94XXXXXXXXX"}'
```

**Success:** HTTP `200` with `{}` — SMS should arrive within seconds.

**Still failing:** Read the JSON `error_code` in the response.

| error_code | Fix |
|------------|-----|
| `phone_provider_disabled` | Enable Phone provider (Step 1) |
| `sms_send_failed` | Check Twilio credentials and verified numbers |
| `over_sms_send_rate_limit` | Wait 60 seconds and retry |

## Step 6 — Rebuild and test the app

1. Rebuild: `./gradlew :app:assembleDebug`
2. Open **Verify your account**
3. Choose **Phone** or **Email** tab
4. Enter your number or email → **Send verification code**
5. Enter the 6-digit code on the next screen

## Email rate limit (common on free tier)

If Sign Up shows **"email rate limit"** / fails to create account:

Supabase free projects only allow a few auth emails per hour.

**Quick fix for testing:**
1. Supabase Dashboard → **Authentication** → **Providers** → **Email**
2. Turn **Confirm email** **OFF**
3. Save
4. Retry Sign Up — account is created immediately with no email needed

**Or:** wait ~1 hour and try again (do not spam Create Account).

## Email OTP (fallback + signup confirmation)

1. Supabase Dashboard → **Authentication** → **Providers** → **Email** → enable
2. Under Email provider, you can leave **Confirm email** ON — SafePath will guide the user after Sign Up
3. **Authentication** → **Email Templates** → open **Magic Link** (and Confirm signup if present) and set the body to include the code:

```html
<h2>SafePath verification</h2>
<p>Your code is: <strong>{{ .Token }}</strong></p>
<p>Enter this 6-digit code in the app. It expires in 1 hour.</p>
```

Do **not** rely only on `{{ .ConfirmationURL }}` — the app can also verify a typed OTP code.

4. Flows that use email:
   - **Sign Up** → check email / confirm → continue
   - **Verify account → Email tab** → send code → enter code → Family Circle unlocked

5. Use a real email inbox (Gmail, etc.). Addresses like `test@example.com` are rejected by Supabase.

## Deep link (optional)

For magic links to open the app directly, add this Redirect URL in Supabase → Authentication → URL Configuration:

```
com.hades.safepath://login-callback
```

## Local development (optional)

For offline Supabase CLI testing without real SMS, use `test_otp` in `config.toml`:

```toml
[auth.sms]
enable_signup = true

[auth.sms.test_otp]
94770000000 = "123456"

[auth.sms.twilio]
enabled = true
account_sid = "test_account_sid"
message_service_sid = "test_message_service_sid"
auth_token = "test_auth_token"
```

Then point the app at your local Supabase URL during development.
