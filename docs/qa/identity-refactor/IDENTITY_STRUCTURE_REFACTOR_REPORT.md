# Identity Structure Refactor — Report

Date: 2026-10-10
Repository: `C:\Users\FPTSHOP\Desktop\StyleMind_BE`
Branch / starting HEAD: `dev` / `a924e8d`

## Scope and preserved state

Reorganized Identity into layer-first, feature-grouped packages and extracted the eight business services into public interfaces with one `@Service` implementation each. Preserved Controller → Service → Repository and Spring JDBC.

Starting `git status` had no tracked changes. Existing untracked `docs/analysis/` and eight raw audit `.tmp` files were preserved without reading their contents. No checkout/reset/clean/commit/push. Temporary scripts and Maven logs are under ignored `target/identity-refactor/`.

No V1–V3, database, Compose, workbook, Postman collection/environment, `.env`, credentials/keys, Notification source, or other service source changed. Workbook not read. No persistent database or Docker resource used.

## Package layout

Base package remains `com.stylemind.identity`.

`client.notification.payload` contains only the internal email-dispatch values accepted by the Notification adapter. These are neither persistence/domain models nor public HTTP DTOs.

```text
client/notification/payload
controller/{auth,profile,address,users,jwks}
directory, errors, logging, openapi, security
dto/{register,emailverification,login,session,passwordreset,profile,address,users}
model/{account,register,emailverification,login,session,passwordreset,profile,address,users}
repository/{account,emailverification,session,passwordreset,profile,address,users}
service/{register,emailverification,login,session,passwordreset,profile,address,users}/{impl,internal}
validation/{common,auth,profile,address,users}
```

### Complete moved-type map

| Old package | Classes | New package |
| --- | --- | --- |
| controller | EmailVerificationController, LoginController, PasswordResetController, RefreshSessionController, RegisterController | controller.auth |
| controller | ManageProfileController | controller.profile |
| controller | LocationLookupController, ManageAddressController | controller.address |
| controller | ManageUsersController | controller.users |
| controller | JwksController | controller.jwks |
| dto | RegisterRequest, RegisterResponse | dto.register |
| dto | ResendEmailVerificationRequest, VerifyEmailRequest | dto.emailverification |
| dto | LoginRequest, LoginResponse | dto.login |
| dto | RefreshTokenRequest | dto.session |
| dto | ForgotPasswordRequest, ForgotPasswordResponse, ResetPasswordRequest, ResetTokenResponse, VerifyPasswordResetRequest | dto.passwordreset |
| dto | MyProfileResponse, PatchMyProfileRequest, ReplaceStyleProfileRequest, StyleProfileResponse | dto.profile |
| dto | AddressListResponse, AddressResponse, ProvinceListResponse, ProvinceResponse, WardListResponse, WardResponse | dto.address |
| dto | ManageUsersPageResponse, ManagedUserResponse, ManagedUserRolesResponse, ManagedUserStatusResponse | dto.users |
| model | Account, AccountState | model.account |
| model | RegistrationCommit | model.register |
| model | EmailVerificationChallenge, ResendCommit, VerificationOutcome | model.emailverification |
| model | IssuedLoginTokens, LoginAccount | model.login |
| model | RefreshSession | model.session |
| model | PasswordResetAccount, PasswordResetChallenge, PasswordResetVerification | model.passwordreset |
| model | MyProfileSnapshot, StyleProfileCommand, StyleProfileSnapshot | model.profile |
| model | AddressPatch, AddressSnapshot, AddressValues | model.address |
| model | AdminManagedAccountState, ManageUserListCriteria, ManagedUserPageData, ManagedUserSummary | model.users |
| repository | AccountRepository | repository.account |
| repository | EmailVerificationRepository | repository.emailverification |
| repository | RefreshSessionRepository | repository.session |
| repository | PasswordResetRepository | repository.passwordreset |
| repository | ProfileRepository | repository.profile |
| repository | AddressRepository | repository.address |
| repository | ManageUsersRepository | repository.users |
| service | EmailDispatch, PasswordResetEmailDispatch | client.notification.payload |
| service | NotificationEmailClient | client.notification |
| service | EmailVerificationPersistence | service.emailverification.internal |
| service | LoginSessionService | service.login.internal |
| service | RefreshSessionTransactionService | service.session.internal |
| service | PasswordResetPersistence, PasswordResetTransactionService | service.passwordreset.internal |
| service | ManageProfilePersistenceService | service.profile.internal |
| service | ManageAddressPersistenceService | service.address.internal |
| service | JwtTokenService | security |
| validation | MaxUtf8Bytes, MaxUtf8BytesValidator | validation.common |
| validation | PasswordPolicy, PasswordPolicyValidator, PasswordResetTokenFormat, PasswordResetTokenFormatConstraint, PasswordResetTokenFormatValidator, RefreshTokenFormat, RefreshTokenFormatConstraint, RefreshTokenFormatValidator | validation.auth |
| validation | ProfileRequestValidator, StrictJsonDecimalDeserializer | validation.profile |
| validation | AddressRequestValidator, VietnamPhoneValidator | validation.address |
| validation | ManageUsersRequestValidator | validation.users |

Unmoved infrastructure: IdentityServiceApplication; VietnamAdministrativeDirectory; all existing errors, logging, and OpenAPI classes; and the existing security classes ApiAccessDeniedHandler, ApiAuthenticationEntryPoint, ApiProblemDetails, IdentityVerificationConfiguration, JwtDecoderConfiguration, JwtKeyMaterial, JwtProperties, JwtSigningConfiguration, PasswordEncodingConfiguration, PasswordResetCryptography, SecurityConfiguration, VerificationCodeService.

### Service interfaces and implementations

| Interface | Sole implementation |
| --- | --- |
| service.register.RegisterService | service.register.impl.RegisterServiceImpl |
| service.emailverification.EmailVerificationService | service.emailverification.impl.EmailVerificationServiceImpl |
| service.login.LoginService | service.login.impl.LoginServiceImpl |
| service.session.RefreshSessionService | service.session.impl.RefreshSessionServiceImpl |
| service.passwordreset.PasswordResetService | service.passwordreset.impl.PasswordResetServiceImpl |
| service.profile.ManageProfileService | service.profile.impl.ManageProfileServiceImpl |
| service.address.ManageAddressService | service.address.impl.ManageAddressServiceImpl |
| service.users.ManageUsersService | service.users.impl.ManageUsersServiceImpl |

Controllers inject interfaces. No operation was added; LoginResponse remains shared with refresh.

## Behavior and transaction preservation

Against starting HEAD, **116/116 non-business production class bodies** were identical after stripping only package/import lines. All eight service implementation bodies were identical after normalizing only the required class/constructor rename and implements clause. No SQL, validation, token/OTP lifecycle, lock order, ownership, business branching, or transaction boundary was edited.

The added IdentityServiceApplicationTests.businessServicesHaveSingleInterfaceBeansAndTransactionsRemainProxied test passed. It verifies one bean per service interface and AOP proxies for each existing transactional collaborator.

| Bean after move | Existing transactional methods |
| --- | --- |
| EmailVerificationPersistence | createRegistration, verify, reserveResend — default |
| LoginSessionService | createSession — default |
| RefreshSessionTransactionService | rotate, revoke — default |
| PasswordResetPersistence | reserveOtp, verifyOtp — default |
| PasswordResetTransactionService | reset — default |
| ManageProfilePersistenceService | getMyProfile/getStyleProfile — read-only REPEATABLE_READ; updateMyProfile/replaceStyleProfile — default |
| ManageAddressPersistenceService | list/get — read-only; create/patch/setDefault/delete — default |
| ManageUsersServiceImpl | list — READ_COMMITTED; updateStatus/replaceRoles — default |

Default propagation/rollback rules are unchanged. Helpers remain separate proxied beans; no self-invocation was introduced.

HTTP route inventory remains unchanged: Auth POST register/verify-email/resend-email-verification/login/refresh/logout/forgot-password/verify-password-reset/reset-password under /api/v1/auth; JWKS GET /.well-known/jwks.json; Profile GET/PATCH /api/v1/me and GET/PUT /api/v1/me/style-profile; Address GET/POST collection, GET/PATCH item, PUT item/default, DELETE item, plus province/ward GET lookups; Users GET list, PATCH status, PUT roles under /api/v1/admin/users. Controller and DTO source bodies/annotations are unchanged. Statuses, JSON fields, headers, Problem Details, security and OpenAPI response metadata remain as before. Existing HTTP tests cover security/error behavior and /v3/api-docs. No application ApiResponse wrapper/advice was added.

## Files and Postman

Moved/repackaged the mapped Identity production types; added eight interfaces; renamed/moved eight implementations; updated Identity test imports and added one wiring/proxy test. Updated identity-service/README.md and docs/architecture/SERVICE_ARCHITECTURE_AND_LAYOUT.md. Added this report.

The four existing Postman collections and two example environments are unchanged. Structural check: all JSON parsed as Collection v2.1/environment files; 221 requests found and 407 embedded scripts parsed successfully. This is structural validation only; no HTTP collection runtime was run.

## Verification

Baseline:
- Identity `mvn -B -ntp -f identity-service/pom.xml verify`: 60 tests, 0 failures, 7 errors, 0 skipped; all 7 were Docker/Testcontainers startup errors.
- Notification verify: 23 tests, 0 failures/errors/skips, BUILD SUCCESS.

After refactor:
- Identity compile: BUILD SUCCESS; test-compile completed successfully.
- Non-Postgres Identity suite: 53 tests, 0 failures/errors/skips, BUILD SUCCESS (before the added wiring assertion).
- IdentityServiceApplicationTests: 19 tests, 0 failures/errors/skips, BUILD SUCCESS, including the new wiring/proxy assertion.
- Identity full verify run 1: 61 tests, 0 failures, 7 errors, 0 skipped, BUILD FAILURE.
- Identity full verify run 2: same 61 / 0 / 7 / 0 outcome.
- Notification full verify: 23 tests, 0 failures/errors/skips, BUILD SUCCESS.
- `git diff --check` passed; no stale flat-package imports or controller-to-implementation imports remain.

Run logs (ignored, retained separately): `target/identity-refactor/baseline-identity-01-mvn-verify.log`, `baseline-notification-01-mvn-verify.log`, `post-wiring-context-test.log`, `post-identity-01-mvn-verify.log`, `post-identity-02-mvn-verify.log`, `post-notification-mvn-verify.log`.

## Migration and runtime boundary

V1–V3 SHA-256 values are identical before/after:

| Migration | SHA-256 |
| --- | --- |
| V1 | 3BB2316BAAF43FEF5A8C9B3B261DA4ADA2BE25D9253C69A42379DF71E82B5CAB |
| V2 | 68DB77E1629D27F9E21AB90D9E22200560A405A4D4EB4BC02049A38882699FC6 |
| V3 | 4D01B554F3474C56A736F9E1906D0BFC516E19C196655291DA65BE63AD314A51 |

Docker client existed, but Docker Engine was unavailable because //./pipe/dockerDesktopLinuxEngine did not exist. Read-only docker info and docker ps -a failed on that pipe. No container, volume, database, or external SMTP was used. Testcontainers and Postman runtime therefore remain unverified; they are not passes. Workbook was not read or hashed.

## Follow-up — Notification payload package naming

The generic `client.notification.model` package was renamed to `client.notification.payload`. `EmailDispatch` and `PasswordResetEmailDispatch` remain private Notification integration values; only their package declarations/imports changed. No endpoint, serialization contract, schema, transaction or business behavior changed. The Identity README, architecture tree and this report now document the package explicitly.

Verification after this package-only change:

| Service | `mvn verify` result |
| --- | --- |
| Identity | 61 tests: 0 failures, 7 Testcontainers errors, 0 skipped; Docker environment unavailable |
| Notification | 23 tests passed |
| Commerce | 13 tests passed |
| Payment | 13 tests passed |
| Wardrobe Community | 13 tests passed |
| Stylist | 13 tests passed |

Focused payload-flow check: `mvn -B -ntp -f identity-service/pom.xml '-Dtest=NotificationEmailClientTests,RegisterServiceTests' test` — 8 tests passed, 0 failures/errors/skips. `git diff --check` passed and no Java source/test imports or package declarations use `client.notification.model`.

## Conclusion

No refactor-related behavior difference was found in source comparisons or runnable tests. Docker-backed database regression and disposable HTTP/Postman runtime remain unverified. The structural implementation is complete locally, but the prompt’s full Definition of Done is **not fully met** until those checks can run. No production-readiness claim, commit, or push. ApiResponse was **not** applied.
