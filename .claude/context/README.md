# Claude Context Directory

This directory contains the comprehensive knowledge repository for Claude Code when working with the Mixpanel Android SDK.

## Directory Structure

```
.claude/context/
├── README.md                    # This file
├── codebase-map.md             # Project structure overview
├── discovered-patterns.md       # Coding conventions and patterns
├── architecture/
│   └── system-design.md        # System architecture documentation
├── technologies/
│   ├── android-sdk-patterns.md # Android-specific patterns
│   └── java-patterns.md        # Java implementation patterns
└── workflows/
    ├── feature-development.md  # How to add features
    ├── release-process.md      # Release workflow
    └── testing-strategy.md     # Testing approaches
```

## Migration Note

This directory was migrated from `claude/` to `.claude/context/` to follow the convention of keeping AI-related configuration in hidden directories. All references in the codebase have been updated to point to the new location.

## Usage

These files are automatically loaded by Claude Code to provide deep context about the codebase. They are referenced by:
- `CLAUDE.md` - Main project instructions
- `AGENTS.md` - Cloud execution instructions
- `.claude/commands/update-context.md` - Update workflow

## Maintenance

When patterns or architecture change, update these files using:
```
/user:context-update
```

This will refresh the knowledge repository with the latest patterns and conventions from the codebase.