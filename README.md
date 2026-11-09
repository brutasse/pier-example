# pier-example

Demo repository for the [pier](https://github.com/brutasse/pier) ⇄
[rig](https://github.com/brutasse/rig) trusted-publishing flow: a dummy
Clojure library published to a private, OIDC-gated Maven repository with
no deploy tokens — only the GitHub Actions OIDC token, checked by a CEL
policy that binds publication to this repository and to release tags.

```
GitHub Actions (tag push)                pier @ 194.182.165.182:80
┌────────────────────────┐   OIDC token  ┌──────────────────────────┐
│ rig publish            │──────────────▶│ CEL policy:              │
│ (RIG_TOKEN_PIER =      │   PUT jar+pom │  upload ← brutasse/      │
│  GHA id-token)         │               │  pier-example @ refs/    │
└────────────────────────┘               │  tags/v* only            │
                                         │ S3 backend               │
consumer job: rig lock ◀─────────────────│ pull-through:           │
  resolves through the same gate         │  central, clojars        │
                                         └──────────────────────────┘
```

## Layout

| Path | What it is |
|------|------------|
| `deps.edn` | Rig workspace root: the library module + the `dev` REPL module, pinned to JVM 21 |
| `modules/pier-example/` | The dummy library `com.example/pier-example` (`:rig/publish? true`, publishes to the `pier` repo) |
| `dev/` | Development REPL module (loads the library by `:local/root`, pins `reloaded.repl`) |
| `pier/pier.yaml` | The pier configuration for the demo host (fill in the S3 block) |
| `pier/rig-auth.yaml` | The rig gate config; CI copies it to `~/.config/rig/auth.yaml` |
| `.github/workflows/ci.yml` | The gate on push/PR: `rig check && rig test` |
| `.github/workflows/release.yml` | On `v*` tag: request the GHA OIDC token, `rig publish`, then verify the artifact is behind the gate (200 with the token, 401 without) and that a consumer locks it through pier |

## Running the pier

On `194.182.165.182`:

```sh
# install the pier binary (see the pier README), then:
export PIER_CONFIG=/path/to/pier/pier.yaml   # S3 block filled in
# S3 credentials come from the standard AWS SDK chain (env vars, etc.)
pier serve -config pier/pier.yaml
```

Policy (`pier/pier.yaml`):

- `publish-from-tags` — only the `brutasse/pier-example` repository may
  upload, and only when the token's ref is a tag (`refs/tags/v*`).
- `ci-download` — jobs of the same repository may download.
- Everything else is denied (including `delete`: it stays the operator's
  tool, done outside the policy).

The audience both sides agree on is `https://194.182.165.182/` (pier
config `audiences`, and the `audience` parameter of the token request in
`release.yml`).

## Releasing

The tag must match `:rig/version` in `modules/pier-example/deps.edn`
(pier's `immutable_releases` rejects re-publishing a GAV):

```sh
# 1. bump :rig/version (and re-lock if any dependency changed)
rig lock            # local, resolution goes to central/clojars
git commit -am "release: 0.2.0"
git tag v0.2.0
git push origin main --tags   # the tag push triggers release.yml
```

`release.yml` then: requests the GHA OIDC token (audience
`https://194.182.165.182/`), sets it as `RIG_TOKEN_PIER`, and runs
`rig publish` — which PUTs the jar and POM to
`http://194.182.165.182/com/example/pier-example/<version>/` with the
bearer. The verify job proves the E2E: the artifact is fetchable with the
token (200), not without (401), and a single-repository consumer
(`central` + `clojars` redeclared at the pier URL) locks and checks the
just-published version through pier.

## Local development

`rig check` and `rig test` run on the locked classpath and need no token.
Any command that resolves (the local `rig lock` above, `rig add`, …)
needs the gate config and a bearer for the `pier` repo, because it is
declared `:auth :oidc`:

```sh
mkdir -p ~/.config/rig
cp pier/rig-auth.yaml ~/.config/rig/auth.yaml
RIG_TOKEN_PIER=<a valid pier bearer> rig lock
```

For the initial lock, `RIG_TOKEN_PIER` may be a placeholder: nothing in
this workspace resolves from pier (its `:mvn/repos` entry sits after
`central`/`clojars`), so the token is only inspected, never sent.
