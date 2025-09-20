Tarmin2Revised
Overview
Tarmin2 is a dungeon-crawling adventure game built with the libGDX framework. The game is a modern tribute to classic first-person dungeon crawlers, featuring procedurally generated mazes, a retro-inspired visual style, and a focus on exploration and survival. The project is structured into a core module for the main game logic and an lwjgl3 module for the desktop launcher.

Key Features
Procedurally Generated Mazes: Every playthrough offers a unique dungeon layout, ensuring high replayability.

First-Person Perspective: An immersive 3D view rendered with a raycasting algorithm, reminiscent of classic dungeon crawlers.

Dynamic Doors: Interactive doors that open with a smooth animation, adding a dynamic element to the environment.

Interactive Items: Discover and collect items like strength and healing potions to aid in your adventure.

Debug Overlay: A toggleable debug screen that displays the maze layout, player position, and direction in real-time.

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
assets/: This directory contains all of the game's assets, such as fonts, images, and data files.

core/src/main/java/com/bpm/minotaur/: The main package for the game's source code.

gamedata/: Contains classes that define the game's data structures, such as Maze, Player, and Item.

generation/: Includes the MazeGenerator class, which is responsible for creating the procedural dungeon layouts.

rendering/: Contains the rendering classes, including the FirstPersonRenderer and DebugRenderer.

screens/: Manages the different screens of the game, such as the MainMenuScreen and GameScreen.

lwjgl3/src/main/java/com/bpm/minotaur/lwjgl3/: The source code for the desktop launcher.

Design and Architecture
Game Loop and Screens
The game is built around a central Tarmin2 class that extends com.badlogic.gdx.Game. This class manages the game's screens, switching between the MainMenuScreen and the GameScreen as needed.

MainMenuScreen: The first screen the player sees. It displays the game's title and waits for player input to start the game.

GameScreen: The main screen for gameplay. It handles the rendering of the maze, player, and items, as well as processing player input and managing game state updates.

Rendering
The game's 3D view is rendered using a raycasting algorithm in the FirstPersonRenderer class. This technique creates a 3D perspective from a 2D map, a hallmark of classic dungeon crawlers.

Depth Buffer: The FirstPersonRenderer also generates a depth buffer, which is used to correctly render items and other objects in the world so they appear behind walls when they should.

Debug View: For development and testing, a DebugRenderer is included. It provides a top-down view of the maze, showing the player's position, direction, and the location of walls and doors.

Maze Generation
The dungeons in Tarmin2 are procedurally generated, ensuring a new experience with every playthrough.

GameScreen.java: This class contains an innovative approach to maze creation by stitching together pre-designed "tiles" of rooms and corridors. The createMazeFromArrayTiles method procedurally arranges and rotates these tiles to form a larger, cohesive map.

MazeGenerator.java: For a more classic roguelike feel, the project also includes a MazeGenerator that uses a combination of room placement and maze-crawling algorithms to create complex and varied dungeons.
