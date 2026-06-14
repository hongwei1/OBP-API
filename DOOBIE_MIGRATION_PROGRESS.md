# Lift Mapper → Doobie Migration Progress

Branch: `lift-mapper-remove` → `hongwei1/OBP-API`
Last updated: 2026-06-14

---

## Phase A: Provider Layer Migration (done)

### Early phases 1–10 (business-entity providers)

| Module | Provider | Note |
|--------|----------|------|
| TransactionMetadata × 5 | DoobieTransactionMetadataProvider | narrative/tags/comments/wheretags/images |
| Counterparty × 4 | DoobieCounterpartyProvider | counterparty + metadata + wheretag + bespoke |
| Product | DoobieProductProvider | |
| TaxResidence | DoobieTaxResidenceProvider | async + FK JOIN to mappedcustomer |
| CustomerAddress | DoobieCustomerAddressProvider | reproduces Lift status-reads-mState bug |
| DirectDebit | DoobieDirectDebitProvider | |
| StandingOrder | DoobieStandingOrderProvider | preserves counterpartyid misspelling + whenDetail bug |
| CounterpartyLimit | DoobieCounterpartyLimitProvider | BigDecimal DECIMAL cols, upsert |
| ProductCollection | DoobieProductCollectionProvider | getOrCreate = delete-all-then-recreate |
| Attribute × 8 | DoobieXxxAttributeProvider | ProductAttribute/BankAttribute/Atm/Counterparty/User/RegulatedEntity/TxRequest/UserAuth |

### Core infrastructure

| Module | Provider | Note |
|--------|----------|------|
| MappedBank | DoobieBankProvider | getBankLegacy/getBanksLegacy/createOrUpdateBank |
| MappedBankAccount | DoobieBankAccountProvider | balance update re-reads from DB before delta (KEY: FREE_FORM self-transfer fix) |
| AuthUser login (Stage 1) | DoobieAuthUserProvider | bcrypt verify: `"b;" + hash.take(44)` / `hash.drop(44)`; login hot-path only |
| ResourceUser reads | DoobieResourceUserProvider | User-returning methods only; write path stays Lift |
| MapperViews reads | DoobieViewProvider + DoobieAccountAccessProvider | read + GRANT/REVOKE; VIEW CRUD stays Lift (109-col writes) |

### Parallel wave 1 (8 modules)

DynamicEndpoint, EndpointMapping, EndpointTag, ProductFee, UserAuthContextUpdate, Branches, BankAccountBalance, Agent

### Medium batch

CustomerMessage, AccountApplication, AccountIdMapping, KycCheck, KycDocument, KycMedia, KycStatus, ConsentAuthContext, ProductCollectionItem, RegulatedEntity

### Individual modules

| Module | Provider | Note |
|--------|----------|------|
| Challenge | DoobieChallengeProvider | BCrypt verify on answer; attempt counter + TTL |
| SigningBasket | DoobieSigningBasketProvider | 3 tables; soft-cancel to CANC |
| Entitlement | DoobieEntitlementsProvider | grantor permission check preserved verbatim |
| EntitlementRequest | DoobieEntitlementRequestsProvider | OBP query-param translation (LIMIT/OFFSET/date range) |
| Scope + UserScope | DoobieScopesProvider × 2 | |
| Group | DoobieGroupProvider | added GroupProviderTest (untested feature → provider-level test) |
| SocialMedia | DoobieSocialMediasProvider | nullable date params via Option[Timestamp] |
| RoutingScheme | DoobieRoutingSchemeProvider | RoutingSchemeSeed still reads MappedRoutingSchemeProvider directly (hardcoded, not vend) |
| Organisation | DoobieOrganisationProvider | |
| UserCustomerLink | DoobieUserCustomerLinkProvider | re-reads inserted row to guarantee field-identical equality for existing tests |
| CustomerAccountLink | DoobieCustomerAccountLinkProvider | |
| CustomerLink | DoobieCustomerLinkProvider | |
| PayeeLookup | DoobiePayeeLookupProvider | |
| AccountAccessRequest | DoobieAccountAccessRequestProvider | |
| UserAgreement | DoobieUserAgreementProvider | DATE col escaped → `date_c`; holder pattern |
| UserInvitation | DoobieUserInvitationProvider | holder pattern; scramble preserves field lengths |
| AttributeDefinition | DoobieAttributeDefinitionProvider | holder pattern; canBeSeenOnViews `;`-separated |
| UserRefreshes | DoobieUserRefreshesProvider | upsert; result discarded by all callers |
| Nonce | DoobieNonceProvider | only `deleteExpiredNonces` is live; others are dead |

### Parallel wave 2 (4 modules)

BulkPayment, ConsentRequest, Thing, PhysicalCard (24 cols + pinreset child one-to-many; CVV Sha256 on create)

### Parallel worktree wave (6 modules)

TransactionIdMapping, CustomerIdMapping, AccountWebhook, BankAccountNotificationWebhook, SystemAccountNotificationWebhook, CustomerDependants

### Final round

| Module | Provider | Note |
|--------|----------|------|
| Customer | DoobieCustomerProvider | `INSERT RETURNING id`; dobOfDependents via CustomerDependants.vend (same N+1 as Lift) |
| Consumer | DoobieConsumerProvider | Token FKs Consumer; rewired |
| Token | DoobieTokenProvider | generateVerifier mutates+persists |
| MappedTransactionRequest | DoobieTransactionRequestProvider | |

### Phase A session 2026-06-14 (6-task plan)

| Task | File | Change |
|------|------|--------|
| MappedMetrics.bulkDeleteMetrics | MappedMetrics.scala | `bulkDelete_!!` → `DELETE FROM metric` |
| LiftUsers.getUsersV600F batch | LiftUsers.scala | per-user Lift loops → Doobie IN batch (entitlements + agreements) |
| LiftUsers bulk/single deletes | LiftUsers.scala | `bulkDeleteAllResourceUsers` / `deleteResourceUser` → Doobie DELETE |
| DoobieConsentProvider lookup | DoobieConsentProvider.scala | `ResourceUser.findAll(By(...))` → `DoobieResourceUserProvider.findByProviderId` |
| MapperViews.removeCustomView/removeSystemView | MapperViews.scala | DELETE viewpermission + viewdefinition via Doobie; COUNT(*) for orphan check |
| LiftUsers.scrambleDataOfResourceUser | LiftUsers.scala | `AuthUser.find(By(...))` → `DoobieAuthUserProvider.findMetaByUserFk` |

---

## What remains on Lift (intentional, with rationale)

### Phase B — 1,819 Lift DSL calls across 199 files

`By` / `ByList` / `findAll` / `saveMe` / `delete_!` / `bulkDelete_!!` scattered throughout business logic.
Mechanical but large-scale; planned as a separate PR sweep.

### Phase C — 26 entity classes still extend Lift Mapper base classes

`LongKeyedMapper`, `MegaProtoUser`, etc. Requires full entity rewrites.

### Phase D — AuthUser / Boot.scala

| Remaining live call | Location | Why it stays |
|---------------------|----------|--------------|
| `AuthUser.create…saveMe()` (user registration) | Http4s200.scala | Two-table atomic creation (authuser + resourceuser); `sendValidationEmail` uses Lift Mailer |
| `AuthUser.grantDefaultEntitlementsToAuthUser` | Http4s200.scala | Takes `TheUserType` (Lift entity) |
| `AuthUser.refreshUser` | Http4s510, Http4s310, NewStyle | Updates views/account access; extractable to a service but not done yet |
| `AuthUser.refreshViewsAccountAccessAndHolders` | GatewayLogin.scala | Complex Lift view-management op |
| `AuthUser.scrambleAuthUser` / `validateAuthUser` | NewStyle.scala | Still use Lift entity lifecycle |
| `AfterApiAuth.sofitInitAction` | AfterApiAuth.scala | Needs full Lift AuthUser entity (.firstName, .lastName, .id.get) |
| `Http4sOpenIdConnect.createAuthUser` + grants | Http4sOpenIdConnect.scala | `grantEntitlementsToUseDynamicEndpointsInSpaces` takes AuthUser |

**Note**: `MegaProtoUser`'s web UI methods (login form, cookie, currentUser session) are all **dead code** since `Http4sLiftWebBridge` was removed. Only the helper methods listed above are live.

### MapperViews write path (kept on Lift)

`ViewDefinition.create…saveMe()` writes 109 columns via Lift's reflection-based `setFromViewData`. The `createViewAndPermissions()` method manages `ViewPermission` rows via a reflection loop. Rewriting this in Doobie would require a 109-field INSERT and a full reimplementation of the permission sync algorithm — not worth it given the coexistence phase. Affects: `createSystemView`, `createCustomView`, `updateCustomView`, `updateSystemView`, `factoryResetSystemView`, `createAndSaveSystemView`, `createAndSaveDefaultPublicCustomView`.

### getUsersCommon / getUserByEmail / getAllUsers (LiftUsers.scala)

`getUsersCommon` uses `diff` / `intersect` on `List[ResourceUser]`. A Doobie holder pattern would create non-equal objects, breaking set operations. Kept on Lift.

---

## Known patterns and gotchas

| Pattern | Detail |
|---------|--------|
| `nn(s)` null guard | `Put[String]` rejects raw null; always wrap with `if (s == null) "" else s` |
| Holder pattern | `Xxx.create.Field(v)…` (no `.saveMe()`): reuses Lift class as in-memory return; safe when callers never do `==` or `asInstanceOf` |
| Equality regression | Introducing a 2nd impl of `User` / `ResourceUser` broke 3 cast/equality sites; audit `asInstanceOf` + object `==` before adding any new impl |
| COUNT(*) | `.query[Int].unique` — COUNT always returns exactly 1 row; `.unique` is correct |
| Doobie IN clause | Build fragment: `ids.map(id => fr"$id").reduceLeft((a,b) => a ++ fr"," ++ b)` then `fr"WHERE col IN (" ++ inList ++ fr")"` |
| `isSystem_` column | Postgres is case-insensitive for unquoted identifiers; `isSystem_` and `issystem_` are equivalent |
| `date_c` / `key_c` / `success_c` | Lift escapes SQL-reserved words (DATE, KEY, SUCCESSFUL) by appending `_c` |
| ViewDefinition stays Lift entity | `View == ViewDefinition` at 4 `asInstanceOf[ViewDefinition]` sites; avoids new-impl equality regression |
| `RoutingSchemeSeed` bypass | Seeds via `MappedRoutingSchemeProvider` directly (hardcoded, not vend) — Lift writes seed, Doobie reads same table during coexistence |
| Phase D difficulty | Moderate; groundwork (read paths, password verify, validation/reset flow) already done in `DoobieAuthUserProvider`. Remaining hard parts: two-table atomic user creation, Lift Mailer dependency for email |

---

## Phase D feasibility assessment

Most of `DoobieAuthUserProvider` is already done (login, password verify, validation/reset flow, all reads). Phase D is **not easy but tractable**:

- **Done**: bcrypt verify, all read paths, email validation flow, password reset flow, uniqueid rotation
- **Remaining**: two-table `authuser + resourceuser` atomic INSERT (user registration), `sendValidationEmail` (Lift Mailer), `sofitInitAction` caller chain, `refreshUser` / `refreshViewsAccountAccessAndHolders`
- **Dead code** (no action needed): `MegaProtoUser` web UI (login form, cookie, `currentUser` session) — all unreachable since Lift bridge removed
- **Risk**: HIGH on registration path (auth entry point); LOW on `refreshUser` (extractable to a service)
- **Comparison**: more focused than Phase B (1,819 scattered calls), but higher risk per change
