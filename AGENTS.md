# Repository Guidelines

## Project Structure & Module Organization

This repository currently holds the MILF product vision and implementation planning docs.

- `VISION.md` is the source product narrative, positioning, demo strategy, and build priorities.
- `docs/superpowers/specs/` contains approved design specifications.
- `docs/superpowers/plans/` contains implementation plans with task checklists.
- Planned backend code should live under `backend/milf/`, with tests in `backend/tests/`, as specified in `docs/superpowers/plans/2026-06-22-milf-backend.md`.
- Keep local-only files out of git: `.venv/`, `.env`, `__pycache__/`, `.DS_Store`, and `*.pyc` are ignored.

## Build, Test, and Development Commands

There is no executable app scaffold or package manifest in the current tree yet. For documentation-only changes, no build step is required.

When backend work is added, follow the existing plan:

- `uv pip install <package>`: install Python dependencies into the existing `.venv`.
- `cd backend && pytest`: run backend tests once `backend/pytest.ini` and tests exist.
- `cd backend && python scripts/spike_contract.py`: inspect the MobileRun contract during the backend scaffold task.

Document any new runnable command in the relevant plan, spec, or README when adding source code.

## Coding Style & Naming Conventions

For Markdown, use concise headings, short paragraphs, and tables only when they improve scanability. Preserve the product language in `VISION.md`; do not casually rename core concepts such as the confirmation gate, WebSocketDriver, RouterSTT, ILMU, or MERaLiON.

For planned Python backend code, use Python 3.11, async I/O for websocket and API boundaries, Pydantic v2 models for structured messages, and snake_case module names. Keep backend package code under `backend/milf/`.

## Testing Guidelines

Backend tests should use `pytest` and `pytest-asyncio`. Place tests in `backend/tests/` and name files `test_*.py`. Prioritize coverage for protocol serialization, websocket driver message translation, STT adapter contracts, confirmation-gate behavior, and the hero-flow reliability harness.

## Commit & Pull Request Guidelines

Recent commits use short, imperative, sentence-case messages, for example `Refine MILF design spec with improved table formatting and clarity`. Keep commits focused on one logical change.

Pull requests should include a brief summary, linked issue or plan section when relevant, test results or an explicit note that no tests apply, and screenshots or recordings for UI/demo-flow changes.

## Security & Configuration Tips

Never commit secrets. Expected backend environment names include `OPENAI_API_KEY`, `ILMU_API_KEY`, `ILMU_API_URL`, `MERALION_API_KEY`, `MERALION_API_URL`, `MILF_STT_BACKEND`, `MILF_WS_HOST`, and `MILF_WS_PORT`.
