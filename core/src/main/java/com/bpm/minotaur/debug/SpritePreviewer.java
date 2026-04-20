package com.bpm.minotaur.debug;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterDataManager;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;

public class SpritePreviewer extends ApplicationAdapter {

    private Stage stage;
    private Skin skin;
    private MonsterDataManager monsterDataManager;
    private AssetManager assetManager;
    private ShapeRenderer shapeRenderer;
    private Monster.MonsterType currentType;

    @Override
    public void create() {
        // 1. Setup Rendering
        shapeRenderer = new ShapeRenderer();
        assetManager = new AssetManager();

        // 2. Load Data
        monsterDataManager = new MonsterDataManager();
        monsterDataManager.load();
        monsterDataManager.queueAssets(assetManager);
        assetManager.finishLoading();

        // 3. Setup UI
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        createBasicSkin();

        // 4. Create Layout
        Table root = new Table();
        root.setFillParent(true);
        root.top().left();
        stage.addActor(root);

        // 5. Dropdown
        Label label = new Label("Select Monster:", skin);
        final SelectBox<Monster.MonsterType> selectBox = new SelectBox<>(skin);
        selectBox.setItems(Monster.MonsterType.values());

        // Listener
        selectBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                currentType = selectBox.getSelected();
            }
        });

        // Set default
        currentType = selectBox.getSelected();

        root.add(label).pad(10);
        root.add(selectBox).width(300).pad(10);
    }

    private void createBasicSkin() {
        skin = new Skin();

        // Generate a 1x1 white texture and store it in the skin named "white"
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        skin.add("white", new Texture(pixmap));

        // Store the default libgdx font
        skin.add("default", new BitmapFont());

        // Configure a TextButton style
        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = skin.getFont("default");
        skin.add("default", labelStyle);

        // Configure SelectBox style
        SelectBox.SelectBoxStyle selectBoxStyle = new SelectBox.SelectBoxStyle();
        selectBoxStyle.font = skin.getFont("default");
        selectBoxStyle.fontColor = Color.WHITE;
        selectBoxStyle.listStyle = new com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle();
        selectBoxStyle.listStyle.font = skin.getFont("default");
        selectBoxStyle.listStyle.fontColorSelected = Color.WHITE;
        selectBoxStyle.listStyle.fontColorUnselected = Color.WHITE;
        selectBoxStyle.listStyle.selection = new TextureRegionDrawable(new TextureRegion(new Texture(pixmap)));

        // Background for the list
        Pixmap listBg = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        listBg.setColor(Color.DARK_GRAY);
        listBg.fill();
        selectBoxStyle.listStyle.background = new TextureRegionDrawable(new TextureRegion(new Texture(listBg)));

        selectBoxStyle.scrollStyle = new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle();

        // Background for the select box itself
        Pixmap sbBg = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        sbBg.setColor(Color.GRAY);
        sbBg.fill();
        selectBoxStyle.background = new TextureRegionDrawable(new TextureRegion(new Texture(sbBg)));

        skin.add("default", selectBoxStyle);
    }

    @Override
    public void render() {
        ScreenUtils.clear(0.1f, 0.1f, 0.1f, 1);

        // Draw UI
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();

        // Draw Sprite
        if (currentType != null) {
            drawMonsterSprite(currentType);
        }
    }

    private void drawMonsterSprite(Monster.MonsterType type) {
        MonsterTemplate template = monsterDataManager.getTemplate(type);
        if (template == null || template.spriteData == null)
            return;

        String[] spriteData = template.spriteData;

        // Render Settings
        int pixelSize = 10; // Zoom factor
        int startX = Gdx.graphics.getWidth() / 2 - (spriteData[0].length() * pixelSize) / 2;
        int startY = Gdx.graphics.getHeight() / 2 + (spriteData.length * pixelSize) / 2;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (int row = 0; row < spriteData.length; row++) {
            String line = spriteData[row];
            for (int col = 0; col < line.length(); col++) {
                char charCode = line.charAt(col);
                if (charCode != '.') {
                    Color color = getPixelColor(charCode, Color.GRAY); // Default gray for basic '#'
                    shapeRenderer.setColor(color);
                    shapeRenderer.rect(startX + col * pixelSize, startY - row * pixelSize, pixelSize, pixelSize);
                }
            }
        }

        shapeRenderer.end();
    }

    // Copied from EntityRenderer for preview consistency
    private Color getPixelColor(char pixelChar, Color defaultColor) {
        switch (pixelChar) {
            case '#':
                return defaultColor;
            case 'R':
                return Color.RED;
            case 'G':
                return Color.GREEN;
            case 'L':
                return Color.LIME;
            case 'B':
                return Color.BLUE;
            case 'Y':
                return Color.YELLOW;
            case 'W':
                return Color.WHITE;
            case 'K':
                return Color.BLACK;
            case 'O':
                return Color.ORANGE;
            case 'P':
                return Color.PURPLE;
            case 'M':
                return Color.MAGENTA;
            case 'C':
                return Color.CYAN;
            case 'S':
                return Color.GRAY;
            case 'D':
                return Color.GOLD;
            case 'T':
                return Color.TEAL;
            default:
                return defaultColor;
        }
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
        shapeRenderer.dispose();
        assetManager.dispose();
    }
}
