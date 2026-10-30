# AURA Phase 2 Permissions & SELinux Plan (draft)

**Do not ship or enable this until reviewed and tested against a pinned AOSP branch.**

## Android permission
Propose `com.aura.os.permission.ACCESS_AGENT_SERVICE` as a signature-level permission declared by the trusted platform package. Signature protection alone is not sufficient: Binder must also enforce allowlisted system caller identity, expected signing certificates, per-user access and fine-grained capabilities. Never trust `packageName` from a parcel without verifying it belongs to the calling UID.

## SELinux
- Draft dedicated `aura_agent_service` domain and service-manager type `aura_agent_service_type`.
- Limit `service_manager` registration to SystemServer or the selected trusted process after reviewing actual boot architecture.
- Only explicitly allow approved system components to `find` the service; constrain Binder calls by domain.
- No blanket `allow` permissions, unconfined domain, permissive exceptions, root/shell access, or broad filesystem privileges.
- Review policy against actual branch's service contexts, domain transitions and neverallow rules before enabling.
- Enforcing mode is a release requirement.

## Security review gates
1. Pin and build AOSP reference image.
2. Freeze AIDL serialization sizes and version compatibility.
3. Implement Binder caller validation and permission checks.
4. Implement one-shot, expiring grants tied to immutable arguments and trusted confirmation surface.
5. Review SELinux diff, run policy tests and verify enforcing mode on emulator.
6. Run negative authorization and replay tests before first privileged action execution.

No device flashing, OEM vendor blob handling or production permission changes are included in this branch.
