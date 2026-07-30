# 🔐 Echo Messaging — Multi-Auth Implementation Guide

## Overview

Three new authentication flows added **without touching** existing `/auth/login` and `/auth/signup`:

| Flow | Endpoints | How it works |
|------|-----------|--------------|
| **Email OTP** | `/auth/email-otp/send`, `/auth/email-otp/verify` | 6-digit OTP via email → verify → JWT |
| **Phone OTP** | `/auth/phone-otp/send`, `/auth/phone-otp/verify` | 6-digit OTP via SMS (Twilio) → verify → JWT |
| **Google OAuth2** | `/auth/google/login` | Google ID token → server-side verify → JWT |

All three flows issue the **same JWT format** as `JwtService.generateToken(User)`.

---

## Phase 1: Foundation (Dependencies + Entity + Config)

### `pom.xml` — New dependencies
- `spring-boot-starter-mail` → JavaMailSender for email OTP
- `com.twilio.sdk:twilio:10.1.5` → Twilio SMS SDK
- `com.google.api-client:google-api-client:2.7.0` → Google ID token verification

### `User.java` — New fields added
| Field | Type | Purpose |
|-------|------|---------|
| `phone` | String (unique, nullable) | Phone number for SMS OTP users |
| `otpCode` | String (nullable) | Stored 6-digit OTP |
| `otpExpiry` | LocalDateTime (nullable) | OTP expiration timestamp |
| `otpRequestCount` | int (default 0) | Rate limit counter |
| `otpRequestWindowStart` | LocalDateTime (nullable) | Rate limit window start |
| `googleId` | String (unique, nullable) | Google OAuth subject ID |
| `authProvider` | AuthProvider enum (default LOCAL) | LOCAL or GOOGLE |
| `password` | changed to `nullable = true` | Supports passwordless users |

### `AuthProvider.java` — [NEW] Enum
- Values: `LOCAL`, `GOOGLE`

### `UserRepository.java` — New finder methods
- `findByEmail(String email)` → find user by email for Email OTP
- `findByPhone(String phone)` → find user by phone for Phone OTP
- `findByGoogleId(String googleId)` → find user by Google subject ID

### `application.yml` — New config blocks
- `spring.mail.*` → SMTP settings (host, port, username, password)
- `twilio.*` → account-sid, auth-token, phone-number
- `google.client-id` → Google OAuth client ID
- `otp.expiry-minutes` → OTP validity (default: 5 min)
- `otp.rate-limit.*` → max-requests (3) and window-minutes (10)

### `TwilioConfig.java` — [NEW] Config class
- `initTwilio()` → Initializes Twilio SDK on app startup with account credentials

---

## Phase 2: DTOs

### `OtpRequestDTO.java` — [NEW]
- `email` (String) — used by email OTP flow
- `phone` (String) — used by phone OTP flow

### `OtpVerifyDTO.java` — [NEW]
- `email` / `phone` (String) — identifier
- `otp` (String) — the 6-digit code to verify

### `GoogleAuthDTO.java` — [NEW]
- `idToken` (String) — Google ID token from frontend

---

## Phase 3: Service Layer

### `OtpService.java` — [NEW] Core OTP logic
| Function | Purpose |
|----------|---------|
| `generateOtp()` | Secure 6-digit random OTP using `SecureRandom` |
| `createOrUpdateUserForEmailOtp(email)` | Find-or-create user by email, attach OTP + expiry, enforce rate limit |
| `createOrUpdateUserForPhoneOtp(phone)` | Find-or-create user by phone, attach OTP + expiry, enforce rate limit |
| `verifyOtp(user, otpCode)` | Validate OTP code + check expiry + invalidate after use |
| `checkRateLimit(user)` | Enforce max 3 OTP requests per 10 min window per user |
| `resetOtp(user)` | Clear `otpCode` and `otpExpiry` after successful use |

### `EmailOtpService.java` — [NEW]
| Function | Purpose |
|----------|---------|
| `sendOtp(email)` | Calls `OtpService` to generate OTP, sends email via `JavaMailSender` |

### `SmsOtpService.java` — [NEW]
| Function | Purpose |
|----------|---------|
| `sendOtp(phone)` | Calls `OtpService` to generate OTP, sends SMS via `Twilio Message.creator()` |

### `GoogleOAuthService.java` — [NEW]
| Function | Purpose |
|----------|---------|
| `authenticateGoogleToken(idToken)` | Verifies Google ID token using `GoogleIdTokenVerifier`, finds-or-creates user by googleId (or links to existing email user), issues JWT |

---

## Phase 4: Controllers

### `EmailOtpController.java` — [NEW] `@RequestMapping("/auth/email-otp")`
| Endpoint | Function | Purpose |
|----------|----------|---------|
| `POST /auth/email-otp/send` | `sendOtp()` | Accept email, send OTP, return success/rate-limit-error |
| `POST /auth/email-otp/verify` | `verifyOtp()` | Accept email + OTP, verify, return JWT cookie + UserDTO |

### `PhoneOtpController.java` — [NEW] `@RequestMapping("/auth/phone-otp")`
| Endpoint | Function | Purpose |
|----------|----------|---------|
| `POST /auth/phone-otp/send` | `sendOtp()` | Accept phone, send OTP via SMS, return success/rate-limit-error |
| `POST /auth/phone-otp/verify` | `verifyOtp()` | Accept phone + OTP, verify, return JWT cookie + UserDTO |

### `GoogleAuthController.java` — [NEW] `@RequestMapping("/auth/google")`
| Endpoint | Function | Purpose |
|----------|----------|---------|
| `POST /auth/google/login` | `googleLogin()` | Accept Google ID token, verify, return JWT cookie + UserDTO |

---

## Phase 5: Existing Files Modified (Minimal)

### `CustomUserDetails.java` — 1-line change
- `loadUserByUsername()` → handles null password for OTP/Google users (uses empty string placeholder)

### `SecurityConfig.java` — No changes needed ✅
- Existing `/auth/**` permit-all rule already covers all new endpoints

### `AuthController.java` — No changes ✅
- `/auth/login` and `/auth/signup` remain untouched

---

## Required Environment Variables

```bash
# Email OTP (SMTP)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password

# Phone OTP (Twilio)
TWILIO_ACCOUNT_SID=your-account-sid
TWILIO_AUTH_TOKEN=your-auth-token
TWILIO_PHONE_NUMBER=+1234567890

# Google OAuth2
GOOGLE_CLIENT_ID=your-google-client-id

# Existing (unchanged)
SPRING_DATASOURCE_URL=jdbc:postgresql://...
DB_USER=...
DB_PASSWORD=...
JWT_SECRET=...
JWT_EXPIRATION=...
```

---

## Safety Features

- ✅ **OTP Rate Limiting**: Max 3 requests per 10 min per email/phone
- ✅ **OTP Invalidation**: OTP cleared from DB after successful verification
- ✅ **OTP Expiry**: 5-minute validity window
- ✅ **Secure Generation**: `SecureRandom` for cryptographically safe 6-digit OTPs
- ✅ **Google Token Verification**: Server-side verification via `GoogleIdTokenVerifier`
- ✅ **Account Linking**: Google login auto-links to existing email accounts
