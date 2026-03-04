package com.therushlight.rendering;

import com.therushlight.engine.Window;
import com.therushlight.narrative.Choice;
import com.therushlight.narrative.DialogueNode;
import com.therushlight.narrative.DialogueRunner;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Renders dialogue boxes, choices, typewriter text, timer bar, and notifications.
 * Uses a text shader that renders characters as small colored blocks
 * (placeholder until proper font rendering with STB truetype).
 */
public class DialogueRenderer {

    private final Window window;
    private int quadVAO, quadVBO;
    private int shaderProgram;

    // Dialogue box dimensions (relative to screen)
    private static final float BOX_HEIGHT_RATIO = 0.28f;
    private static final float BOX_MARGIN = 20;
    private static final float TEXT_MARGIN = 30;
    private static final float CHAR_WIDTH = 10;
    private static final float CHAR_HEIGHT = 16;
    private static final float LINE_SPACING = 22;

    private static final String VERTEX_SHADER = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            uniform vec4 uRect;
            uniform vec2 uScreen;
            void main() {
                vec2 pos = aPos * uRect.zw + uRect.xy;
                vec2 ndc = (pos / uScreen) * 2.0 - 1.0;
                ndc.y = -ndc.y;
                gl_Position = vec4(ndc, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 330 core
            out vec4 FragColor;
            uniform vec4 uColor;
            void main() {
                FragColor = uColor;
            }
            """;

    public DialogueRenderer(Window window) {
        this.window = window;
        initGL();
    }

    private void initGL() {
        float[] verts = { 0,0, 1,0, 1,1, 0,0, 1,1, 0,1 };

        quadVAO = glGenVertexArrays();
        quadVBO = glGenBuffers();
        glBindVertexArray(quadVAO);
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
        glBufferData(GL_ARRAY_BUFFER, verts, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0);
        glBindVertexArray(0);

        shaderProgram = compileShader(VERTEX_SHADER, FRAGMENT_SHADER);
    }

    public void render(DialogueRunner runner) {
        if (runner.getCurrentNode() == null) return;

        float screenW = window.getWidth();
        float screenH = window.getHeight();
        float boxH = screenH * BOX_HEIGHT_RATIO;
        float boxY = screenH - boxH - BOX_MARGIN;
        float boxW = screenW - BOX_MARGIN * 2;

        // Dialogue box background (dark, semi-transparent)
        drawRect(BOX_MARGIN, boxY, boxW, boxH, 0.02f, 0.02f, 0.04f, 0.85f);

        // Speaker name
        DialogueNode node = runner.getCurrentNode();
        String speaker = node.getSpeaker();
        if (speaker != null && !speaker.isEmpty()) {
            // Speaker name bar
            float nameW = speaker.length() * CHAR_WIDTH + 20;
            drawRect(BOX_MARGIN + 10, boxY - 28, nameW, 26, 0.15f, 0.12f, 0.08f, 0.9f);
            drawText(speaker.toUpperCase(), BOX_MARGIN + 20, boxY - 24,
                    getSpeakerColor(speaker), 1.0f);
        }

        // Dialogue text (typewriter)
        String text = runner.getRevealedText();
        drawWrappedText(text, BOX_MARGIN + TEXT_MARGIN, boxY + TEXT_MARGIN,
                boxW - TEXT_MARGIN * 2, 0.85f, 0.82f, 0.78f, 1.0f);

        // Continue indicator
        if (runner.isWaitingForAdvance() && runner.isTextFullyRevealed()) {
            float pulseAlpha = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 300.0));
            drawRect(BOX_MARGIN + boxW - 30, boxY + boxH - 25, 10, 10,
                    0.7f, 0.6f, 0.4f, pulseAlpha);
        }

        // Choices
        if (runner.isWaitingForChoice() && runner.isTextFullyRevealed()) {
            renderChoices(runner, boxY, boxW);
        }
    }

    private void renderChoices(DialogueRunner runner, float boxY, float boxW) {
        List<Choice> choices = runner.getValidChoices();
        int selected = runner.getSelectedChoice();

        float choiceY = boxY - 10 - choices.size() * 40;
        float choiceX = window.getWidth() * 0.3f;
        float choiceW = window.getWidth() * 0.4f;

        for (int i = 0; i < choices.size(); i++) {
            Choice choice = choices.get(i);
            boolean isSelected = (i == selected);

            float bgAlpha = isSelected ? 0.7f : 0.4f;
            float r = isSelected ? 0.2f : 0.08f;
            float g = isSelected ? 0.15f : 0.06f;
            float b = isSelected ? 0.1f : 0.05f;

            // Choice background
            drawRect(choiceX, choiceY + i * 40, choiceW, 35, r, g, b, bgAlpha);

            // Selection indicator
            if (isSelected) {
                drawRect(choiceX, choiceY + i * 40, 4, 35, 0.8f, 0.6f, 0.3f, 1.0f);
            }

            // Choice text
            String label = (i + 1) + ". " + choice.getText();
            float textR = isSelected ? 1.0f : 0.6f;
            float textG = isSelected ? 0.9f : 0.55f;
            float textB = isSelected ? 0.7f : 0.5f;
            drawText(label, choiceX + 15, choiceY + i * 40 + 10, textR, textG, textB, 1.0f);
        }

        // Timer bar
        if (runner.getChoiceTimerMax() > 0 && runner.getChoiceTimer() > 0) {
            float timerRatio = runner.getChoiceTimer() / runner.getChoiceTimerMax();
            float timerW = choiceW * timerRatio;

            // Background
            drawRect(choiceX, choiceY - 12, choiceW, 6, 0.1f, 0.1f, 0.1f, 0.5f);
            // Fill (red as time runs out)
            float tr = 1.0f - timerRatio;
            float tg = timerRatio;
            drawRect(choiceX, choiceY - 12, timerW, 6, tr, tg, 0.1f, 0.8f);
        }
    }

    public void renderNotifications(DialogueRunner runner) {
        float y = window.getHeight() * 0.15f;
        for (var notification : runner.getNotifications()) {
            float alpha = notification.getAlpha();
            String text = notification.getText();

            // Italic-style notification in top-center
            float textW = text.length() * CHAR_WIDTH;
            float x = (window.getWidth() - textW) / 2;

            drawText(text, x, y, 0.7f, 0.6f, 0.5f, alpha);
            y += 30;
        }
    }

    // --- Text rendering (placeholder: small rectangles per character) ---

    private void drawText(String text, float x, float y, float r, float g, float b, float a) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != ' ') {
                drawRect(x + i * CHAR_WIDTH, y, CHAR_WIDTH - 2, CHAR_HEIGHT - 2, r, g, b, a);
            }
        }
    }

    private void drawText(String text, float x, float y, float[] color, float alpha) {
        drawText(text, x, y, color[0], color[1], color[2], alpha);
    }

    private void drawWrappedText(String text, float x, float y, float maxWidth,
                                  float r, float g, float b, float a) {
        int charsPerLine = Math.max(1, (int) (maxWidth / CHAR_WIDTH));
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        float lineY = y;

        for (String word : words) {
            if (line.length() + word.length() + 1 > charsPerLine) {
                drawText(line.toString(), x, lineY, r, g, b, a);
                lineY += LINE_SPACING;
                line = new StringBuilder();
            }
            if (line.length() > 0) line.append(" ");
            line.append(word);
        }
        if (line.length() > 0) {
            drawText(line.toString(), x, lineY, r, g, b, a);
        }
    }

    private float[] getSpeakerColor(String speaker) {
        return switch (speaker.toLowerCase()) {
            case "drew" -> new float[]{0.9f, 0.7f, 0.4f};
            case "rush" -> new float[]{0.4f, 0.7f, 0.9f};
            case "lu", "luisa", "luísa" -> new float[]{0.9f, 0.5f, 0.7f};
            case "yen", "yenevieve" -> new float[]{0.8f, 0.8f, 0.5f};
            case "shepherd" -> new float[]{0.7f, 0.55f, 0.35f}; // Warm brown. Close to Drew's gold. They look alike from far away.
            case "kimiru" -> new float[]{0.5f, 0.5f, 0.55f}; // Muted grey-blue. Otherworldly.
            case "narrator" -> new float[]{0.6f, 0.6f, 0.6f};
            default -> new float[]{0.7f, 0.7f, 0.7f};
        };
    }

    private void drawRect(float x, float y, float w, float h, float r, float g, float b, float a) {
        glUseProgram(shaderProgram);
        glUniform4f(glGetUniformLocation(shaderProgram, "uRect"), x, y, w, h);
        glUniform2f(glGetUniformLocation(shaderProgram, "uScreen"), window.getWidth(), window.getHeight());
        glUniform4f(glGetUniformLocation(shaderProgram, "uColor"), r, g, b, a);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
        glUseProgram(0);
    }

    private int compileShader(String vertSrc, String fragSrc) {
        int vert = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vert, vertSrc);
        glCompileShader(vert);

        int frag = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(frag, fragSrc);
        glCompileShader(frag);

        int prog = glCreateProgram();
        glAttachShader(prog, vert);
        glAttachShader(prog, frag);
        glLinkProgram(prog);

        glDeleteShader(vert);
        glDeleteShader(frag);
        return prog;
    }

    public void cleanup() {
        glDeleteVertexArrays(quadVAO);
        glDeleteBuffers(quadVBO);
        glDeleteProgram(shaderProgram);
    }
}
