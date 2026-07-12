# gftd-motion-actor

A **free motion clip generation** loop actor for
[`network-isekai`](https://github.com/gftdcojp/network-isekai), gftdcojp's
fourth of seven per-modality asset actors (ADR-2607122200). Persona:
**マイ (Mai)**, 振付師 (choreographer) — "間と重心にこだわる振付師。データより
体の納得感を優先する" (see `resources/persona.edn`). Sibling actors:
`gftd-illust-actor` (image), `gftd-sculpt-actor` (3D), `gftd-rig-actor`
(auto-rig), `gftd-avatar-actor` (VRM compositing), `gftd-audio-actor`
(music+SFX), `gftd-voice-actor` (TTS voice).

Built on the same "sealed intelligence ⊣ independent governor ⊣ append-only
ledger" containment pattern as this workspace's other actors
(`gftd-talent-actor`, `wami-actor`, `cloud-itonami`) — here it is
**co-scientist tournament ⊣ AssetGovernor**, run by a **durable outer loop**
(not a StateGraph — murakumo generation jobs are async, minutes-scale, and
this workspace's CLAUDE.md is explicit that long-running work belongs in a
lease/tick/budget loop, not a StateGraph interrupt).

## The core contract

```
motion.generate            murakumo fleet (async gen.job)      motion.judge
 (closed gene pool,   ──▶  submit via cloud-murakumo.gen +  ──▶ (persona-fit
  persona-flavored)        queue-kotoba, poll for :done)         prompt score)
                                    │
                                    ▼
                        motion.cosci/run-round
              (Reflection=HARD gate, Ranking=Elo on judge score,
                    Proximity, Evolution, Meta-review)
                                    │
                              round winner
                                    ▼
                          motion.governor/violations
                    (license-free? format-ok? safe? titled?
                          write-kind is :asset only)
                          │                    │
                        ok?                  hard
                          ▼                    ▼
          motion.datalad + motion.aozora    motion.ledger
          (save to assets/, datalad push,   (:held — no binary
           publish to net.motion.asset)      is ever saved)
```

**The actor never commits/publishes an asset the AssetGovernor would
reject**, and it never writes anything but `:kind :asset` — it does not
touch network-isekai's game logic or canon, it only produces free material
for games to consume.

**HONEST LIMITS** (state these, do not pretend otherwise):
- The `:motion` modality's engine is `:skeleton` — a Mac-mini CPU worker
  that produces validated **idle/walk/dance/gesture EDN clips**, NOT full
  CUDA text-to-motion synthesis (see `cloud-murakumo`'s `resources/
  murakumo.edn` `:motion` function `:note`: "no fictitious CUDA
  text-to-motion claim"). It IS still prompt/param-driven like illust's
  image engine (no `:refs` needed, unlike rig/avatar) — set expectations
  accordingly: this actor generates small, structured, loopable motion
  data, not free-form choreography from arbitrary text.
- `motion.judge` scores the candidate's **prompt text** for persona-fit, not
  the actual joint motion the job produced. A real perceptual judge (a
  motion-quality model, a vision-capable critique call over a rendered
  preview) is follow-up.
- Whether a submitted job ever leaves `:queued` depends on a murakumo fleet
  worker (Mac-mini / `gad`) being up and consuming the `gftd-murakumo` kotoba
  queue — this actor only submits/polls, it never runs GPU/CPU inference
  itself.
- `motion.murakumo/artifact-url`'s CID→URL resolution is a best-effort guess
  (`KOTOBASE_ARTIFACT_BASE_URL` overrides it), not a confirmed contract.
- `motion.datalad/->isekai-manifest`'s `:asset/gen {:stage :motion ...}` is
  forward-compatible metadata only — network-isekai's own `isekai.gen`
  maturity table does not have a `:motion` stage wired up yet (as of this
  writing it covers `:photo-to-3d`/`:diffusion`/`:tts`/`:music`).

## This repo IS its own DataLad dataset

Unlike a typical actor repo, `assets/` here is **git-annex + Backblaze B2**
(`-c text2git`: code/EDN stay plain git, binaries get annexed) — accepted
assets are saved straight into this repo and pushed to B2, so "actor's own
git repo" and "asset storage" are the same thing (ADR-2607122200 §5).
`assets/<id>.edn` is written in the `network-isekai` `isekai.asset` manifest
shape so a later Asset Hub import needs no conversion.

```sh
datalad get assets/            # fetch real bytes from B2 (skeleton clones without them)
datalad push --to b2           # push new bytes after a local save
```

## Running

```sh
clojure -M:run tick     # one durable-loop step (cron/launchd)
clojure -M:run run      # stay resident, tick on an interval
clojure -M:run status   # print ledger tail + loop state
clojure -M:test         # offline, fully faked (no network) — see test/motion/loop_test.clj
clojure -M:lint         # clj-kondo, errors fail
```

Env: `ASSET_ACTOR_DAILY_BUDGET` (default 8 gen jobs/day),
`MURAKUMO_KOTOBA_URL`/`MURAKUMO_KOTOBA_GRAPH`/`MURAKUMO_KOTOBA_TOKEN`
(queue-kotoba auth), `MURAKUMO_GATEWAY_URL` (judge's chat-completions
gateway).

CACAO identity is self-minted to `.motion/identity.edn` on first run
(gitignored — never commit a private key). aozora collection:
`net.motion.asset.publish`.

## Design

ADR-2607122200 (`network-isekai 向け murakumo 生成アセット持続ループ actor
群`) is the SSoT for this actor and its six siblings. Direct code ancestry:
`cloud-itonami`'s `src/cloud_itonami/media/{murakumo,aozora,cacao,publisher,
publish}.clj(c)` (murakumo→governor→aozora pipeline) and
`cloud-murakumo`'s `src/cloud_murakumo/cosci.cljc` (co-scientist tournament
shape). This repo is itself a faithful 1:1 port of `gftd-illust-actor`
(actor #1 of 7) with only the domain-specific parts substituted (persona,
gene pool dimensions, murakumo modality/engine, governor's allowed formats,
manifest gen-stage).
