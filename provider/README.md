# Provider modules

`provider:api` is the stable SPI. Each `provider:<engine>` module owns one protocol family and must
normalize that protocol into Orbin domain models before returning.

Provider implementations may depend on `provider:api`, core primitives and shared network plumbing.
They may not depend on app, feature, data, media, `ui-next`, or another provider implementation.
These boundaries are enforced by `scripts/validate_architecture.py`.

Mapped results are checked by `ProviderContract` and registry-level instrumentation. Keep fixture
tests engine-specific, but run representative mapped boards/catalogs/threads through the shared
contract so protocol regressions fail close to the mapper.

See `docs/provider-api/contract.md` and `docs/provider-api/adding-a-provider.md`.
