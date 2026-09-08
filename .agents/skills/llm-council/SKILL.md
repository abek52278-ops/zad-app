---
name: llm-council
description: Multi-model consensus and peer review using Groq and Gemini in parallel. Queries models simultaneously to compare perspectives, validate complex architectural decisions, or detect blindspots.
---

# LLM Council

Runs concurrent queries across alternative LLM engines (Groq `openai/gpt-oss-120b` and Google `gemini-3.5-flash`) to gather multi-model consensus and adversarial review.

## Usage
```bash
python3 .agents/skills/llm-council/scripts/query_llms.py "Your prompt or architectural dilemma here"
```
