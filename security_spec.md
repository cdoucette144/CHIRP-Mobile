# Security Specification (`security_spec.md`)

## 1. Data Invariants
1. **User Isolation (`zones`)**: Every zone document in `/zones/{zoneId}` is strictly owned by `userId == request.auth.uid`. Only the authenticated owner can read (`get`, `list`), create, update, or delete their zone documents.
2. **Remote Codeplug Sharing (`shared_codeplugs`)**: Every published codeplug in `/shared_codeplugs/{shareCode}` is authored by `userId == request.auth.uid`. Any authenticated user who knows the `shareCode` can `get` the shared codeplug to pull or sync remote updates, while `list`, `create`, `update`, and `delete` are strictly constrained to the authenticated owner (`userId == request.auth.uid`). No PII (email, phone, address) is stored in `shared_codeplugs`.
3. **Immutable Identity & Creation Timestamps**: Neither `userId` nor `createdAt` can be mutated on `update`.
4. **Bounded Channel Lists**: `channelSpecs` is a bounded `list` of up to 100 strings (each up to 256 characters), preventing resource exhaustion attacks.

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read (`zones`)**: Unauthenticated client requests `get` or `list` on `/zones`. -> Rejected (`isSignedIn()`).
2. **Cross-User Read (`zones`)**: User Alice attempts `get` on Bob's `/zones/bob_zone`. -> Rejected (`existing().userId == request.auth.uid`).
3. **Unfiltered List Query (`zones`)**: User Alice executes `db.collection("zones").get()` without `.where("userId", "==", aliceUid)`. -> Rejected (`allow list` requires `existing().userId == request.auth.uid`).
4. **Identity Spoofing on Create**: User Alice creates a zone with `userId: "bob_456"`. -> Rejected (`incoming().userId == request.auth.uid`).
5. **Shadow Update / Ghost Field Injection**: User Alice updates `/zones/alice_zone` adding `"isAdmin": true`. -> Rejected (`affectedKeys().hasOnly(...)` and `isValidZone(incoming())`).
6. **Value Poisoning on Update**: User Alice updates `name` to a 5,000-character string or integer `12345`. -> Rejected (`isValidZone(incoming())` enforces `data.name is string && data.name.size() <= 80`).
7. **Immutable Field Mutation (`createdAt`)**: User Alice updates `createdAt` on an existing zone. -> Rejected (`incoming().createdAt == existing().createdAt`).
8. **Immutable Owner Transfer (`userId`)**: User Alice updates `userId` on an existing zone to `"bob_456"`. -> Rejected (`incoming().userId == existing().userId`).
9. **Enum Bypass (`safetyProfile`)**: User Alice creates a zone with `safetyProfile: "UNLIMITED_POWER"`. -> Rejected (`data.safetyProfile in ['STANDARD_TX', 'RX_ONLY_INHIBIT', 'SAFE_TX_FALLBACK_FRS1']`).
10. **Array Overflow Attack (`channelSpecs`)**: User Alice creates a zone with 150 items in `channelSpecs`. -> Rejected (`data.channelSpecs.size() <= 100`).
11. **Cross-User Overwrite on Shared Codeplug (`shared_codeplugs`)**: User Bob attempts to `update` or `delete` Alice's `/shared_codeplugs/PLYM-PD-01`. -> Rejected (`existing().userId == request.auth.uid`).
12. **Unfiltered List on Shared Codeplugs (`shared_codeplugs`)**: User Alice runs `db.collection("shared_codeplugs").get()` without filtering by her `userId`. -> Rejected (`allow list: if isSignedIn() && existing().userId == request.auth.uid`).
