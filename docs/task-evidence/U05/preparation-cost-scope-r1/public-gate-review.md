# Public launch/replay admission review

Read-only source review at `fc88419b4e4dfe5f58cede8e23ce9a9cfab388f4`:

- `PlayerLaunchRequest` requires registration and an available profile. Exact
  requests do not check the normal-player-enabled family flag. Thus a current
  `tsj-alpha/6/MEDIUM/RB56_CONTROL/0` can parse even though RB56 is held. The
  analogous HARD request is currently rejected only because HARD is unavailable.
- `RegistrationBoundary.supportsQuickPlay` similarly omits the enabled flag;
  future global HARD availability would make the held declared HARD pair appear
  supported there. Registration must remain distinct from normal admission.
- The coordinator already checks normal family admission before cancellation
  and again at publication; current source does not publish the held board.
  However, `PlayerSessionController` begins the public session before invoking
  the coordinator, so an invalid held-family request can transiently replace
  session presentation with PREPARING/ERROR. The prior electrical owner remains
  protected. Rejecting the public request earlier is the next bounded fix.
- Current HARD-negative tests can pass for the wrong reason: one uses an older
  epoch and another relies only on global profile unavailability. New negatives
  must use the current epoch and an available profile, plus independent held-
  registration support checks. Enabled exact requests keep their existing
  actual-evidence profile assessment; broad-seed declared-profile policy is a
  separate gate. Private qualification identities keep their separate path.

Relevant owners: `PlayerLaunchRequest`, `PlayerFamilyCatalog.RegistrationBoundary`,
`QuickPlayAdmission`, `PlayerSessionController`, `GenerationCoordinator`, public
replay/save parsers; contracts in `QuickPlayDeveloperVerifier`,
`QuickPlayContractTest`, `RelBSessionIdentityContractTest`, and
`U06SessionSaveContractTest`. This review ran no build or UI test and claims no
current publication bypass. A separate implementation scope must reproduce the
pre-admission failure and preserve valid public/private parsing and owner safety.
