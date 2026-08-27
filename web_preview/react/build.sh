#!/usr/bin/env bash
# JSX -> plain JS. No bundling, no deps in the output: React/ReactDOM come from
# vendor/*.js as globals, so app.js stays a single readable file.
set -euo pipefail
cd "$(dirname "$0")"
npx --yes esbuild@0.23.1 app.jsx \
  --loader:.jsx=jsx \
  --jsx=transform \
  --format=iife \
  --target=es2018 \
  --charset=utf8 \
  --outfile=app.js
echo "built app.js"
