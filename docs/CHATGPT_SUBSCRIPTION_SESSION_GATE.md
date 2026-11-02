# AURA OS — ChatGPT Plus / Codex subscription session core

**Status:** OAuth token request/HTTPS transport and trusted-session coordinator source with host Kotlin tests. This is **not yet a working ChatGPT account connection**. No OAuth tokens have been exchanged with OpenAI in this milestone, and no user's account is accessed.

## Product intent
Users who have an eligible ChatGPT Plus subscription may optionally authorize AURA OS to use that subscription's supported **ChatGPT plan allowance**, including compatible Codex/agentic workloads. This is distinct from separately billed API-key usage. Eligibility, workspace selection, available models, allowance and per-app limits are controlled by OpenAI and cannot be inferred from subscription naming alone. OAuth identity does not grant permission to call the Responses API, and neither permission authorizes AURA Android actions.

## Implemented code boundaries
- `AuraChatGptTokenRequest.AuthorizationCode`: public-client `authorization_code` POST form, issued `oaiapp_...` client ID, same code/verifier/redirect URI, `resource=https://api.openai.com/v1`; **no client secret or API key**.
- `AuraChatGptTokenRequest.Refresh`: exact issued client ID, latest refresh token, resource and `grant_type=refresh_token`; omit `scope`.
- `AuraChatGptTokenHttpClient`: bounded, redirect-disabled, POST-only HTTPS transport with timeouts, content-type and size checks. JSON decoding is **injected**, not implemented by this class. Do not wire an unvalidated decoder.
- `AuraChatGptIdTokenVerifier` interface: a signed-ID-token validation contract. Production must enforce trusted JWKS signature and algorithm, `iss`, exact issued-client `aud`, expiration, nonce on new authorization, appropriate temporal claims, and subject. Default `AuraRejectUnverifiedIdTokens` always denies.
- `AuraChatGptSessionManager`: refuses unverified identity, separates accounts by issued client ID and Android user ID, enforces returning subject matching, requires atomic compare-and-swap refresh rotation, and never equates identity-only consent to plan-usage consent.
- `SessionStore` interface: intended for an encrypted, durable, transaction-safe Android backend. **No on-device credential store has been implemented.**

## What MUST be completed before a real account sign-in
1. Use an audited/maintained OIDC/JWS verifier wired to OpenAI discovery/JWKS endpoints; strictly validate signed ID token claims. Do not decode a JWT and trust claims without signature validation.
2. Integrate a strict Android JSON decoder for token responses, including correct handling of malformed responses, duplicate keys, types, size limits, `earliest_refresh_at`, and provider error status.
3. Persist credentials with an Android Keystore-protected encrypted store, per Android user/profile and account/client ID; ensure atomic refresh-token rotation and no plaintext logs/backups.
4. Wire the official browser callback, token exchange and OIDC verification into a lifecycle-aware Android setup screen, with explicit plan usage consent, account switching and disconnect/revocation.
5. Implement retry/backoff and typed recovery for refresh errors; only clear unusable credentials after confirmed terminal errors.
6. Check granted `chatgpt.tokens.use.direct` scope, session validity, plan usage availability and the actual signed-in workspace before inference. Do not invent usage counters.
7. Run actual AOSP/Soong compile and Android emulator network and cross-user security tests. No privileges or AURA SystemServer Binder registration are authorized by this feature.

## Test evidence
`AuraChatGptSessionManagerTest` uses a **fake, already-verified identity implementation and a test-only in-memory store**. It tests request field shape, missing secret/API key, scope checks, account swapping, Android-user separation, concurrency rejection, refresh token rotation, expiry scheduling and the *default* fail-closed verifier. These are host behavioral tests, **not cryptographic JWT tests, network OAuth tests, or Keystore instrumentation**.

## Official protocol references (checked 2026-11-02)
- https://developers.openai.com/siwc/token-sharing-open-source/sign-in
- https://developers.openai.com/siwc/token-sharing-open-source/token-reference
- https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions
- https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery
