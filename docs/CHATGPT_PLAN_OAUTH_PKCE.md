# AURA OS: ChatGPT Plus / Codex subscription sign-in — PKCE milestone

**Status: source and host-run tests only.** Nothing in this PR authenticates an Android user, obtains OAuth tokens, calls OpenAI, starts an HTTP listener, stores secrets, or activates ChatGPT plan usage.

## Product contract

AURA must allow **Continue with ChatGPT (optional)** during first-run setup and in Settings. Eligible users should be able to use their **own ChatGPT plan's included AI allowance**, including supported Codex/agentic usage, within account eligibility and usage limits. No separately billed OpenAI API key/account should be required for this option. Requests use the OpenAI-documented plan-backed **Responses API protocol** with separately authorized OAuth access tokens; this is **not** generic API-key billing.

The user may skip, revoke, disconnect or hit their usage allowance without losing core Android functionality. Never silently switch to credits or a paid API key.

## What this PR actually implements

`AuraChatGptOAuthPkce` is a pure JVM/Android-compatible authorization-transaction boundary:
- SecureRandom creates a fresh PKCE verifier, OIDC nonce, and state for each attempt.
- An initial registration uses `dynamic_agent_client`, the stable `ext_agent_host_id`, `agent_name_hint=AURA OS`, required identity and plan scopes, `resource=https://api.openai.com/v1`, and PKCE `S256`.
- Returning registration uses the previously issued `oaiapp_...` client ID. Only the account identity determined by a *cryptographically verified* ID token may select or activate an account.
- Only `http://127.0.0.1:<port>/auth/callback` is accepted, with exact redirect URI matching. The listener must be bound before launching the system browser.
- State and callback are checked, duplicate parameters are rejected, and matching-state callbacks are consumed once, including `access_denied`.
- The authorization attempt expires in five minutes. Code, PKCE verifier, nonce and client ID are delivered solely to a **future** validated token-exchange component and are not included in logs or `toString()`.

The transaction rejects identity-only sign-ins as proof of ChatGPT plan consent. After token exchange, the future session layer must verify the returned **signed ID token** (JWKS, issuer, audience, expiry, nonce and account identity) and inspect the **granted**, not requested, `chatgpt.tokens.use.direct` scope. A verified identity *without* this scope must not grant inference. The existing `AuraChatGptPlanPolicy` blocks cloud inference without consent, a valid session and allowance.

## Remaining implementation gates (not done)

1. Confirm native Android handling of the OpenAI-documented HTTP loopback redirect and a secure ephemeral listener bound exclusively to `127.0.0.1`; a custom-scheme URI must not be silently substituted.
2. Integrate verified OAuth/OIDC library and OpenAI discovery/JWKS. Perform exchange and refresh, handle rotating refresh tokens atomically and store credentials via Android Keystore-backed encrypted storage, separated by account/client ID.
3. Persist a stable per-installation `ext_agent_host_id` without embedding a shared OAuth secret. Enforce Android user/profile separation.
4. Create first-run and Settings UI with *Skip*, *Connect*, *Disconnect*, visible account and clear data-transfer consent. Limit/background status should come from real provider responses; do not fabricate counters.
5. Add a plan-backed Responses stream adapter using `store:false` and `stream:true` under the documented authorization contract. No fallback to chargeable credits unless explicitly enabled by the user via OpenAI controls.
6. Keep AI outputs confined to proposed typed actions; Android Binder and user approval remain independent.
7. Validate the full flow on an AOSP reference emulator, including changed account, revoked consent, replay, callback hijack, token refresh and exhausted allowance.

## Automated validation

The framework CI invokes `AuraChatGptOAuthPkceTestKt`. It covers request parameters, PKCE SHA-256 derivation, initial registration, returning clients, forged state, wrong loopback host, duplicate parameters, replay, cancellation, expiry, and consent denial. These host tests are **not** proof of completed OAuth login or Android safety.

## Official references (checked 2026-11-02)

- https://developers.openai.com/siwc/token-sharing-open-source/sign-in
- https://developers.openai.com/siwc/token-sharing-open-source/token-reference
- https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery
- https://help.openai.com/en/articles/20001542-using-your-chatgpt-plan-in-other-apps-and-sites
