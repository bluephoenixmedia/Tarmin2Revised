package com.bpm.minotaur.generation;

/**
 * AY-3-8914 Sound Chip Emulator
 *
 * This class emulates the General Instrument AY-3-8914 Programmable Sound Generator,
 * a popular sound chip used in many 8-bit computers and arcade machines.
 *
 * Features:
 * - 3 tone generators (channels A, B, C)
 * - 1 noise generator
 * - 1 envelope generator with 16 different shapes
 * - Mixer for combining tone and noise per channel
 * - 16 volume levels per channel
 * - Standard register interface
 */
public class AY38914 {

    // Clock frequency (typically 1.0 MHz for original chip)
    private static final int CLOCK_FREQUENCY = 1000000;

    // Register definitions
    public static final int REG_TONE_A_FINE = 0x00;
    public static final int REG_TONE_A_COARSE = 0x01;
    public static final int REG_TONE_B_FINE = 0x02;
    public static final int REG_TONE_B_COARSE = 0x03;
    public static final int REG_TONE_C_FINE = 0x04;
    public static final int REG_TONE_C_COARSE = 0x05;
    public static final int REG_NOISE_PERIOD = 0x06;
    public static final int REG_MIXER = 0x07;
    public static final int REG_VOLUME_A = 0x08;
    public static final int REG_VOLUME_B = 0x09;
    public static final int REG_VOLUME_C = 0x0A;
    public static final int REG_ENV_FINE = 0x0B;
    public static final int REG_ENV_COARSE = 0x0C;
    public static final int REG_ENV_SHAPE = 0x0D;
    public static final int REG_IO_PORT_A = 0x0E;
    public static final int REG_IO_PORT_B = 0x0F;

    // Envelope shapes
    public static final int ENV_CONTINUE = 0x08;
    public static final int ENV_ATTACK = 0x04;
    public static final int ENV_ALTERNATE = 0x02;
    public static final int ENV_HOLD = 0x01;

    // Internal state
    private int[] registers = new int[16];
    private int sampleRate;
    private double clockDivider;

    // Tone generators
    private int[] toneCounters = new int[3];
    private int[] tonePeriods = new int[3];
    private boolean[] toneOutputs = new boolean[3];

    // Noise generator
    private int noiseCounter;
    private int noisePeriod;
    private int noiseShiftRegister = 1;
    private boolean noiseOutput;

    // Envelope generator
    private int envelopeCounter;
    private int envelopePeriod;
    private int envelopeStep;
    private int envelopeShape;
    private boolean envelopeHolding;

    // Volume table (logarithmic approximation)
    private static final int[] VOLUME_TABLE = {
        0, 836, 1212, 1773, 2619, 3875, 5397, 8823,
        10392, 16706, 23339, 29292, 36969, 46421, 55195, 65535
    };

    /**
     * Constructor
     * @param sampleRate Audio sample rate (e.g., 44100)
     */
    public AY38914(int sampleRate) {
        this.sampleRate = sampleRate;
        this.clockDivider = (double) CLOCK_FREQUENCY / (sampleRate * 8);
        reset();
    }

    /**
     * Reset the chip to initial state
     */
    public void reset() {
        // Clear all registers
        for (int i = 0; i < 16; i++) {
            registers[i] = 0;
        }

        // Reset tone generators
        for (int i = 0; i < 3; i++) {
            toneCounters[i] = 0;
            tonePeriods[i] = 1;
            toneOutputs[i] = false;
        }

        // Reset noise generator
        noiseCounter = 0;
        noisePeriod = 1;
        noiseShiftRegister = 1;
        noiseOutput = false;

        // Reset envelope generator
        envelopeCounter = 0;
        envelopePeriod = 1;
        envelopeStep = 0;
        envelopeShape = 0;
        envelopeHolding = false;

        // Set mixer to disable all channels initially
        registers[REG_MIXER] = 0x3F;
    }

    /**
     * Write to a register
     * @param register Register address (0-15)
     * @param value Value to write (0-255)
     */
    public void writeRegister(int register, int value) {
        if (register < 0 || register > 15) return;

        registers[register] = value & 0xFF;

        // Update internal state based on register
        switch (register) {
            case REG_TONE_A_FINE:
            case REG_TONE_A_COARSE:
                updateTonePeriod(0);
                break;
            case REG_TONE_B_FINE:
            case REG_TONE_B_COARSE:
                updateTonePeriod(1);
                break;
            case REG_TONE_C_FINE:
            case REG_TONE_C_COARSE:
                updateTonePeriod(2);
                break;
            case REG_NOISE_PERIOD:
                noisePeriod = Math.max(1, registers[REG_NOISE_PERIOD] & 0x1F);
                break;
            case REG_ENV_FINE:
            case REG_ENV_COARSE:
                updateEnvelopePeriod();
                break;
            case REG_ENV_SHAPE:
                envelopeShape = registers[REG_ENV_SHAPE] & 0x0F;
                envelopeStep = 0;
                envelopeHolding = false;
                envelopeCounter = 0;
                break;
        }
    }

    /**
     * Read from a register
     * @param register Register address (0-15)
     * @return Register value
     */
    public int readRegister(int register) {
        if (register < 0 || register > 15) return 0;
        return registers[register];
    }

    /**
     * Update tone period for a channel
     */
    private void updateTonePeriod(int channel) {
        int fineReg = REG_TONE_A_FINE + (channel * 2);
        int coarseReg = REG_TONE_A_COARSE + (channel * 2);
        int period = registers[fineReg] | ((registers[coarseReg] & 0x0F) << 8);
        tonePeriods[channel] = Math.max(1, period);
    }

    /**
     * Update envelope period
     */
    private void updateEnvelopePeriod() {
        int period = registers[REG_ENV_FINE] | (registers[REG_ENV_COARSE] << 8);
        envelopePeriod = Math.max(1, period);
    }

    /**
     * Generate the next audio sample
     * @return 16-bit signed stereo sample (left and right channels mixed)
     */
    public short generateSample() {
        updateGenerators();

        int mixedOutput = 0;
        int mixer = registers[REG_MIXER];

        for (int channel = 0; channel < 3; channel++) {
            boolean toneEnabled = (mixer & (1 << channel)) == 0;
            boolean noiseEnabled = (mixer & (1 << (channel + 3))) == 0;

            boolean output = true;
            if (toneEnabled) output &= toneOutputs[channel];
            if (noiseEnabled) output &= noiseOutput;

            if (output) {
                int volume = registers[REG_VOLUME_A + channel];
                int amplitude;

                if ((volume & 0x10) != 0) {
                    // Use envelope
                    amplitude = VOLUME_TABLE[getEnvelopeOutput()];
                } else {
                    // Use fixed volume
                    amplitude = VOLUME_TABLE[volume & 0x0F];
                }

                mixedOutput += amplitude;
            }
        }

        // Scale and clip output
        mixedOutput = Math.min(65535, Math.max(-65536, mixedOutput - 32768));
        return (short) mixedOutput;
    }

    /**
     * Update all internal generators
     */
    private void updateGenerators() {
        // Update tone generators
        for (int i = 0; i < 3; i++) {
            toneCounters[i] += clockDivider;
            if (toneCounters[i] >= tonePeriods[i]) {
                toneCounters[i] -= tonePeriods[i];
                toneOutputs[i] = !toneOutputs[i];
            }
        }

        // Update noise generator
        noiseCounter += clockDivider;
        if (noiseCounter >= noisePeriod) {
            noiseCounter -= noisePeriod;

            // 17-bit LFSR
            int feedback = ((noiseShiftRegister & 1) ^ ((noiseShiftRegister >> 3) & 1)) & 1;
            noiseShiftRegister = (noiseShiftRegister >> 1) | (feedback << 16);
            noiseOutput = (noiseShiftRegister & 1) != 0;
        }

        // Update envelope generator
        if (!envelopeHolding) {
            envelopeCounter += clockDivider;
            if (envelopeCounter >= envelopePeriod) {
                envelopeCounter -= envelopePeriod;
                envelopeStep++;

                if (envelopeStep >= 32) {
                    if ((envelopeShape & ENV_CONTINUE) != 0) {
                        if ((envelopeShape & ENV_HOLD) != 0) {
                            envelopeHolding = true;
                        } else {
                            envelopeStep = 0;
                        }
                    } else {
                        envelopeStep = 0;
                        envelopeHolding = true;
                    }
                }
            }
        }
    }

    /**
     * Get current envelope output level (0-15)
     */
    private int getEnvelopeOutput() {
        if (envelopeHolding) {
            boolean attack = (envelopeShape & ENV_ATTACK) != 0;
            return attack ? 15 : 0;
        }

        int step = envelopeStep;
        boolean attack = (envelopeShape & ENV_ATTACK) != 0;
        boolean alternate = (envelopeShape & ENV_ALTERNATE) != 0;

        if (step >= 16) {
            if (alternate) {
                attack = !attack;
            }
            step = step % 16;
        }

        int level = attack ? step : (15 - step);
        return Math.max(0, Math.min(15, level));
    }

    /**
     * Set the master clock frequency
     * @param frequency Clock frequency in Hz
     */
    public void setClockFrequency(int frequency) {
        this.clockDivider = (double) frequency / (sampleRate * 8);
    }

    /**
     * Get current register values as a string for debugging
     */
    public String getRegisterDump() {
        StringBuilder sb = new StringBuilder();
        sb.append("AY-3-8914 Register Dump:\n");
        for (int i = 0; i < 16; i++) {
            sb.append(String.format("R%02X: %02X ", i, registers[i]));
            if ((i + 1) % 4 == 0) sb.append("\n");
        }
        return sb.toString();
    }
}
