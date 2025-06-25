# Update Context Command

## Purpose
Refreshes the Claude Code context when significant changes are made to the codebase.

## When to Run
- After adding major new features
- After refactoring core components  
- When patterns or conventions change
- Before major releases
- Quarterly for active projects

## Command
```
/user:context-update
```

## What Gets Updated
1. `.claude/context/discovered-patterns.md` - New patterns found
2. `.claude/context/architecture/system-design.md` - Architecture changes
3. `.claude/context/technologies/*.md` - Technology updates
4. `.claude/context/workflows/*.md` - Process improvements
5. `CLAUDE.md` - Key patterns summary

## Post-Update Tasks
- Review generated files for accuracy
- Remove outdated patterns
- Add any missing context manually
- Run `/user:rules-sync` if using Cursor
- Commit changes to version control