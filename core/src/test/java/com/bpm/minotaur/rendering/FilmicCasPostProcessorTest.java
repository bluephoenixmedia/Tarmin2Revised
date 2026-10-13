package com.bpm.minotaur.rendering;

import org.junit.Test;

import static org.junit.Assert.*;

public class FilmicCasPostProcessorTest {

    @Test
    public void testDefaultParametersAndMutators() {
        FilmicCasPostProcessor processor = new FilmicCasPostProcessor();

        assertEquals(0.45f, processor.getSharpness(), 0.001f);
        assertEquals(0.0f, processor.getGrain(), 0.001f);

        processor.setSharpness(0.90f);
        assertEquals(0.90f, processor.getSharpness(), 0.001f);

        processor.setGrain(0.05f);
        assertEquals(0.05f, processor.getGrain(), 0.001f);

        processor.dispose();
        assertFalse("Processor shader should not be compiled after dispose", processor.isCompiled());
    }
}
