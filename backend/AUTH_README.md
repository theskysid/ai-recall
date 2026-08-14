# 🔐 Echo Messaging — Multi-Auth Implementation Guide

## Overview

Two new authentication flows added **alongside** existing `/auth/login` and `/auth/signup`:

| Flow | Endpoints | How it works |
|------|-----------|--------------|
| **Email OTP** | `/auth/email-otp/send`, `/auth/email-otp/verify` | 6-digit OTP via email → verify → JWT |
| **Google OAuth2** | `/auth/google/login` | Google ID token → server-side verify → JWT |

Both flows issue the **same JWT format** as `JwtService.generateToken(User)`.

---

## Phase 1: Foundation (Dependencies + Entity + Config)

### `pom.xml` — New dependencies
- `spring-boot-starter-mail` → JavaMailSender for email OTP
- `com.google.api-client:google-api-client:2.7.0` → Google ID token verification

### `User.java` — New fields added
| Field | Type | Purpose |
|-------|------|---------|
| `googleId` | String (unique, nullable) | Google OAuth subject ID |
| `authProvider` | AuthProvider enum (not null) | `EMAIL` or `GOOGLE` |
| `password` | String (nullable) | Null for passwordless (Google) users |

OTP state is **not** stored on `User`. It lives in its own entity:

### `OtpVerification.java` — [NEW] Entity (`otp_verifications`)
| Field | Type | Purpose |
|-------|------|---------|
| `identifier` | String (not null) | The email the OTP was issued for |
| `type` | IdentifierType enum | `EMAIL` (`PHONE` retained only for legacy rows) |
| `otpCode` | String (not null) | The 6-digit code |
| `expiry` | LocalDateTime (not null) | OTP expiration timestamp |
| `requestCount` | int (not null) | Rate limit counter |
| `windowStart` | LocalDateTime (not null) | Rate limit window start |

### `OtpVerificationRepository.java` — [NEW]
- `findByIdentifierAndType(String identifier, IdentifierType type)` → the pending OTP row, if any

### `AuthProvider.java` — [NEW] Enum
- Values: `GOOGLE`, `EMAIL` (plus a legacy `PHONE` constant kept only for old rows)
- Email/password signup stores `EMAIL`

### `UserRepository.java` — New finder methods
- `findByEmailIgnoreCase(String email)` → used by the email OTP and signup paths
- `findByUsernameIgnoreCase(String username)` → used by login and the JWT-authenticated lookups
- `findByGoogleId(String googleId)` → find user by Google subject ID

### `application.yml` — New config blocks
- `spring.mail.*` → SMTP settings (host, port, username, password)
- `google.client-id` → Google OAuth client ID
- `otp.expiry-minutes` → OTP validity (default: 5 min)
- `otp.rate-limit.*` → max-requests (3) and window-minutes (10)

---

## Phase 2: DTOs

### `OtpRequestDTO.java` — [NEW]
- `email` (String) — the address to send the OTP to

### `OtpVerifyDTO.java` — [NEW]
- `email` (String) — identifier
- `otp` (String) — the 6-digit code to verify

### `SignupOtpRequestDTO.java` — [NEW]
- `username`, `identifier` (email), `password`, `otp` — the OTP-verified signup body

### `GoogleAuthDTO.java` — [NEW]
- `idToken` (String) — Google ID token from frontend

---

## Phase 3: Service Layer

### `OtpService.java` — [NEW] Core OTP logic
| Function | Purpose |
|----------|---------|
| `generateOtp()` | Secure 6-digit random OTP using `SecureRandom` |
| `createOrUpdateForEmail(email)` | Upsert the `otp_verifications` row for the email, attach OTP + expiry, enforce rate limit, return the code |
| `verifyOtp(identifier, type, otpCode)` | Validate OTP code + check expiry, then **delete** the row (expired rows are deleted too) |
| `checkRateLimit(record)` | Private — max 3 OTP requests per 10 min window per identifier |

> Sending an OTP does **not** create an account. `/auth/email-otp/verify` is
> login-only: an unknown email fails in `AuthenticationService.loginWithOtp`
> with `No account linked to this email`. New accounts go through
> `/auth/signup/verify`.

### `EmailOtpService.java` — [NEW]
| Function | Purpose |
|----------|---------|
| `sendOtp(email)` | Calls `OtpService` to generate OTP, sends email via `JavaMailSender` |

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

### `GoogleAuthController.java` — [NEW] `@RequestMapping("/auth/google")`
| Endpoint | Function | Purpose |
|----------|----------|---------|
| `POST /auth/google/login` | `googleLogin()` | Accept Google ID token, verify, return JWT cookie + UserDTO |

---

## Phase 5: Existing Files Modified (Minimal)

### `CustomUserDetails.java` — 1-line change
- `loadUserByUsername()` → handles null password for OTP/Google users (uses empty string placeholder)

### `SecurityConfig.java` — permit-all list
There is no `/auth/**` wildcard. Exactly these are `permitAll`:

- `/auth/login`
- `/auth/signup`
- `/auth/signup/verify`
- `/auth/email-otp/**`
- `/auth/google/**`

Everything else falls through to `.anyRequest().authenticated()`, so
`/auth/logout`, `/auth/getcurrentuser` and `/auth/getonlineusers` all require a
valid JWT.

### `AuthController.java` — additional endpoints
| Endpoint | Purpose |
|----------|---------|
| `POST /auth/signup/verify` | OTP-gated signup: `/auth/email-otp/send` first, then post `username` + `identifier` + `password` + `otp` → `AuthenticationService.signupWithOtp` → JWT cookie + UserDTO |
| `POST /auth/logout` | Clears the `JWT` cookie (authenticated) |
| `GET /auth/getonlineusers` | Usernames currently connected (authenticated) |
| `GET /auth/getcurrentuser` | UserDTO for the caller (authenticated) |

`/auth/signup` and `/auth/login` also normalize the username and reject
whitespace in it.

---

## Token / cookie handling

- `JwtAuthenticationFilter` accepts either an `Authorization: Bearer <token>`
  header or the httpOnly `JWT` cookie — header first, cookie as fallback.
- The user is resolved from the token's `userId` claim, not the subject.
- The cookie's `secure` flag comes from `app.secure-cookie`. Note the default
  differs by class: `false` in `AuthController`, `true` in
  `AuthenticationService`, `EmailOtpController` and `GoogleAuthController` — set
  it explicitly rather than relying on the default.
- `SameSite` differs per flow: `Lax` for `/auth/login`, `/auth/signup/verify`
  and logout; `Strict` for email-OTP and Google login.

---

## Required Environment Variables

```bash
# Email OTP (SMTP)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password

# Google OAuth2
GOOGLE_CLIENT_ID=your-google-client-id

# Existing (unchanged)
SPRING_DATASOURCE_URL=jdbc:postgresql://...
DB_USER=...
DB_PASSWORD=...
JWT_SECRET=...
JWT_EXPIRATION=...
ALLOWED_ORIGINS=http://localhost:5173   # CORS + credentialed cookie flows
```

`app.secure-cookie` is a Spring property (not in `.env.example`) — set it to
`true` behind HTTPS.

---

## Safety Features

- ✅ **OTP Rate Limiting**: Max 3 requests per 10 min per email address
- ✅ **OTP Invalidation**: OTP cleared from DB after successful verification
- ✅ **OTP Expiry**: 5-minute validity window
- ✅ **Secure Generation**: `SecureRandom` for cryptographically safe 6-digit OTPs
- ✅ **Google Token Verification**: Server-side verification via `GoogleIdTokenVerifier`
- ✅ **Account Linking**: Google login auto-links to existing email accounts
