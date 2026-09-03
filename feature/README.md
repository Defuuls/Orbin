# Feature module contract

Every `feature:*` module should feel like a small application even though Orbin is large.

## Own here

- screen/route adapter code that connects `ui-next` to feature state;
- ViewModels and immutable UI state;
- presentation mapping/indexing that turns domain models into plain screen data;
- feature-specific Android interaction glue.

## Do not own here

- engine/protocol parsing (`provider:*`);
- persistence/network implementations (`data`, `network`);
- reusable business rules (`domain`, `core:model`);
- cross-feature calls;
- screen layout primitives that can accept plain values/callbacks (`ui-next`).

## Reading order

For an unfamiliar feature, start at its `Next*Screen` or route adapter, then follow its ViewModel
inward to the domain contract. Do not begin in the database/provider unless the behavior being
debugged has already been proven to originate there.

## Dependency rule

Features may share inner contracts and common UI/infrastructure, but never depend on another
`feature:*` module. `scripts/validate_architecture.py` enforces this both in Gradle dependencies and
source imports.

When two features need the same behavior, extract the smallest stable contract inward rather than
creating a sideways dependency.
