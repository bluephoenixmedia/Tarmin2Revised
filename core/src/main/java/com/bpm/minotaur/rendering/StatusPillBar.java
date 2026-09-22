package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.ObjectMap;
import com.bpm.minotaur.gamedata.effects.ActiveStatusEffect;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.injury.InjuryRecord;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerStats;

/**
 * Persistent row of compact status pills displayed in the top-right field of view.
 * 
 * Displays active conditions (Bleed, Hypothermia, Chilled, Heatstroke, Poison, Fever,
 * Starvation, Dehydration, and tactical buffs) with color-coded dark-fantasy capsules.
 * Hovering any pill with the mouse displays a full descriptive tooltip card.
 */
public class StatusPillBar extends Table {

    private final HudSkin skin;
    private final HudTooltip tooltip;
    private final ObjectMap<String, Drawable> pillDrawables = new ObjectMap<>();
    private final java.util.List<Texture> ownedTextures = new java.util.ArrayList<>();

    public StatusPillBar(HudSkin skin, HudTooltip tooltip) {
        this.skin = skin;
        this.tooltip = tooltip;
        top().right();
    }

    public void update(Player player) {
        clearChildren();
        if (player == null) return;

        // 1. Bleed status
        if (player.getInjuryManager() != null && player.getInjuryManager().isBleeding()) {
            int maxSev = 1;
            for (InjuryRecord r : player.getInjuryManager().getInjuries().values()) {
                if (r != null && r.isBleeding() && r.getSeverity() > maxSev) {
                    maxSev = r.getSeverity();
                }
            }
            String labelText = "BLD " + (maxSev == 3 ? "III" : maxSev == 2 ? "II" : "I");
            Color col = HudSkin.COL_HP_CRITICAL;
            addPill(labelText, col,
                    "Bleeding Hemorrhage (Tier " + maxSev + ")",
                    "[TRAUMA]",
                    "Losing 1 HP every few steps to uncontrolled blood loss.",
                    "Apply direct pressure, spider silk sutures, or clean bandages.");
        }

        // 2. Temperature exposure
        if (player.getStats() != null) {
            float temp = player.getStats().getBodyTemperature();
            if (temp < 32.0f) {
                addPill(String.format("HYPO %.0f°", temp), Color.valueOf("8CD6FF"),
                        "Hypothermia Exposure",
                        "[ENVIRONMENT]",
                        "Extreme cold gnaws at your body (1 HP damage every 10 turns, blurred vision).",
                        "Stand near a lit Campfire or Lantern, drink warm broth, or equip warmth rings.");
            } else if (temp < 35.0f) {
                addPill(String.format("CHILL %.0f°", temp), HudSkin.COL_WATER_CYAN,
                        "Chilled Exposure",
                        "[ENVIRONMENT]",
                        "Bitter cold slows your reflexes (-20% move and attack speed).",
                        "Warm yourself by a campfire, equip heavier fur/armor, or seek shelter.");
            } else if (temp > 38.0f) {
                addPill(String.format("HEAT %.0f°", temp), HudSkin.COL_TEMP_ORANGE,
                        "Heatstroke / Hyperthermia",
                        "[ENVIRONMENT]",
                        "Overheating causes heavy perspiration (doubled thirst decay and exhaustion).",
                        "Drink cool water, rest in cooler biomes or shadow.");
            }
        }

        // 3. Poison & Toxicity
        if (player.getStatusManager() != null && player.getStatusManager().hasEffect(StatusEffectType.POISONED)) {
            ActiveStatusEffect eff = player.getStatusManager().getEffect(StatusEffectType.POISONED);
            int turns = (eff != null) ? eff.getDuration() : -1;
            String text = "PSN" + (turns > 0 ? " " + turns + "t" : "");
            addPill(text, Color.valueOf("44DD66"),
                    "Bloodstream Poisoning",
                    "[TOXIN]",
                    "Venom courses through your veins dealing damage over time.",
                    "Drink Antidote, Vitality potion, or rest at a Sanctuary Altar.");
        } else if (player.getStats() != null && player.getStats().getToxicity() > 50) {
            addPill("TOX " + player.getStats().getToxicity(), HudSkin.COL_TOX,
                    "High Body Toxicity",
                    "[TOXIN]",
                    "Hazardous toxin accumulation from tainted food, venom, or murky liquids.",
                    "Cleanse with water, pure rest, or anti-toxin elixirs.");
        }

        // 4. Disease / Fever / Sepsis
        if (player.getInjuryManager() != null && player.getInjuryManager().getIllnessStage().isIll()) {
            addPill("FEVER", Color.valueOf("E8C83A"),
                    "Systemic Fever / Wound Infection",
                    "[ILLNESS]",
                    "A festering wound has spread bacteria into the bloodstream.",
                    "Drink Vitality potion, apply Glowing Lichen, or rest in Shelter.");
        } else if (player.getStatusManager() != null && player.getStatusManager().hasEffect(StatusEffectType.SICK)) {
            addPill("SICK", Color.valueOf("D8B030"),
                    "Bodily Sickness",
                    "[ILLNESS]",
                    "Weakened physical constitution and degraded stamina.",
                    "Take medicinal curative potions or rest at sanctuary.");
        }

        // 5. Starvation & Dehydration
        if (player.getStats() != null) {
            PlayerStats stats = player.getStats();
            if (stats.getSatietyFloat() <= 0f) {
                addPill("STARV", HudSkin.COL_HP_CRITICAL,
                        "Severe Starvation",
                        "[SURVIVAL]",
                        "Your body is starving to death! Taking 1 true damage every 25 turns.",
                        "Consume rations, dried meat, or cooked food immediately.");
            } else if (stats.getSatiationState() == PlayerStats.SatiationState.HUNGRY) {
                addPill("HUNGRY", HudSkin.COL_TEMP_ORANGE,
                        "Hunger Pang",
                        "[SURVIVAL]",
                        "Your stomach growls. Natural HP regeneration is suppressed.",
                        "Eat food before starvation sets in.");
            }

            if (stats.getHydrationFloat() <= 0f) {
                addPill("PARCHED", HudSkin.COL_HP_CRITICAL,
                        "Extreme Dehydration",
                        "[SURVIVAL]",
                        "You are parched with thirst! Taking 1 true damage every 20 turns.",
                        "Drink fresh water or water flask immediately.");
            }
        }

        // 6. Tactical Boons & Buffs
        if (player.getStatusManager() != null) {
            if (player.getStatusManager().hasEffect(StatusEffectType.HASTED)) {
                ActiveStatusEffect eff = player.getStatusManager().getEffect(StatusEffectType.HASTED);
                int turns = (eff != null) ? eff.getDuration() : -1;
                addPill("HASTE" + (turns > 0 ? " " + turns + "t" : ""), HudSkin.COL_GOLD_BRIGHT,
                        "Haste", "[TACTICAL BOON]", "Movement and action speed doubled.", "Ends with lethargy.");
            }
            if (player.getStatusManager().hasEffect(StatusEffectType.HARDENED)) {
                addPill("IRONSKIN", HudSkin.COL_GOLD_BRIGHT,
                        "Iron Skin", "[PROTECTION]", "Hardened skin absorbs physical impacts (+AC).", "Temporary magical ward.");
            }
            if (player.getStatusManager().hasEffect(StatusEffectType.BERZERK)) {
                addPill("RAGE", HudSkin.COL_HP_CRITICAL,
                        "Berzerk Bloodlust", "[BOON]", "Ferocious melee damage boost at cost of incoming fatigue.", "Ends in exhaustion.");
            }
            if (player.getStatusManager().hasEffect(StatusEffectType.MOTE_OF_LIGHT)) {
                addPill("LIGHT", Color.valueOf("FFF099"),
                        "Mote of Light", "[ENCHANTMENT]", "Radiating pure ambient light through the darkness.", "Illuminates corridors.");
            }
            if (player.getStatusManager().hasEffect(StatusEffectType.HEROISM)) {
                addPill("HEROIC", HudSkin.COL_GOLD_BRIGHT,
                        "Heroism", "[BOON]", "Immunity to fear and grants temporary vitality.", "Courageous aura.");
            }
        }

        pack();
        // Directly beneath the minimap's reserved box, not on top of it. Both widgets used to
        // anchor themselves to the top-right corner independently, so the bleeding badge was
        // drawn over the map and clipped at the screen edge.
        setPosition(Hud.STAGE_WIDTH - Hud.MINIMAP_MARGIN_RIGHT - getWidth(),
                Hud.minimapZoneBottom() - 12f - getHeight());
    }

    private void addPill(String labelText, Color accentColor, String tooltipTitle, String tooltipCategory,
                         String tooltipDesc, String tooltipRemedy) {
        Table pill = new Table();
        pill.setBackground(getPillBackground(accentColor));
        pill.pad(3f, 8f, 3f, 8f);

        Label label = new Label(labelText, new Label.LabelStyle(skin.getFontSmall(), accentColor));
        pill.add(label);

        pill.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (tooltip != null) {
                    Vector2 screenPos = pill.localToStageCoordinates(new Vector2(0, 0));
                    tooltip.showStatus(tooltipTitle, tooltipCategory, tooltipDesc, tooltipRemedy,
                            screenPos.x + pill.getWidth() / 2f, screenPos.y);
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (tooltip != null) {
                    tooltip.hide();
                }
            }
        });

        add(pill).padLeft(6f);
    }

    private Drawable getPillBackground(Color color) {
        String key = color.toString();
        if (pillDrawables.containsKey(key)) {
            return pillDrawables.get(key);
        }

        int sz = 16;
        Pixmap p = new Pixmap(sz, sz, Pixmap.Format.RGBA8888);

        // Recessed deep dark core
        p.setColor(new Color(0.06f, 0.05f, 0.04f, 0.92f));
        p.fill();

        // Colored 1px perimeter border
        p.setColor(color.r, color.g, color.b, 0.85f);
        p.drawRectangle(0, 0, sz, sz);

        Texture tex = new Texture(p);
        ownedTextures.add(tex);
        p.dispose();

        NinePatch np = new NinePatch(tex, 3, 3, 3, 3);
        Drawable d = new NinePatchDrawable(np);
        pillDrawables.put(key, d);
        return d;
    }

    public void dispose() {
        for (Texture t : ownedTextures) {
            t.dispose();
        }
        ownedTextures.clear();
        pillDrawables.clear();
    }
}
