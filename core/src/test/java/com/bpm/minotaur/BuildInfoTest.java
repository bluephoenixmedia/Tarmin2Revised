package com.bpm.minotaur;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/** The version on the title screen has to be the version that was built. */
public class BuildInfoTest {

    @Test
    public void versionMatchesGradleProperties() throws IOException {
        File f = new File("gradle.properties");
        if (!f.exists()) {
            f = new File("../gradle.properties");
        }
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(f)) {
            props.load(in);
        }
        String fromGradle = props.getProperty("projectVersion");
        assertNotNull("gradle.properties has no projectVersion", fromGradle);
        assertEquals("BuildInfo.VERSION has drifted from gradle.properties -- update both on release",
                fromGradle.trim(), BuildInfo.VERSION);
    }
}
