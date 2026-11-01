# Issue tracker: Local Markdown

Issues and specs for this project live as Markdown files in `.scratch/`.

## Conventions

- One feature per directory: `.scratch/<feature-slug>/`.
- The specification is `.scratch/<feature-slug>/spec.md`.
- Implementation issues are separate files at `.scratch/<feature-slug>/issues/<NN>-<slug>.md`, numbered from `01`.
- Triage state is a `Status:` line near the top of each spec or issue file, using the vocabulary in `triage-labels.md`.
- Append discussion under `## Comments` in the relevant file.

## Publish and read

- To publish a spec, create its feature directory and write `spec.md` there.
- To publish an implementation issue, create one numbered Markdown file in `issues/`.
- To fetch an issue, read the referenced file; the user may provide its path or number.

## Wayfinding

- Map: `.scratch/<effort>/map.md`.
- Child ticket: `.scratch/<effort>/issues/NN-<slug>.md`, with `Type:` (`research`, `prototype`, `grilling`, or `task`) and `Status:` (`claimed` or `resolved`).
- Record dependencies with `Blocked by: NN, NN`. A child becomes available when all blockers are resolved.
- Claim an available child by setting `Status: claimed` before work. Resolve it by recording the answer, setting `Status: resolved`, and adding a pointer in the map.
