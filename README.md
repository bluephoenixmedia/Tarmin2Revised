Tarmin2Revised
Overview
Tarmin2Revised is a dungeon-crawling adventure game built with the libGDX framework. The game is a modern tribute to the classic Intellivision game Treasure of Tarmin, featuring procedurally generated mazes, a retro-inspired visual style, and a focus on exploration, combat, and survival. The project is structured into a core module for the main game logic and an lwjgl3 module for the desktop launcher.

Key Features
Procedural Tile-Based Mazes: Dungeons are built by randomly selecting, rotating, and stitching pre-designed room "tiles" together, creating unique layouts every game.

First-Person Perspective: An immersive 3D view rendered with a raycasting algorithm, reminiscent of classic dungeon crawlers.

Dynamic Doors: Interactive doors that open with a smooth animation.

Deep Item & Equipment System: Discover dozens of items across categories: War Weapons, Spiritual Weapons, Armor, Rings, Consumables, and Treasure.

Color-Coded Tiers: Items and monsters have colors (e.g., Tan, Orange, Blue) that signify their power, based on the dungeon level.

Locked Containers: Find locked chests and boxes that require a key of the correct color to open.

Turn-Based Combat: Engage in classic turn-based combat against a variety of monsters.

Dual-Strength System: Manage both War Strength (health/melee) and Spiritual Strength (magic/defense), attacked by different monster types.

Player Leveling: Gain experience from victories to level up, increasing your stats and healing you.

Resource Management: Juggle Food (for resting) and Arrows (for ranged combat).

Debug Overlay: A toggleable debug screen (F1) that displays the maze layout, player position, and other technical info.

Dual-Render Mode: Toggle (F2) between a modern, textured renderer and a "Classic" mode with solid, shaded colors.

Getting Started
To get the project up and running, you'll need to have a Java Development Kit (JDK) installed, version 17 or higher.

Building and Running
This project uses Gradle to manage dependencies and build the application. You can use the included Gradle wrapper to run the game without having to install Gradle yourself.

To run the game, open a terminal in the project's root directory and run the following command:

On Windows: gradlew.bat lwjgl3:run

On macOS/Linux: ./gradlew lwjgl3:run

To build a runnable JAR file, use this command:

gradlew lwjgl3:jar

The JAR file will be located in the lwjgl3/build/libs/ directory.

Project Structure
The project is organized into two main modules:

core: Contains the majority of the game's code, including game logic, rendering, and data management. This module is platform-independent.

lwjgl3: The desktop launcher for the game, responsible for creating the application window and starting the game.

Key Files and Directories
assets/: Contains all of the game's assets, such as fonts, images, sounds, and music.

core/src/main/java/com/bpm/minotaur/: The main package for the game's source code.

gamedata/: Defines core game state classes: Player, Maze, Item, Monster.

managers/: Contains singletons and systems for managing combat, spawning, sound, music, and debug features.

rendering/: Contains the rendering classes, including the FirstPersonRenderer and EntityRenderer.

screens/: Manages the different screens of the game, such as the MainMenuScreen and GameScreen.

lwjgl3/src/main/java/com/bpm/minotaur/lwjgl3/: The source code for the desktop launcher.

Design and Architecture
Game Loop and Screens
The game is built around a central Tarmin2 class that extends com.badlogic.gdx.Game. This class manages the game's screens, switching between the MainMenuScreen and the GameScreen as needed.

MainMenuScreen: The first screen the player sees. It displays the game's title and waits for player input to start the game.

GameScreen: The main screen for gameplay. It handles the rendering of the maze, player, and entities, as well as processing player input and managing game state updates.

Rendering
The game's 3D view is rendered using a raycasting algorithm in the FirstPersonRenderer class. This technique creates a 3D perspective from a 2D map.

Depth Buffer: The FirstPersonRenderer also generates a depthBuffer, which is used by the EntityRenderer to correctly render items and monsters in the world so they appear behind walls when they should.

Debug View: For development and testing, a DebugRenderer is included. It provides a top-down view of the maze, showing the player's position, direction, and the location of walls and doors.

Maze Generation
The game's dungeons are procedurally generated in GameScreen.java. The createMazeFromArrayTiles method implements an innovative approach by building a large map (e.g., 2x2) from a grid of smaller, pre-designed "tiles" (12x12). These tiles are randomly selected from a pool of 16 templates, rotated, and then "stitched" together using corridor templates to ensure connectivity. This creates complex, varied, and non-linear dungeon layouts for each level.
