---
name: prepare-pr
description: "Detect work state and auto-execute remaining steps: commit, feature branch, push, PR create. Use /prepare-pr after implementation."
user-invocable: true
disable-model-invocation: true
allowed-tools:
  - Bash
  - Read
  - Glob
  - Grep
---

# /prepare-pr - Auto PR Creation from Work State

Detect the current git work state and execute only the remaining steps needed: commit, feature branch creation, push, and PR creation.

## Steps

### Step 1: Gather current state

Run these commands **in parallel**:

```bash
git status --short
git branch --show-current
git log --oneline -10
git diff --stat
git diff --cached --stat
git rev-list --count origin/main..HEAD 2>/dev/null || echo "0"
gh pr view --json number,url 2>/dev/null || echo "NO_PR"
```

### Step 2: Classify state

Based on the gathered info, classify into one of:

| State | Condition | Steps to run |
|-------|-----------|-------------|
| A. Uncommitted changes | `git status` shows modified/untracked files | commit + branch + push + PR |
| B. Committed on main, not pushed | On main, commits ahead of origin/main | branch + push + PR |
| C. On feature branch, not pushed | On feature branch, commits ahead of remote | push + PR |
| D. Pushed, no PR | Synced with remote, no PR exists | PR only |
| E. PR already exists | `gh pr view` succeeds | Show PR URL and exit |
| F. No changes | Nothing to commit, nothing ahead | Show "No changes" and exit |

**Important**: Handle cases where the user is already on a feature branch (states C, D, E).

### Step 3: Commit (state A only)

1. Analyze `git diff`, `git diff --cached`, and `git status`
2. Check `git log --oneline -5` for existing commit message style
3. Generate a concise commit message matching existing style
   - Target: under 50 chars for first line
4. Stage relevant files with `git add`
   - **EXCLUDE**: `.env`, `credentials`, secrets files
   - **EXCLUDE**: `scripts/review/output/` files
5. Execute commit

```bash
git add <target files>
git commit -m "<generated message>"
```

### Step 4: Create feature branch (when on main)

1. Generate a kebab-case English summary from the commit content
2. Branch name format: `feature/<summary>` (e.g., `feature/add-player-search`)
3. Create and switch to the feature branch:

```bash
git checkout -b feature/<summary>
```

4. Reset main back to origin/main so the commits only exist on the feature branch:

```bash
git branch -f main origin/main
```

### Step 5: Push

```bash
git push -u origin <branch-name>
```

### Step 6: Create PR

1. Generate PR title and body from commit content
2. Create the PR:

```bash
gh pr create --base main --title "<title>" --body "$(cat <<'EOF'
## Summary
<bullet points of changes>

## Test plan
- [ ] Operation check

Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

### Step 7: Report completion

Display:
- The created PR URL
- List of steps that were executed
- Remind: "To generate a review prompt, run `/review`"
