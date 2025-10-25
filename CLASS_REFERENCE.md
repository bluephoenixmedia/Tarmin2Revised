Tarmin2Revised Class Reference
Core Application
class: Tarmin2
Inherits: com.badlogic.gdx.Game

Implements: (None)

Composition (Owns/Creates):

SpriteBatch

Viewport

OrthographicCamera

Dependencies (Uses/References):

MusicManager

SettingsManager

MainMenuScreen

Notes: The main libGDX entry point. Manages global objects (SpriteBatch, Viewport) and controls screen transitions.

class: Lwjgl3Launcher (lwjgl3)
Inherits: (None)

Implements: (None)

Composition (Owns/Creates):

Lwjgl3ApplicationConfiguration

Tarmin2

Notes: The desktop launcher for the game. Creates the application window and starts the Tarmin2 game instance.

Screens
class: BaseScreen
Inherits: (None)

Implements: com.badlogic.gdx.Screen

Composition (Owns/Creates):

Tarmin2 (Reference)

Notes: An abstract base class for all game screens to share the Tarmin2 game reference.

class: GameScreen
Inherits: BaseScreen

Implements: com.badlogic.gdx.InputProcessor, com.badlogic.gdx.utils.Disposable

Composition (Owns/Creates):

DebugManager (Singleton Reference)

ShapeRenderer

BitmapFont

DebugRenderer

FirstPersonRenderer

EntityRenderer

Hud

AnimationManager

GameEventManager

SoundManager

Player

Maze

Random

CombatManager

SpawnManager

Dependencies (Uses/References):

Tarmin2

Difficulty

MusicManager

Item

Ladder

Gate

CastleMapScreen

Notes: The central hub of all gameplay. It initializes, manages, and renders the game world, HUD, and entities. It also processes all player input.

class: MainMenuScreen
Inherits: BaseScreen

Implements: com.badlogic.gdx.InputProcessor

Composition (Owns/Creates):

Texture (titleImage)

Stage

Table

TextButton

SelectBox<Difficulty>

Sound (enterSound)

Dependencies (Uses/References):

Tarmin2

Difficulty

GameScreen

SettingsScreen

MusicManager

Notes: The main menu, allowing the player to start a new game (selecting difficulty) or go to settings.

class: SettingsScreen
Inherits: BaseScreen

Implements: (None)

Composition (Owns/Creates):

Stage

Table

Label

Slider

TextButton

Dependencies (Uses/References):

Tarmin2

SettingsManager

MusicManager

MainMenuScreen

Notes: Allows the player to adjust volume settings, which are saved/loaded via SettingsManager.

class: CastleMapScreen
Inherits: BaseScreen

Implements: com.badlogic.gdx.InputProcessor

Composition (Owns/Creates):

Player (Reference)

Maze (Reference)

GameScreen (Reference)

Texture (mapBackground)

ShapeRenderer

BitmapFont

Dependencies (Uses/References):

Tarmin2

Notes: Displays a 2D top-down view of the maze, player position, and discovered items/monsters.

class: GameOverScreen
Inherits: BaseScreen

Implements: (None)

Composition (Owns/Creates):

Stage

Table

Label

Dependencies (Uses/References):

Tarmin2

MainMenuScreen

Notes: A simple screen shown when the player is defeated, prompting them to return to the main menu.

Game Data (gamedata)
class: Player
Inherits: (None)

Implements: (None)

Composition (Owns/Creates):

Vector2 (position)

Direction (facing)

Vector2 (directionVector, cameraPlane)

Inventory

Item (wornHelmet, wornShield, etc.)

Dependencies (Uses/References):

Difficulty

Maze

GameEventManager

SoundManager

Door

Gate

Ladder

Notes: The main player data class. Holds all stats (WS, SS, food, arrows, level, XP) and equipment. Contains all interaction logic (interact, useItem, moveForward).

class: Maze
Inherits: (None)

Implements: (None)

Composition (Owns/Creates):

int[][] (wallData)

Map<GridPoint2, Object> (gameObjects)

Map<GridPoint2, Item>

Map<GridPoint2, Monster>

Map<GridPoint2, Ladder>

Map<GridPoint2, Gate>

List<Projectile>

Dependencies (Uses/References):

Direction

Door

Notes: A data container for a single dungeon level. Holds the wall bitmask array and maps of all entities by their grid coordinates.

class: Monster
Inherits: (None)

Implements: Renderable

Composition (Owns/Creates):

MonsterType (enum)

Vector2 (position)

MonsterColor (enum)

Texture

Vector2 (scale)

Dependencies (Uses/References):

MonsterSpriteData

Notes: Data class for monsters. The constructor is a large switch statement that defines all stats, sprites, and textures based on MonsterType.

class: Item
Inherits: (None)

Implements: Renderable

Composition (Owns/Creates):

ItemType (enum)

ItemCategory (enum)

Vector2 (position)

ItemColor (enum)

Texture

Vector2 (scale)

WeaponStats

SpiritualWeaponStats

ArmorStats

RingStats

List<Item> (contents)

Dependencies (Uses/References):

ItemSpriteData

Notes: Data class for items. The constructor is a large switch statement that defines all stats, categories, sprites, and textures based on ItemType.

class: Inventory
Inherits: (None)

Implements: (None)

Composition (Owns/Creates):

Item (leftHand, rightHand)

Item[] (backpack, 6 slots)

Dependencies (Uses/References): (None)

Notes: A simple data container for the player's held items and backpack. Contains swap/rotate logic.

class: Door
Inherits: (None)

Implements: (None)

Composition (Owns/Creates):

DoorState (enum)

Notes: A state machine for doors (CLOSED, OPENING, OPEN). update() method handles the animation timer.

Other Data Classes
Difficulty.java (enum): Defines starting stats for EASY, MEDIUM, HARD.

Direction.java (enum): Defines NORTH, SOUTH, EAST, WEST and their vector/bitmask representations.

GameEvent.java: A simple data class for passing messages (String) and a duration (float) to the GameEventManager.

Gate.java, Ladder.java, Projectile.java: Simple data objects, primarily holding a position.

ItemColor.java, MonsterColor.java: Enums that map a name (e.g., TAN, BLUE) to a Color object and an XP/stat multiplier.

ItemSpriteData.java, MonsterSpriteData.java: Static data classes containing String[] arrays for 2D sprite rendering.

Renderable.java (interface): An interface guaranteeing getPosition() and getColor() methods for the EntityRenderer.

SpawnData.java: Static data class defining minLevel/maxLevel for items and monsters to be used by SpawnManager.

AttackPattern.java, DungeonTiles.java, ItemType.java: (Empty or unused classes).

Managers (managers)
class: CombatManager
Inherits: (None)

Implements: (None)

Composition (Owns/Creates):

CombatState (enum)

Player (Reference)

Monster (Reference)

Maze (Reference)

Tarmin2 (Reference)

AnimationManager (Reference)

GameEventManager (Reference)

SoundManager (Reference)

Dependencies (Uses/References):

Item

Animation

GameOverScreen

Notes: A state machine that manages turn-based combat. It handles attack logic for both player and monster, manages state transitions (PLAYER_TURN, MONSTER_TURN), and awards XP on victory.

class: SpawnManager
Inherits: (None)

Implements: (None)

Composition (Owns/Creates):

Maze (Reference)

Difficulty (Reference)

Random

List<GridPoint2> (validSpawnPoints)

Dependencies (Uses/References):

SpawnData

Monster

Item

ItemColor

MonsterColor

Notes: Responsible for populating the maze with monsters and items based on level, difficulty, and a "budget" system. Uses SpawnData to determine what to spawn.

class: GameEventManager
Inherits: (None)

Implements: (None)

Composition (Owns/Creates):

List<GameEvent>

Dependencies (Uses/References): (None)

Notes: A simple message queue. Manages a list of GameEvent objects, updating their timers and removing them when expired. The Hud reads from this list.

class: AnimationManager
Inherits: (None)

Implements: (None)

Composition (Owns/Creates):

List<Animation>

Dependencies (Uses/References):

ShapeRenderer

Player

Viewport

FirstPersonRenderer

Maze

Notes: Manages a list of active Animation objects (projectiles). Updates them, renders them, and removes them when finished.

class: DebugManager
Inherits: (None)

Implements: (None)

Composition (Owns/Creates):

RenderMode (enum)

Notes: A Singleton (getInstance()) that holds global debug state, such as isDebugOverlayVisible and RenderMode (MODERN vs. CLASSIC).

class: MusicManager
Inherits: (None)

Implements: com.badlogic.gdx.utils.Disposable

Composition (Owns/Creates):

com.badlogic.gdx.assets.AssetManager

Map<String, Music>

Music (currentTrack)

Dependencies (Uses/References):

SettingsManager

Notes: A Singleton (getInstance()) that manages loading, playing, and stopping background music. Volume is controlled by SettingsManager.

class: SoundManager
Inherits: (None)

Implements: com.badlogic.gdx.utils.Disposable

Composition (Owns/Creates):

DebugManager (Reference)

Map<String, Sound>

Dependencies (Uses/References):

SettingsManager

Monster

Notes: Manages loading and playing one-shot sound effects. Volume is controlled by SettingsManager.

class: SettingsManager
Inherits: (None)

Implements: (None)

Composition (Owns/Creates):

Preferences

Notes: A Singleton (getInstance()) that loads and saves game settings (like volume) to a Preferences file.

Other Manager Classes
AssetManager.java: (Empty, unused).

TextureManager.java: (Empty, unused).

Rendering (rendering)
class: FirstPersonRenderer
Inherits: (None)

Implements: (None)

Composition (Owns/Creates):

float[] (depthBuffer)

DebugManager (Singleton Reference)

SpriteBatch

Texture (wall, door, floor, skybox)

Dependencies (Uses/References):

Player

Maze

Viewport

Door

Gate

Notes: The core 3D rendering engine. Uses a DDA raycasting algorithm to draw walls, floors, and ceilings. It generates a depthBuffer used by EntityRenderer for occlusion.

class: EntityRenderer
Inherits: (None)

Implements: com.badlogic.gdx.utils.Disposable

Composition (Owns/Creates):

List<Renderable>

Dependencies (Uses/References):

ShapeRenderer

Player

Maze

Viewport

FirstPersonRenderer

Item

Monster

ItemSpriteData (via Hud)

MonsterSpriteData

Notes: Renders all entities (items, monsters). Sorts them by distance and uses the depthBuffer from FirstPersonRenderer to correctly draw them in front of or behind walls.

class: Hud
Inherits: (None)

Implements: com.badlogic.gdx.utils.Disposable

Composition (Owns/Creates):

Stage

Viewport

Player (Reference)

Maze (Reference)

CombatManager (Reference)

GameEventManager (Reference)

BitmapFont

SpriteBatch (Reference)

ShapeRenderer

Texture (hudBackground)

Label, Table, Actor (UI elements)

Dependencies (Uses/References):

DebugManager

Monster

Item

ItemSpriteData

Notes: Manages all 2D UI. Uses a Stage with Tables for layout. Also responsible for drawing the 2D inventory item sprites.

class: DebugRenderer
Inherits: (None)

Implements: (None)

Composition (Owns/Creates):

BitmapFont

Dependencies (Uses/References):

ShapeRenderer

Player

Maze

Viewport

Notes: Draws the 2D top-down map overlay when debug mode is active.

class: Animation
Inherits: (None)

Implements: (None)

Composition (Owns/Creates):

AnimationType (enum)

Vector2 (startPos, endPos, currentPos)

String[] (spriteData)

Notes: A data class representing a single projectile animation from a start to an end position.

Other Rendering Classes
ItemRenderer.java: (Empty, unused).

MonsterRenderer.java: (Empty, unused).

ProjectileRenderer.java: (Empty, unused).

Other Packages
package: generation
MazeGenerator.java: An alternate, more traditional maze generator (classic roguelike algorithm). It is not currently used by GameScreen, which uses its own tile-stitching method.

package: sound
AY38914.java, GameSoundEffectsGenerator.java, SoundDataParser.java, SoundEffect.java: A complex system for generating retro-style sound effects based on the Intellivision sound chip. This system is not currently used. SoundManager loads pre-made .wav and .mp3 files instead.
