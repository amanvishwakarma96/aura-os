# AURA OS — ChatGPT Plus OAuth loopback callback transport

**Scope:** An isolated, JVM/Android-compatible **socket callback transport** for the OpenAI Sign in with ChatGPT OAuth+PKCE flow. This is not a production Android login feature. The goal remains user-owned, eligible **ChatGPT Plus / Codex plan allowance**, not separately billed OpenAI API-key inference.

## Security properties implemented
- Bind an ephemeral TCP port to IPv4 `127.0.0.1` only, before constructing the PKCE authorization request.
- Construct an exact `http://127.0.0.1:<ephemeral-port>/auth/callback` redirect for the OAuth request.
- Accept exactly one incoming HTTP/1.1 GET request on a background thread; no public network listener.
- Reject invalid Host, method, path, oversized or incomplete headers, forged state and OAuth callback problems.
- Hand the callback to the existing single-use `AuraChatGptOAuthPkce.complete` state/nonce/PKCE boundary.
- Return only a neutral browser status page with no code, state, nonce, token or verifier. Set `no-store`, `no-referrer`, `nosniff` and a restrictive Content-Security-Policy.
- Close the socket after the first request or failure; subsequent callbacks cannot reuse the listener.
- Time out rather than wait forever. The caller must close and cancel on setup cancellation.

## Future Android UI integration (NOT implemented)
1. Only after the user explicitly taps **Continue with ChatGPT**, create `AuraChatGptLoopbackListener` on a background-capable Android component.
2. Persist/reuse the installation's approved opaque host ID, call `AuraChatGptOAuthPkce.begin(hostId, listener.redirectUri)`, then launch its URL in the user's trusted system browser (not an embedded WebView).
3. On a worker thread, call `listener.awaitCallback(transaction)`; do not block the Android main thread.
4. Only on `CallbackResult.Exchange`, send the code, issued client ID, original redirect URI, and PKCE verifier to the documented token endpoint using TLS and a public-client grant.
5. Validate the signed ID token against OpenAI's OIDC JWKS, issuer, exact client audience, expiry and nonce **before** installing/refreshing any user session. Independently check granted `chatgpt.tokens.use.direct` scope.
6. Protect credentials using Android Keystore-backed storage; ensure user/profile separation, rotating refresh-token atomicity, disconnect and logout/revocation.
7. Show explicit allowance/consent state and never silently switch to separately billable inference.

## Limitations and acceptance gate
These new tests open an **actual local TCP socket** and check callback behavior; they do not authenticate with OpenAI, launch an Android browser, run on an emulator, exchange/refresh tokens, or verify JWT signatures. A localhost browser callback can potentially be reached by other apps or web pages: the unpredictable state/PKCE values and one-shot listener are mandatory safeguards; further Android threat modeling and instrumentation are required. An invalid first request intentionally consumes the listener to fail closed (it may cause a denial-of-service requiring retry).

Do not integrate the loopback into `SystemServer` or grant any AURA device action based on OAuth alone. ChatGPT plan usage remains optional and subject to eligibility, user consent, permitted app limits, and the published Sign in with ChatGPT contract.

Official references:
- https://developers.openai.com/siwc/token-sharing-open-source/sign-in
- https://developers.openai.com/siwc/token-sharing-open-source/token-reference
- https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions
