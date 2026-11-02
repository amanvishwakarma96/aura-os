# AURA OS — issuer-pinned OpenAI OIDC discovery and JWKS verification

**Milestone:** source-level issuer trust and bounded key rotation for optional, eligible ChatGPT Plus / Codex plan-backed AI requests. Does **not** use a separately paid OpenAI API key.

## Previous-work verification

At the start of this milestone, PR #17 and its parent feature branch had three completed successful GitHub checks. Previous host tests covered local signature verification against supplied RSA keys, signed JWT claim checks and strict token-response JSON decoding. However, **a cryptographic signature is not proof of identity if the signing key's origin is not authenticated**. The prior verifier deliberately required a trusted key provider. This PR introduces a production pinned-key provider, but does not connect to a real ChatGPT account.

## Trust chain implemented

1. `AuraPinnedHttpsOidcFetcher` permits only two *exact* production OpenAI URLs, using TLS validation and no redirects:
   - `https://auth.openai.com/.well-known/openid-configuration`
   - `https://auth.openai.com/.well-known/jwks.json`
2. JSON discovery metadata must contain `issuer: https://auth.openai.com` and `jwks_uri: https://auth.openai.com/.well-known/jwks.json` **exactly**, before any key data is interpreted.
3. A bounded strict JSON/JWKS parser validates the returned RSA signing keys and their signature-purpose metadata. No key or URL can come from a JWT header (`jku`, `jwk` and similar untrusted parameters remain prohibited).
4. `AuraChatGptTrustedIssuerKeys.production()` creates the pinned HTTPS key source. `verifier()` delegates to the prior RS256 signature and issuer/client audience/nonce/expiry checks.
5. Signing keys are cached for at most ten minutes. Unknown key IDs trigger at most one forced source refresh every 30 seconds. Expired cached keys are not reused when the network fails. All replacements are published atomically after validation.
6. Signed ID-token header and claim JSON now reject malformed UTF-8 instead of replacing undecodable bytes.

## Verified scope

Host tests use **generated temporary RSA key pairs and injected offline discovery/JWKS documents**. They check valid signature verification through the key source, cache reuse and rotation, unknown key denial, stale-key denial on simulated network failure, malicious discovery metadata, duplicate JSON properties and attempts to fetch a different HTTPS host or an insecure HTTP URL.

The hosted Kotlin test does **not** make a real OpenAI HTTPS request, prove actual key rotation, or validate any user's ChatGPT account. The production fetcher has not been exercised on an Android emulator. It relies on the device's system trusted TLS store and standard HTTPS hostname checks.

## Remaining activation gates

- Security-review the homegrown JSON, JWKS and JWS code against a maintained OIDC/JOSE implementation on the selected AOSP branch.
- Build and test a **Keystore-backed, encrypted**, durable, per-Android-user credential SessionStore with crash-consistent atomic refresh-token rotation. No plaintext SharedPreferences, logs, backups or credential URLs.
- Implement the Android setup screen and OAuth browser lifecycle, live code exchange and error/revocation recovery.
- Connect to real OpenAI OIDC only after approved test registration and secure profile isolation, with explicit user plan-usage consent and no automated paid fallback.
- Obtain an AOSP/Soong build and true Cuttlefish multi-process Binder instrumentation evidence before merging the parent PR #13 into `develop`.

## Protocol references
- https://developers.openai.com/siwc/website (production issuer, discovery and JWKS URL)
- https://developers.openai.com/siwc/token-sharing-open-source/sign-in
- https://developers.openai.com/siwc/token-sharing-open-source/token-reference
