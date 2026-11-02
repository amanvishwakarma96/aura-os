# ADR-005: Optional ChatGPT Plus / Codex-plan assistant for AURA OS

**Decision:** Users may optionally connect their **own ChatGPT account and eligible subscription allowance** to AURA OS. The intended first scenario is a ChatGPT Plus subscriber who already uses Codex. AURA will not require a separate OpenAI API key or an AURA-paid API subscription for this option.

## Licensing, not generic API billing
- This proposal targets OpenAI's **Sign in with ChatGPT / ChatGPT plan usage** flow for open-source clients. The user signs in to their ChatGPT account and **separately consents** to use the eligible ChatGPT Work/Codex plan allowance.
- Eligible plan-backed inference is carried over OpenAI's documented Responses endpoint using the specifically authorized OAuth access token. Although the transport has an API shape, it is **not** billed through a separately supplied API key.
- This is **not** unlimited: availability, selected model, eligibility, reset windows, user-configured per-app weekly limits, and permitted credits are managed by OpenAI. AURA must not promise a fixed number of requests.
- Additional credits must never be used unless the user has explicitly opted in through the supported OpenAI controls. No silent fallback to another chargeable account.
- Identity-only OAuth sign-in is not permission to use subscription allowance. Require the **granted**, not merely requested, `chatgpt.tokens.use.direct` scope and valid session before plan-based inference.
- An open-source implementation must follow OpenAI's approved client registration, stable per-installation `ext_agent_host_id`, OAuth Authorization Code + PKCE, token refresh, ID token verification, and secure token storage. No password scraping, private ChatGPT session cookies, undocumented Codex endpoints, or subscription credential reuse.
- ChatGPT conversations/memories/files are **not** shared simply by signing in. User data to be sent from Android requires separate AURA data disclosure and consent.

## AURA OS first-run setup
1. Finish standard Android setup; the OS must remain functional without ChatGPT.
2. Optional step: **Connect ChatGPT (use your existing subscription)**, with **Skip for now** and local AI options.
3. Explain that supported AI uses **the user's existing plan allowance**, including Codex/agentic limits; show current account eligibility and consent as reported by OpenAI, not guessed by AURA.
4. After successful consent, show Connected, available features, **Manage limits in ChatGPT Settings > Usage**, and Disconnect.
5. If consent is declined, revoked, session expired, offline, or limit reached, show clear status and retain local assistant/device functionality.

## Agent model
AURA's system assistant and action registry remain independent of AI provider. A ChatGPT-plan-backed provider is a **planner/conversational intelligence**, optionally with Codex app-server orchestration **only in a supported host environment**. Official Codex app-server documentation describes a child process receiving an OAuth access token and using the Responses provider; native Android availability, binary packaging, lifecycle, and sandboxing must be investigated and validated. **Do not claim stock Codex CLI or Codex app-server runs as a privileged Android service.**

Architecture:
```
AURA Setup / Settings
  -> user-owned Sign in with ChatGPT + separate plan usage consent
  -> OAuth PKCE, identity validation, encrypted token store (future)
  -> optional ChatGPT plan adapter or supported Codex app-server host (future)
  -> AI proposes typed actions
  -> existing AURA Action Registry / Policy / Approval / Binder identity
  -> authorized OS action only after OS policy permits it
```
Never allow a model to authorize its own Android privileged operations. No auto-granting microphone, notifications, contacts, payments, app installs or device admin permission. The user can disconnect ChatGPT and disable cloud processing at any time.

## Scope implemented in this PR
Only `AuraChatGptPlanPolicy` and host smoke tests (fail-closed provider readiness), plus design documentation. No OAuth client, OpenAI request, Codex binary, setup screen, credentials, subscription entitlements, or Android privileges are implemented.

Official references:
- https://developers.openai.com/siwc/quickstart
- https://developers.openai.com/siwc/token-sharing-open-source
- https://developers.openai.com/siwc/token-sharing-open-source/codex-app-server
- https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery
- https://help.openai.com/en/articles/20001542-using-your-chatgpt-plan-in-other-apps-and-sites
