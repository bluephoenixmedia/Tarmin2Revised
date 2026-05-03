---
description: Workflow for starting a new feature or task in Tarmin2
---

# Start Task Workflow

Whenever you are asked to implement a new feature, fix a bug, or perform a refactor in this repository, you must follow these precise steps:

1. **Read the Product Specification**
   Use your file reading capabilities to read `spec.md` located in the root directory. 
   - Understand the current core gameplay loops.
   - See how the new feature integrates with existing mechanics (Maze Generation, Combat, Survival).

2. **Read the AI/Agent Guidelines**
   Use your file reading capabilities to read `agent.md` located in the root directory.
   - Note the technical constraints (LibGDX standards, ECS-lite paradigm).
   - Abide by the coding conventions (e.g., Use `Gdx.app.log` instead of `System.out.println`).
   - Use the exact standard lexicons (e.g., War Strength, Satiety, Toxicity).

3. **Propose an Implementation Plan**
   Present a clear implementation plan to the user that specifies:
   - Which managers will be modified or created.
   - Which Data objects are affected.
   - Any Screens that will need to be updated.

4. **Execute and Verify**
   Once the plan is approved, proceed with modifying the codebase safely.
