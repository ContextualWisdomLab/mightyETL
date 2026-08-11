# NVIDIA OpenCode model selection evidence

## Scope

This evidence note governs the hosted NVIDIA model identifier used by `.github/workflows/hourly-opencode-maintenance.yml`. It does not change the existing review agent, its provider, its credential names, or its approval authority. It also does not introduce a non-NVIDIA credential or model fallback.

## Problem statement

The workflow originally selected `nvidia/qwen/qwen3-coder-480b-a35b-instruct`. NVIDIA's current model catalog marks that model's free endpoint as deprecated. The partner endpoint remains available, but a repository workflow that relies only on `NVIDIA_NIM_API_KEY` must not assume a separately contracted partner endpoint. A deprecated free endpoint is therefore an operational reliability defect: the scheduler may be syntactically correct yet fail before it can inspect or improve the repository.

## Decision

Select `nvidia/deepseek-ai/deepseek-v4-pro` for the scheduled OpenCode development agent.

NVIDIA's current primary documentation identifies `deepseek-ai/deepseek-v4-pro` as:

- available through a free endpoint;
- suitable for coding, agentic AI, tool use, software engineering, and enterprise assistants;
- capable of structured output and function or tool calling;
- able to accept up to one million tokens of context;
- licensed for commercial and non-commercial use under the NVIDIA Open Model Agreement and the model's MIT terms.

The NVIDIA model card reports stronger maximum-reasoning results than DeepSeek V4 Flash on the listed software-engineering and terminal-agent benchmarks, including SWE Verified, SWE Pro, SWE Multilingual, and Terminal Bench 2.0. mightyETL does not claim those benchmark values as its own performance and does not claim that OpenCode automatically selects NVIDIA's maximum-reasoning mode. The comparison is used only as current primary-source evidence that the Pro endpoint is a defensible high-capability free model for repository-scale agentic coding.

## Cost and reliability boundary

The selected endpoint is currently marked free by NVIDIA. That status is operational metadata, not a permanent contractual guarantee. The workflow therefore pins one explicit model identifier and its contract test rejects the known deprecated Qwen3 Coder identifier. It does not silently route to a paid partner endpoint or another provider.

A future endpoint deprecation must be handled through the same test-first process:

1. confirm status in NVIDIA's current primary model catalog and API reference;
2. add or update a failing contract test for the replacement identifier;
3. select a currently available NVIDIA free endpoint suited to coding and tool use;
4. update this evidence note, operations documentation, design, implementation plan, and `CHANGELOG.md`;
5. require exact-head CI, security checks, and independent approval before merge.

The scheduled agent intentionally has no automatic model fallback. Running a second model after a partially completed agent session could create non-deterministic workspace state, duplicate branches, or conflicting pull requests. A model rejection therefore fails the run visibly and leaves repository state for the next reviewed maintenance change.

## Test-first evidence

`HourlyOpenCodeMaintenanceWorkflowTest.usesCurrentFreeAgenticCodingModel` was committed before the workflow model was changed. On the pull-request CI merge ref for test-only commit `42eb7d7ac8bc3912e3a50f98b427b712f78b2b9b`, GitHub Actions run `30964719079` reported 286 tests, one failure, zero errors, and zero skipped project tests on Ubuntu. The sole failure was the new model-availability contract because the workflow still contained the deprecated Qwen3 Coder identifier.

The production workflow then replaced only the model identifier with `nvidia/deepseek-ai/deepseek-v4-pro`. The NVIDIA credential alias, OpenCode installation, permissions, branch authority, review-agent boundary, and merge protections remain unchanged.

This note records design evidence rather than exact-head completion. Any later commit makes earlier CI and review evidence stale.

## Operational response

If NVIDIA rejects the model identifier, reports endpoint deprecation, or removes free access, do not add GitHub Copilot, Anthropic, OpenAI, or a partner-endpoint credential as an emergency fallback. Disable the scheduled workflow if necessary, preserve any open feature branch or pull request, and prepare a bounded reviewed model-selection change using current NVIDIA primary documentation.

## References

NVIDIA Corporation. (2026). *DeepSeek V4 Pro*. NVIDIA NIM API catalog. https://build.nvidia.com/deepseek-ai/deepseek-v4-pro

NVIDIA Corporation. (2026). *DeepSeek AI / DeepSeek V4 Pro*. NVIDIA NIM API reference. https://docs.api.nvidia.com/nim/reference/deepseek-ai-deepseek-v4-pro

NVIDIA Corporation. (2026). *Qwen3-Coder-480B-A35B-Instruct*. NVIDIA NIM API catalog. https://build.nvidia.com/qwen/qwen3-coder-480b-a35b-instruct
