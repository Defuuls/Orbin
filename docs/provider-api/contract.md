# Provider contract

`ImageBoardProvider` is Orbin's engine boundary. Protocol adapters may be messy internally; values
that cross this boundary may not be.

`ProviderContract` is the executable part of that promise. The registry wraps every provider with
`InstrumentedImageBoardProvider`, which validates results before repositories see them.

## Required invariants

### Boards

- board ids are nonblank and unique within a provider response.

### Catalogs

- thread keys are unique;
- each opening post is marked as an OP;
- opening-post board/thread ids match the catalog key;
- attachment ids are nonblank;
- source and thumbnail URLs are absolute HTTP(S) URLs.

### Threads

- the opening post is marked as an OP;
- every post belongs to the requested board/thread;
- post ids are unique inside the thread;
- attachment ids are nonblank;
- source and thumbnail URLs are absolute HTTP(S) URLs.

Contract failures become `ProviderException.Parse`, so the data layer handles them through the same
typed error path as malformed transport JSON rather than allowing an unrelated media/UI crash.

## Provider tests

Engine modules should keep protocol-specific fixture tests and pass mapped results through the
shared contract. This gives two layers of protection:

1. fixture tests catch a regression before runtime;
2. the registry wrapper catches an upstream response shape that fixtures did not anticipate.

Do not weaken the shared contract to accommodate one engine. Normalize that engine inside its
mapper so callers continue receiving the same domain guarantees.

## Diagnostics

Provider instrumentation records only provider id, operation, duration and outcome. Never add board
ids, thread ids, query text, URLs, titles or post/media content to diagnostic events. Orbin's local
diagnostics are useful precisely because they remain safe to export without exposing browsing
content.
