# AURA OS — ChatGPT Plus token and identity validation

**Scope:** Host-testable cryptographic and parser boundary for the optional, user-owned ChatGPT plan/Codex allowance flow. No separate paid API key or client secret is involved.

## Security work

- `AuraStrictJson`: size/depth-limited JSON parsing; rejects duplicate keys, malformed numbers/escapes, invalid Unicode surrogate pairs and trailing content. Designed for host tests. **Must receive independent security review** before handling production identity credentials.
- `AuraChatGptStrictTokenDecoder`: only accepts the documented OAuth response keys and exact primitive types; does not coerce expiry fields, grant scopes, or bearer type.
- `AuraChatGptTokenHttpClient`: uses the strict decoder by default and rejects invalid UTF-8; HTTPS request body and response remain bounded and redirects disabled.
- `AuraChatGptRsaJwks`: converts RSA JWK modulus and exponent to RSA public keys. Only pass a JWKS response **previously authenticated as coming from the issuer-bound OpenAI OIDC discovery JWKS URL over TLS**; untrusted arbitrary JSON provides no authentication.
- `AuraChatGptRsaIdTokenVerifier`: validates JWS RS256 signature against a caller-provided *trusted* RSA signing key, checks `kid`, rejects untrusted JOSE header key URLs and unsupported algorithms, checks issuer/client audience/nonce/sub/exp/iat/nbf. A key must be at least 2048 bits.
- Negative host tests generate temporary RSA key pairs and reject forged signatures, unknown keys, header trickery, wrong issuer/audience, expired/future tokens, wrong nonce, duplicate JSON fields and malformed token payloads.

## Outstanding production gates

1. **Issuer-bound discovery and JWKS fetch:** query `https://auth.openai.com/.well-known/openid-configuration` using TLS, validate `issuer`, `jwks_uri` and HTTPS origin/redirect policy, then fetch a bounded JWKS document. Support keyed rotation and expiry without accepting attacker-supplied JWT URLs.
2. Independently review or replace the custom JSON/JWS boundary with an audited maintained JOSE/OIDC implementation available on the chosen AOSP build.
3. Add Android Keystore-backed encrypted durable SessionStore with compare-and-swap, recovery/rotation and per-user/profile separation; do not persist secrets in plaintext logs/backups.
4. Integrate the verified key store with `AuraChatGptSessionManager` and the OAuth browser flow only after Android emulator tests. The manager's default `AuraRejectUnverifiedIdTokens` remains fail-closed; no authenticated production session is enabled here.
5. Add strict real provider error handling and consent/revocation status. Permission `chatgpt.tokens.use.direct` and eligible allowance still must be checked before inference. Avoid any automatically paid fallback.
6. AOSP/Soong compilation, device network and multi-process Binder instrumentation remain unverified and are **not** part of these host tests.

## Acceptance

The Kotlin host tests and CI workflow cover the bounded decoder and JWS positive/negative cases, but **CI alone is not an authentication security sign-off**. Do not merge PR #13 into `develop` until the AOSP Binder probe has compiled and passed real emulator tests; a host-only stack can still be merged into that feature branch after green checks.

Reference: https://developers.openai.com/siwc/token-sharing-open-source/sign-in
Reference: https://developers.openai.com/siwc/token-sharing-open-source/token-reference
