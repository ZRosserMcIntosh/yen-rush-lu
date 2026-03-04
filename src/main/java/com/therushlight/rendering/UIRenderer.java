package com.therushlight.rendering;

import com.therushlight.engine.Window;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Renders UI elements — title screen, pause menu, chapter title cards.
 */
public class UIRenderer {

    private final Window window;
    private int quadVAO, quadVBO;
    private int shaderProgram;

    private static final float CHAR_W = 12;
    private static final float CHAR_H = 18;

    private static final String VERT = """
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

    private static final String FRAG = """
            #version 330 core
            out vec4 FragColor;
            uniform vec4 uColor;
            void main() {
                FragColor = uColor;
            }
            """;

    public UIRenderer(Window window) {
        this.window = window;

        float[] verts = { 0,0, 1,0, 1,1, 0,0, 1,1, 0,1 };
        quadVAO = glGenVertexArrays();
        quadVBO = glGenBuffers();
        glBindVertexArray(quadVAO);
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
        glBufferData(GL_ARRAY_BUFFER, verts, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0);
        glBindVertexArray(0);

        int vert = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vert, VERT);
        glCompileShader(vert);

        int frag = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(frag, FRAG);
        glCompileShader(frag);

        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vert);
        glAttachShader(shaderProgram, frag);
        glLinkProgram(shaderProgram);
        glDeleteShader(vert);
        glDeleteShader(frag);
    }

    /**
     * Title screen — "ON TIME" centered, with a tagline and prompt.
     */
    public void renderTitleScreen() {
        float sw = window.getWidth();
        float sh = window.getHeight();

        // Dark warm background
        drawRect(0, 0, sw, sh, 0.03f, 0.02f, 0.02f, 1.0f);

        // Title: ON TIME
        String title = "ON TIME";
        float titleScale = 2.5f;
        float titleW = title.length() * CHAR_W * titleScale;
        float titleX = (sw - titleW) / 2;
        float titleY = sh * 0.3f;
        drawTextScaled(title, titleX, titleY, titleScale, 0.85f, 0.65f, 0.35f, 1.0f);

        // Tagline
        String tagline = "A small light is still a light.";
        float tagW = tagline.length() * CHAR_W;
        float tagX = (sw - tagW) / 2;
        drawText(tagline, tagX, titleY + 60, 0.55f, 0.45f, 0.35f, 0.7f);

        // Pulsing prompt
        float pulse = (float) (0.4 + 0.3 * Math.sin(System.currentTimeMillis() / 500.0));
        String prompt = "Press SPACE to begin";
        float promptW = prompt.length() * CHAR_W;
        float promptX = (sw - promptW) / 2;
        drawText(prompt, promptX, sh * 0.65f, 0.6f, 0.5f, 0.4f, pulse);

        // Small flame (the rushlight)
        float flameX = sw / 2 - 3;
        float flameY = titleY - 40;
        float flicker = (float) (1.0 + 0.15 * Math.sin(System.currentTimeMillis() / 120.0));
        drawRect(flameX - 1, flameY, 8 * flicker, 12 * flicker, 0.9f, 0.6f, 0.2f, 0.9f);
        drawRect(flameX, flameY + 4, 5 * flicker, 6 * flicker, 1.0f, 0.85f, 0.4f, 0.7f);

        // Credits
        String credit = "For Yen, Rush, and Lu";
        float creditW = credit.length() * CHAR_W * 0.7f;
        drawTextScaled(credit, (sw - creditW) / 2, sh * 0.88f, 0.7f, 0.4f, 0.35f, 0.3f, 0.5f);
    }

    /**
     * Chapter title card — "Chapter 1" and title, centered on black.
     */
    public void renderChapterTitle(int number, String title) {
        float sw = window.getWidth();
        float sh = window.getHeight();

        // Black
        drawRect(0, 0, sw, sh, 0.0f, 0.0f, 0.0f, 1.0f);

        // Chapter number
        String chapterStr = number > 0 ? "Chapter " + number : "";
        float chW = chapterStr.length() * CHAR_W;
        drawText(chapterStr, (sw - chW) / 2, sh * 0.4f, 0.5f, 0.4f, 0.3f, 0.8f);

        // Title
        float titleScale = 1.8f;
        float titleW = title.length() * CHAR_W * titleScale;
        drawTextScaled(title, (sw - titleW) / 2, sh * 0.48f, titleScale,
                0.85f, 0.7f, 0.45f, 1.0f);
    }

    /**
     * Pause menu overlay.
     */
    public void renderPauseMenu() {
        float sw = window.getWidth();
        float sh = window.getHeight();

        // Dim overlay
        drawRect(0, 0, sw, sh, 0.0f, 0.0f, 0.0f, 0.6f);

        // PAUSED
        String paused = "PAUSED";
        float pw = paused.length() * CHAR_W * 2;
        drawTextScaled(paused, (sw - pw) / 2, sh * 0.4f, 2.0f, 0.7f, 0.6f, 0.5f, 0.9f);

        String hint = "Press ESC to resume";
        float hw = hint.length() * CHAR_W;
        drawText(hint, (sw - hw) / 2, sh * 0.52f, 0.5f, 0.4f, 0.4f, 0.6f);
    }

    // --- Drawing helpers ---

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

    private void drawText(String text, float x, float y, float r, float g, float b, float a) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != ' ') {
                drawRect(x + i * CHAR_W, y, CHAR_W - 2, CHAR_H - 2, r, g, b, a);
            }
        }
    }

    private void drawTextScaled(String text, float x, float y, float scale,
                                 float r, float g, float b, float a) {
        float cw = CHAR_W * scale;
        float ch = CHAR_H * scale;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != ' ') {
                drawRect(x + i * cw, y, cw - 2, ch - 2, r, g, b, a);
            }
        }
    }

    public void cleanup() {
        glDeleteVertexArrays(quadVAO);
        glDeleteBuffers(quadVBO);
        glDeleteProgram(shaderProgram);
    }
}
