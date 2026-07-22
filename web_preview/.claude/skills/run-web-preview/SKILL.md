---
name: run-web-preview
description: Build, run, and drive the زاد (Zad) web_preview static mockup. Use when asked to start web_preview, take a screenshot of it, or click through its screens (home, inventory, subscriptions, family, chat, kids mode).
---

`web_preview/index.html` is a single self-contained static HTML mockup
(no build step, no framework) of the Zad phone UI — 6 screens toggled
by JS (`switchScreen()`), no server-side logic. Drive it via the
headless-Chromium REPL at `.claude/skills/run-web-preview/driver.mjs`
(no `chromium-cli` on this machine, so this driver wraps Playwright
directly).

All paths below are relative to `web_preview/`.

## Prerequisites

```bash
sudo apt-get update
sudo apt-get install -y tmux   # only if not already present
cd .claude/skills/run-web-preview
npm install                    # installs playwright
npx playwright install chromium
sudo npx playwright install-deps chromium   # system .so's (libatk-1.0 etc.)
```

No `xvfb` needed — Chromium runs in its own headless mode, not under a
virtual X server.

## Run (agent path)

1. Serve the static file (it's not meant to be opened via `file://` —
   keep it on `http://` so relative asset loads behave normally):

```bash
cd web_preview
python3 -m http.server 8420 &
timeout 10 bash -c 'until curl -sf http://localhost:8420/index.html >/dev/null; do sleep 0.5; done'
```

2. Launch the driver under tmux and drive it:

```bash
tmux new-session -d -s zadweb -x 200 -y 50
tmux send-keys -t zadweb 'node .claude/skills/run-web-preview/driver.mjs' Enter
timeout 10 bash -c 'until tmux capture-pane -t zadweb -p | grep -q "launch <url>"; do sleep 0.2; done'
tmux send-keys -t zadweb 'launch http://localhost:8420/index.html' Enter
timeout 20 bash -c 'until tmux capture-pane -t zadweb -p | grep -q "^launched\."; do sleep 0.2; done'
tmux send-keys -t zadweb 'ss 01-home' Enter
timeout 10 bash -c 'until tmux capture-pane -t zadweb -p | grep -q "screenshot:.*01-home"; do sleep 0.2; done'
```

**One command per round-trip, and always wait for that command's own
exact output before sending the next one** — see Gotchas. Don't batch
multiple `send-keys` calls with only a fixed `sleep` between them.

Screenshots land in `screenshots/` next to the driver (override with
`SCREENSHOT_DIR`). Stop the server when done:
`lsof -ti:8420 -sTCP:LISTEN | xargs -r kill`.

### Commands

| command | what it does |
|---|---|
| `launch <url>` | launch headless Chromium, nav to `<url>` |
| `nav <url>` | navigate the existing page |
| `ss [name]` | screenshot → `screenshots/<name>.png` |
| `click <css-sel>` | click element via DOM `.click()` |
| `click-text <text>` | click button/link/`[onclick]` containing text |
| `type <text>` / `press <key>` | keyboard input |
| `wait <css-sel>` | wait up to 10s for a selector |
| `eval <js>` | evaluate expression in the page, print JSON |
| `text [css-sel]` | print `innerText` of selector (or `body`) |
| `console-errors` | print any buffered console/page errors |
| `quit` | close the browser, exit the driver |

### The 6 screens

Bottom-nav driven, via `id`/class selectors in `index.html`:

| screen | how to reach it |
|---|---|
| Home | `click #navHome` (default on load) |
| Inventory (المخزون) | `click #navInv` |
| Chat (مساعد زاد الذكي) | `click .ncenter` |
| Subscriptions (الاشتراكات) | `click #navSubs` |
| Family (العائلة) | `click #navFamily` |
| Kids mode (وضع الأطفال) | `click-text إدارة` (link inside the Family screen's kids card — not in the bottom nav) |

## Run (human path)

Open `index.html` directly in a real browser, or serve it and open
`http://localhost:8420/` — it's a static mockup, nothing to build.

## Gotchas

- **Sending multiple `tmux send-keys` commands back-to-back with only
  a fixed `sleep` between them races the driver.** The REPL's command
  handlers are `async`; Node's readline does not serialize overlapping
  `line` events, so if two commands land in the pty's buffer close
  together, a `click` for screen N+1 can execute (and its DOM mutation
  land) *while* the screenshot for screen N is still capturing —
  producing a screenshot of the wrong screen, or occasionally a
  screenshot of a screen mid-way through losing its `active` class
  (i.e. blank). This is exactly what happened during driver
  development: batching 5 click+screenshot pairs in one shell script
  produced two screenshots showing the *next* screen's content and two
  blank ones. Fix: send one command, then poll
  `tmux capture-pane -t <sess> -p | grep -qF '<that command's own exact
  output line>'` (not a substring that also matches the echoed input)
  before sending the next command. One round-trip per command is
  slower but reliable.
- **`tmux capture-pane -p` captures the full pane height** (50 rows,
  per `-y 50`), including blank padding below the last real line —
  `| tail -N` on it grabs blank lines, not the last output. Filter
  first: `tmux capture-pane -p | grep -v '^$' | tail -N`.
- **Relaunching `driver.mjs` in the same tmux pane right after `quit`
  can silently fail to read stdin** (the process starts, prints
  nothing, and exits immediately — the shell falls straight back to
  bash and the next line you send gets run as a shell command instead
  of a driver command, e.g. `-bash: launch: command not found`). Cause
  not fully isolated (likely pty/termios state left over from the
  previous process's raw `/dev/stdin` read). Workaround: don't `quit`
  and relaunch in the same pane — `tmux kill-session` and start a
  fresh session instead, or just don't `quit` until you're fully done.
- **Missing shared libraries on first Chromium launch**
  (`libatk-1.0.so.0: cannot open shared object file`) — `npm install
  playwright && npx playwright install chromium` only downloads the
  browser binary, not its OS-level deps. Run
  `sudo npx playwright install-deps chromium` separately.

## Troubleshooting

- **`browserType.launch: Target page, context or browser has been
  closed` + `error while loading shared libraries`**: missing Chromium
  system deps — run `sudo npx playwright install-deps chromium`.
- **`-bash: launch: command not found` right after `quit`+relaunch**:
  see the tmux-pane-reuse gotcha above — kill the session and start a
  new one.
- **Screenshot shows the wrong screen or is blank**: a race from
  batching commands — see the first Gotcha. Re-run that one step
  alone, waiting for its exact completion line.
