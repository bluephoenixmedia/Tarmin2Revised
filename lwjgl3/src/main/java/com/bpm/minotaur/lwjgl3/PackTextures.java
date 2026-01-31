package com.bpm.minotaur.lwjgl3;

import com.badlogic.gdx.tools.texturepacker.TexturePacker;

public class PackTextures {
    public static void main(String[] args) {
        TexturePacker.Settings settings = new TexturePacker.Settings();
        settings.maxWidth = 4096;
        settings.maxHeight = 4096;
        settings.edgePadding = false;
        settings.duplicatePadding = true; // Fix bleeding
        settings.paddingX = 2;
        settings.paddingY = 2;
        settings.stripWhitespaceX = true;
        settings.stripWhitespaceY = true;

        // Pack Gore
        System.out.println("Packing Gore assets...");
        // Path logic notes:
        // gradle task workingDir = assets/
        // args to TexturePacker.process are relative to workingDir (usually) or
        // absolute.
        // If workingDir is assets folder:
        // Input: "images/gore"
        // Output: "packed"
        // Name: "gore"

        try {
            TexturePacker.process(settings, "assets/images/gore", "assets/packed", "gore");
            System.out.println("Textures packed successfully to assets/packed/gore.atlas");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        // Pack Debris
        System.out.println("Packing Debris assets...");
        try {
            TexturePacker.process(settings, "assets/images/debris", "assets/packed", "debris");
            System.out.println("Textures packed successfully to assets/packed/debris.atlas");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        // Pack Items
        System.out.println("Packing Items assets...");
        try {
            TexturePacker.process(settings, "assets/images/items", "assets/packed", "items");
            System.out.println("Textures packed successfully to assets/packed/items.atlas");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        // Pack Armor
        System.out.println("Packing Armor assets...");
        try {
            TexturePacker.process(settings, "assets/images/armor", "assets/packed", "armor");
            System.out.println("Textures packed successfully to assets/packed/armor.atlas");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        // Pack Weapons
        System.out.println("Packing Weapon assets...");
        try {
            TexturePacker.process(settings, "assets/images/weapons", "assets/packed", "weapons");
            System.out.println("Textures packed successfully to assets/packed/weapons.atlas");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        // Pack Portraits
        System.out.println("Packing Portrait assets...");
        try {
            TexturePacker.process(settings, "assets/images/portrait", "assets/packed", "portrait");
            System.out.println("Textures packed successfully to assets/packed/portrait.atlas");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
