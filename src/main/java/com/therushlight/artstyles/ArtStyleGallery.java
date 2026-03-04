package com.therushlight.artstyles;

import com.therushlight.engine.Window;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Art Style Gallery — test harness for comparing 5 visual approaches.
 *
 * Press LEFT/RIGHT arrows to cycle styles.
 * Press ESCAPE to quit.
 *
 * Styles:
 *   1. WATERCOLOR — soft edges, bleed, paper texture, painterly warmth
 *   2. WOODCUT    — high contrast, hatching, ink-on-paper, woodblock print feel
 *   3. STAINED GLASS — bold outlines, jewel tones, luminous flat color
 *   4. SHADOW PUPPET — silhouettes, warm backlight, Javanese wayang kulit
 *   5. CHALK ON SLATE — muted, textured, like a child's drawing come alive
 *
 * Each style renders the same test scene:
 *   - A landscape (ground + sky + horizon)
 *   - A tree
 *   - A character silhouette
 *   - The rushlight (the lantern flame)
 *   - A title card
 */
public class ArtStyleGallery {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final String[] STYLE_NAMES = {
            "Watercolor", "Woodcut", "Stained Glass", "Shadow Puppet", "Chalk on Slate"
    };

    private Window window;
    private int quadVAO, quadVBO;
    private int[] shaderPrograms;
    private int currentStyle = 0;
    private boolean leftPressed = false;
    private boolean rightPressed = false;

    // Simple UI shader (for text/labels — same as UIRenderer)
    private int uiShader;

    public static void main(String[] args) {
        // macOS -XstartOnFirstThread handling
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac") && System.getProperty("artstyle.restarted") == null) {
            var vmArgs = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments();
            boolean hasFirstThread = vmArgs.stream().anyMatch(a -> a.contains("XstartOnFirstThread"));
            if (!hasFirstThread) {
                try {
                    String javaHome = System.getProperty("java.home");
                    String javaBin = javaHome + java.io.File.separator + "bin" + java.io.File.separator + "java";
                    String classpath = System.getProperty("java.class.path");
                    var command = new java.util.ArrayList<String>();
                    command.add(javaBin);
                    command.add("-XstartOnFirstThread");
                    command.add("-Dartstyle.restarted=true");
                    command.addAll(vmArgs);
                    command.add("-cp");
                    command.add(classpath);
                    command.add(ArtStyleGallery.class.getName());
                    for (String arg : args) command.add(arg);
                    var pb = new ProcessBuilder(command);
                    pb.inheritIO();
                    System.exit(pb.start().waitFor());
                } catch (Exception e) {
                    System.err.println("Failed to restart: " + e.getMessage());
                    System.exit(1);
                }
                return;
            }
        }

        System.out.println();
        System.out.println("  ╔══════════════════════════════════════╗");
        System.out.println("  ║      ON TIME — Art Style Gallery     ║");
        System.out.println("  ║  LEFT/RIGHT to cycle • ESC to quit   ║");
        System.out.println("  ╚══════════════════════════════════════╝");
        System.out.println();

        new ArtStyleGallery().run();
    }

    public void run() {
        try {
            init();
            loop();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private void init() throws Exception {
        window = new Window("On Time — Art Style Gallery", WIDTH, HEIGHT);
        window.init();

        // Shared quad geometry
        float[] verts = {
                0, 0, 0, 0,
                1, 0, 1, 0,
                1, 1, 1, 1,
                0, 0, 0, 0,
                1, 1, 1, 1,
                0, 1, 0, 1,
        };

        quadVAO = glGenVertexArrays();
        quadVBO = glGenBuffers();
        glBindVertexArray(quadVAO);
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
        glBufferData(GL_ARRAY_BUFFER, verts, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * 4, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * 4, 2 * 4);
        glBindVertexArray(0);

        // Compile all 5 art style shaders
        shaderPrograms = new int[5];
        shaderPrograms[0] = compile(SHARED_VERT, WATERCOLOR_FRAG);
        shaderPrograms[1] = compile(SHARED_VERT, WOODCUT_FRAG);
        shaderPrograms[2] = compile(SHARED_VERT, STAINED_GLASS_FRAG);
        shaderPrograms[3] = compile(SHARED_VERT, SHADOW_PUPPET_FRAG);
        shaderPrograms[4] = compile(SHARED_VERT, CHALK_FRAG);

        // Simple UI shader for labels
        uiShader = compile(UI_VERT, UI_FRAG);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private void loop() {
        while (!window.shouldClose()) {
            processInput();
            render();
            window.update();
        }
    }

    private void processInput() {
        boolean leftNow = glfwGetKey(window.getHandle(), GLFW_KEY_LEFT) == GLFW_PRESS;
        boolean rightNow = glfwGetKey(window.getHandle(), GLFW_KEY_RIGHT) == GLFW_PRESS;

        if (leftNow && !leftPressed) {
            currentStyle = (currentStyle + 4) % 5;
            System.out.println("  → Style: " + STYLE_NAMES[currentStyle]);
        }
        if (rightNow && !rightPressed) {
            currentStyle = (currentStyle + 1) % 5;
            System.out.println("  → Style: " + STYLE_NAMES[currentStyle]);
        }

        leftPressed = leftNow;
        rightPressed = rightNow;

        if (glfwGetKey(window.getHandle(), GLFW_KEY_ESCAPE) == GLFW_PRESS) {
            glfwSetWindowShouldClose(window.getHandle(), true);
        }
    }

    private void render() {
        glClear(GL_COLOR_BUFFER_BIT);

        float time = (float) (System.currentTimeMillis() % 100000) / 1000.0f;
        float sw = window.getWidth();
        float sh = window.getHeight();

        // Draw full-screen art style quad
        int prog = shaderPrograms[currentStyle];
        glUseProgram(prog);

        // Set uniforms
        int locRect = glGetUniformLocation(prog, "uRect");
        int locScreen = glGetUniformLocation(prog, "uScreen");
        int locTime = glGetUniformLocation(prog, "uTime");
        int locStyle = glGetUniformLocation(prog, "uStyle");

        if (locRect >= 0) glUniform4f(locRect, 0, 0, sw, sh);
        if (locScreen >= 0) glUniform2f(locScreen, sw, sh);
        if (locTime >= 0) glUniform1f(locTime, time);
        if (locStyle >= 0) glUniform1i(locStyle, currentStyle);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
        glUseProgram(0);

        // Draw style label
        renderLabel(STYLE_NAMES[currentStyle], sw, sh);
        renderNavHint(sw, sh);
    }

    private void renderLabel(String name, float sw, float sh) {
        // Style name at top center
        String label = "[ " + (currentStyle + 1) + " / 5 ]  " + name;
        float charW = 12;
        float labelW = label.length() * charW;
        float x = (sw - labelW) / 2;
        float y = 20;

        for (int i = 0; i < label.length(); i++) {
            if (label.charAt(i) != ' ') {
                drawUIRect(x + i * charW, y, charW - 2, 16, 1.0f, 1.0f, 1.0f, 0.9f);
            }
        }
    }

    private void renderNavHint(float sw, float sh) {
        String hint = "< LEFT  |  RIGHT >";
        float charW = 10;
        float hintW = hint.length() * charW;
        float x = (sw - hintW) / 2;
        float y = sh - 40;

        for (int i = 0; i < hint.length(); i++) {
            if (hint.charAt(i) != ' ') {
                drawUIRect(x + i * charW, y, charW - 2, 14, 0.7f, 0.6f, 0.5f, 0.6f);
            }
        }
    }

    private void drawUIRect(float x, float y, float w, float h, float r, float g, float b, float a) {
        glUseProgram(uiShader);
        glUniform4f(glGetUniformLocation(uiShader, "uRect"), x, y, w, h);
        glUniform2f(glGetUniformLocation(uiShader, "uScreen"), window.getWidth(), window.getHeight());
        glUniform4f(glGetUniformLocation(uiShader, "uColor"), r, g, b, a);
        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
        glUseProgram(0);
    }

    private int compile(String vertSrc, String fragSrc) {
        int vert = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vert, vertSrc);
        glCompileShader(vert);
        if (glGetShaderi(vert, GL_COMPILE_STATUS) == 0) {
            System.err.println("Vertex shader error: " + glGetShaderInfoLog(vert));
        }

        int frag = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(frag, fragSrc);
        glCompileShader(frag);
        if (glGetShaderi(frag, GL_COMPILE_STATUS) == 0) {
            System.err.println("Fragment shader error: " + glGetShaderInfoLog(frag));
        }

        int prog = glCreateProgram();
        glAttachShader(prog, vert);
        glAttachShader(prog, frag);
        glLinkProgram(prog);
        if (glGetProgrami(prog, GL_LINK_STATUS) == 0) {
            System.err.println("Shader link error: " + glGetProgramInfoLog(prog));
        }

        glDeleteShader(vert);
        glDeleteShader(frag);
        return prog;
    }

    private void cleanup() {
        for (int p : shaderPrograms) if (p != 0) glDeleteProgram(p);
        if (uiShader != 0) glDeleteProgram(uiShader);
        glDeleteVertexArrays(quadVAO);
        glDeleteBuffers(quadVBO);
        if (window != null) window.cleanup();
    }

    // =====================================================================
    //  SHADERS
    // =====================================================================

    // Shared vertex shader — maps quad to screen coords, passes UV
    private static final String SHARED_VERT = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            layout (location = 1) in vec2 aTexCoord;
            uniform vec4 uRect;
            uniform vec2 uScreen;
            out vec2 vUV;
            void main() {
                vec2 pos = aPos * uRect.zw + uRect.xy;
                vec2 ndc = (pos / uScreen) * 2.0 - 1.0;
                ndc.y = -ndc.y;
                gl_Position = vec4(ndc, 0.0, 1.0);
                vUV = aTexCoord;
            }
            """;

    // UI vertex/fragment (simple colored quads for labels)
    private static final String UI_VERT = """
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

    private static final String UI_FRAG = """
            #version 330 core
            out vec4 FragColor;
            uniform vec4 uColor;
            void main() {
                FragColor = uColor;
            }
            """;

    // -----------------------------------------------------------------
    // STYLE 1: WATERCOLOR
    // Soft blended colors, paper grain, bleeding edges, warm palette
    // Think: Miyazaki background art, children's book illustration
    // -----------------------------------------------------------------
    private static final String WATERCOLOR_FRAG = """
            #version 330 core
            in vec2 vUV;
            out vec4 FragColor;
            uniform float uTime;

            // Hash / noise functions
            float hash(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
            }

            float noise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                float a = hash(i);
                float b = hash(i + vec2(1.0, 0.0));
                float c = hash(i + vec2(0.0, 1.0));
                float d = hash(i + vec2(1.0, 1.0));
                return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
            }

            float fbm(vec2 p) {
                float v = 0.0;
                float a = 0.5;
                for (int i = 0; i < 5; i++) {
                    v += a * noise(p);
                    p *= 2.0;
                    a *= 0.5;
                }
                return v;
            }

            void main() {
                vec2 uv = vUV;

                // Paper texture grain
                float paper = 0.92 + 0.08 * noise(uv * 200.0);

                // Sky gradient — warm sunset watercolor
                vec3 skyTop = vec3(0.35, 0.45, 0.65);
                vec3 skyBot = vec3(0.85, 0.55, 0.35);
                float horizon = 0.45;

                // Watercolor bleed on horizon
                float bleed = fbm(uv * 8.0 + uTime * 0.02) * 0.06;
                float skyMask = smoothstep(horizon - 0.08 + bleed, horizon + 0.08 + bleed, uv.y);

                vec3 sky = mix(skyBot, skyTop, 1.0 - uv.y);

                // Ground — warm earth tones with watercolor wash
                float groundNoise = fbm(uv * 12.0 + vec2(0.0, uTime * 0.01));
                vec3 groundBase = vec3(0.25, 0.35, 0.18);
                vec3 groundWarm = vec3(0.4, 0.3, 0.15);
                vec3 ground = mix(groundBase, groundWarm, groundNoise);

                vec3 col = mix(ground, sky, skyMask);

                // Rolling hills (watercolor wash layers)
                float hill1 = smoothstep(0.0, 0.04, uv.y - (horizon - 0.05 + 0.04 * sin(uv.x * 6.0 + 1.0)));
                float hill2 = smoothstep(0.0, 0.03, uv.y - (horizon - 0.12 + 0.03 * sin(uv.x * 8.0 + 3.0)));
                col = mix(vec3(0.2, 0.3, 0.15), col, hill1);
                col = mix(vec3(0.15, 0.22, 0.12), col, hill2);

                // Tree (simple watercolor blob)
                vec2 treePos = vec2(0.3, horizon - 0.1);
                float trunk = step(abs(uv.x - treePos.x), 0.008) *
                              step(treePos.y - 0.15, uv.y) * step(uv.y, treePos.y);
                col = mix(col, vec3(0.25, 0.18, 0.1), trunk);

                float canopyDist = length((uv - treePos + vec2(0.0, -0.02)) * vec2(1.0, 1.5));
                float canopy = smoothstep(0.08, 0.05, canopyDist + fbm(uv * 30.0) * 0.03);
                col = mix(col, vec3(0.2, 0.4, 0.15) + 0.1 * noise(uv * 40.0), canopy);

                // Character silhouette (soft watercolor figure)
                vec2 charPos = vec2(0.6, horizon - 0.05);
                float body = smoothstep(0.04, 0.02, length((uv - charPos) * vec2(1.0, 0.5)));
                float head = smoothstep(0.025, 0.015, length(uv - charPos + vec2(0.0, -0.07)));
                float figure = max(body, head);
                vec3 charColor = vec3(0.35, 0.25, 0.2);
                col = mix(col, charColor, figure * 0.85);

                // Rushlight flame (warm glow)
                vec2 flamePos = vec2(0.62, horizon - 0.14);
                float flameDist = length((uv - flamePos) * vec2(1.0, 1.5));
                float flame = smoothstep(0.03, 0.0, flameDist);
                float flicker = 0.8 + 0.2 * sin(uTime * 4.0 + uv.y * 20.0);
                vec3 flameCol = vec3(1.0, 0.7, 0.3) * flicker;
                col += flameCol * flame * 0.8;

                // Warm glow around flame
                float glow = smoothstep(0.2, 0.0, flameDist);
                col += vec3(0.3, 0.15, 0.05) * glow * 0.3;

                // Paper texture overlay
                col *= paper;

                // Slight vignette
                float vig = 1.0 - 0.3 * length(vUV - 0.5);
                col *= vig;

                FragColor = vec4(col, 1.0);
            }
            """;

    // -----------------------------------------------------------------
    // STYLE 2: WOODCUT
    // High contrast, hatching lines, black ink on warm paper
    // Think: Albrecht Dürer, Japanese ukiyo-e, medieval manuscripts
    // -----------------------------------------------------------------
    private static final String WOODCUT_FRAG = """
            #version 330 core
            in vec2 vUV;
            out vec4 FragColor;
            uniform float uTime;

            float hash(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
            }

            float noise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                float a = hash(i);
                float b = hash(i + vec2(1.0, 0.0));
                float c = hash(i + vec2(0.0, 1.0));
                float d = hash(i + vec2(1.0, 1.0));
                return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
            }

            // Hatching: parallel lines for shading
            float hatch(vec2 uv, float density, float angle) {
                float c = cos(angle);
                float s = sin(angle);
                vec2 rotUV = vec2(uv.x * c - uv.y * s, uv.x * s + uv.y * c);
                return smoothstep(0.4, 0.5, abs(sin(rotUV.x * density)));
            }

            void main() {
                vec2 uv = vUV;

                // Warm paper base
                vec3 paper = vec3(0.9, 0.85, 0.75);
                vec3 ink = vec3(0.08, 0.06, 0.05);

                float horizon = 0.45;

                // Sky — mostly paper with light hatching
                float skyShade = (1.0 - uv.y) * 0.3;
                float skyHatch = hatch(uv, 150.0, 0.1) * skyShade;

                // Ground — heavy hatching
                float groundMask = step(uv.y, horizon + 0.01 * sin(uv.x * 15.0));
                float groundShade = 0.4 + 0.2 * noise(uv * 10.0);
                float groundHatch1 = hatch(uv, 200.0, 0.785) * groundShade;
                float groundHatch2 = hatch(uv, 180.0, -0.5) * groundShade * 0.5;
                float groundLine = groundHatch1 + groundHatch2;

                // Horizon line (bold)
                float horizLine = smoothstep(0.003, 0.001,
                    abs(uv.y - horizon - 0.01 * sin(uv.x * 15.0)));

                // Tree — bold ink strokes
                vec2 treePos = vec2(0.3, horizon);
                float trunk = step(abs(uv.x - treePos.x), 0.01) *
                              step(treePos.y - 0.18, uv.y) * step(uv.y, treePos.y);

                // Branches (angular, woodcut style)
                float branch1 = step(abs(uv.y - (treePos.y - 0.12) - (uv.x - treePos.x) * 1.5), 0.004) *
                                step(treePos.x, uv.x) * step(uv.x, treePos.x + 0.08);
                float branch2 = step(abs(uv.y - (treePos.y - 0.10) + (uv.x - treePos.x) * 1.2), 0.004) *
                                step(treePos.x - 0.07, uv.x) * step(uv.x, treePos.x);

                // Canopy (hatched circle)
                float canopyDist = length((uv - treePos + vec2(0.0, -0.04)) * vec2(1.0, 1.4));
                float canopyEdge = smoothstep(0.09, 0.085, canopyDist);
                float canopyHatch = hatch(uv, 250.0, 0.6) * 0.7 + hatch(uv, 200.0, -0.3) * 0.3;

                // Character — bold silhouette with some internal hatching
                vec2 charPos = vec2(0.6, horizon);
                float body = smoothstep(0.04, 0.035, length((uv - charPos + vec2(0.0, 0.05)) * vec2(1.0, 0.45)));
                float head = smoothstep(0.025, 0.02, length(uv - charPos + vec2(0.0, -0.07)));
                float figure = max(body, head);

                // Rushlight flame — radiating lines
                vec2 flamePos = vec2(0.62, horizon - 0.14);
                float flameDist = length(uv - flamePos);
                float flame = smoothstep(0.02, 0.005, flameDist);

                // Radiating lines from flame
                float angle = atan(uv.y - flamePos.y, uv.x - flamePos.x);
                float rays = smoothstep(0.3, 0.5, abs(sin(angle * 12.0 + uTime * 0.5)));
                float rayMask = smoothstep(0.15, 0.02, flameDist);
                float rayLines = rays * rayMask * 0.6;

                // Compose everything
                float inkAmount = 0.0;
                inkAmount += skyHatch * (1.0 - groundMask);
                inkAmount += groundLine * groundMask;
                inkAmount = max(inkAmount, horizLine);
                inkAmount = max(inkAmount, trunk);
                inkAmount = max(inkAmount, branch1);
                inkAmount = max(inkAmount, branch2);
                inkAmount = max(inkAmount, canopyEdge * canopyHatch);
                inkAmount = max(inkAmount, figure * 0.9);
                inkAmount = max(inkAmount, flame);
                inkAmount = max(inkAmount, canopyEdge * 0.15); // faint fill

                // Rays reduce ink (they're paper-colored gaps)
                inkAmount = max(inkAmount - rayLines, 0.0);

                vec3 col = mix(paper, ink, clamp(inkAmount, 0.0, 1.0));

                // Slight paper texture
                col *= 0.95 + 0.05 * noise(uv * 300.0);

                FragColor = vec4(col, 1.0);
            }
            """;

    // -----------------------------------------------------------------
    // STYLE 3: STAINED GLASS
    // Bold black outlines, jewel-tone flat fills, luminous, Voronoi cells
    // Think: Chartres Cathedral, Art Nouveau, Tiffany lamps
    // -----------------------------------------------------------------
    private static final String STAINED_GLASS_FRAG = """
            #version 330 core
            in vec2 vUV;
            out vec4 FragColor;
            uniform float uTime;

            vec2 hash2(vec2 p) {
                p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
                return fract(sin(p) * 43758.5453);
            }

            // Voronoi — returns (distance to nearest edge, cell id)
            vec3 voronoi(vec2 uv, float scale) {
                vec2 p = uv * scale;
                vec2 i = floor(p);
                vec2 f = fract(p);

                float md = 8.0;
                float md2 = 8.0;
                vec2 id = vec2(0.0);

                for (int y = -1; y <= 1; y++) {
                    for (int x = -1; x <= 1; x++) {
                        vec2 neighbor = vec2(float(x), float(y));
                        vec2 point = hash2(i + neighbor);
                        point = 0.5 + 0.4 * sin(uTime * 0.3 + 6.2831 * point);
                        vec2 diff = neighbor + point - f;
                        float d = length(diff);
                        if (d < md) {
                            md2 = md;
                            md = d;
                            id = i + neighbor;
                        } else if (d < md2) {
                            md2 = d;
                        }
                    }
                }

                float edge = md2 - md;
                return vec3(edge, id);
            }

            void main() {
                vec2 uv = vUV;
                float horizon = 0.45;

                // Voronoi cells for glass panes
                vec3 vor = voronoi(uv, 12.0);
                float edge = smoothstep(0.02, 0.06, vor.x);

                // Lead lines (black outlines between panes)
                float lead = 1.0 - edge;

                // Cell color based on position
                float cellHash = fract(sin(dot(vor.yz, vec2(127.1, 311.7))) * 43758.5453);

                vec3 col;
                if (uv.y > horizon + 0.03) {
                    // Sky panes — deep blues, purples, golds
                    vec3 c1 = vec3(0.15, 0.2, 0.6);   // deep blue
                    vec3 c2 = vec3(0.5, 0.2, 0.55);    // purple
                    vec3 c3 = vec3(0.8, 0.6, 0.15);    // gold
                    vec3 c4 = vec3(0.2, 0.35, 0.7);    // medium blue

                    if (cellHash < 0.3) col = c1;
                    else if (cellHash < 0.5) col = c2;
                    else if (cellHash < 0.65) col = c3;
                    else col = c4;
                } else {
                    // Ground panes — greens, earth, amber
                    vec3 c1 = vec3(0.1, 0.35, 0.12);   // forest green
                    vec3 c2 = vec3(0.3, 0.25, 0.1);    // earth
                    vec3 c3 = vec3(0.5, 0.35, 0.1);    // amber
                    vec3 c4 = vec3(0.15, 0.4, 0.2);    // bright green

                    if (cellHash < 0.25) col = c1;
                    else if (cellHash < 0.5) col = c2;
                    else if (cellHash < 0.75) col = c3;
                    else col = c4;
                }

                // Luminosity — glass glows from behind
                float lum = 0.7 + 0.3 * sin(uTime * 0.5 + cellHash * 6.28);
                col *= lum;

                // Character silhouette (lead/dark shape in the glass)
                vec2 charPos = vec2(0.6, horizon);
                float body = smoothstep(0.045, 0.04, length((uv - charPos + vec2(0.0, 0.05)) * vec2(1.0, 0.45)));
                float head = smoothstep(0.03, 0.025, length(uv - charPos + vec2(0.0, -0.07)));
                float figure = max(body, head);

                // Tree in glass
                vec2 treePos = vec2(0.3, horizon);
                float canopyDist = length((uv - treePos + vec2(0.0, -0.03)) * vec2(1.0, 1.4));
                float canopy = smoothstep(0.09, 0.08, canopyDist);
                float trunkLine = step(abs(uv.x - treePos.x), 0.008) *
                                  step(treePos.y - 0.15, uv.y) * step(uv.y, treePos.y);
                float tree = max(canopy * 0.3, trunkLine);

                // Flame — golden pane
                vec2 flamePos = vec2(0.62, horizon - 0.14);
                float flameDist = length((uv - flamePos) * vec2(1.0, 1.3));
                float flame = smoothstep(0.04, 0.01, flameDist);
                vec3 flameCol = vec3(1.0, 0.8, 0.2);

                // Radiant halo around flame
                float halo = smoothstep(0.15, 0.0, flameDist);

                // Compose
                col = mix(col, flameCol * 1.2, flame);
                col += vec3(0.4, 0.25, 0.05) * halo * 0.25;

                // Apply lead lines
                vec3 leadColor = vec3(0.04, 0.03, 0.03);
                col = mix(leadColor, col, edge);

                // Figure and tree as dark lead
                col = mix(col, leadColor, figure * 0.85);
                col = mix(col, leadColor, tree);

                FragColor = vec4(col, 1.0);
            }
            """;

    // -----------------------------------------------------------------
    // STYLE 4: SHADOW PUPPET
    // Silhouettes against warm backlight. Javanese wayang kulit.
    // Everything is flat black against a glowing amber/orange screen.
    // -----------------------------------------------------------------
    private static final String SHADOW_PUPPET_FRAG = """
            #version 330 core
            in vec2 vUV;
            out vec4 FragColor;
            uniform float uTime;

            float hash(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
            }

            float noise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                float a = hash(i);
                float b = hash(i + vec2(1.0, 0.0));
                float c = hash(i + vec2(0.0, 1.0));
                float d = hash(i + vec2(1.0, 1.0));
                return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
            }

            void main() {
                vec2 uv = vUV;
                float horizon = 0.4;

                // Backlit screen — warm radial gradient from center
                vec2 center = vec2(0.5, 0.45);
                float dist = length((uv - center) * vec2(1.0, 0.8));

                // Parchment/screen glow
                vec3 warm = vec3(0.95, 0.75, 0.4);
                vec3 edge = vec3(0.6, 0.35, 0.15);
                vec3 dark = vec3(0.15, 0.08, 0.03);

                vec3 screen = mix(warm, edge, smoothstep(0.2, 0.6, dist));
                screen = mix(screen, dark, smoothstep(0.5, 0.85, dist));

                // Subtle cloth texture
                float cloth = 0.95 + 0.05 * noise(uv * 80.0);
                screen *= cloth;

                // Flickering backlight
                float flicker = 0.9 + 0.1 * sin(uTime * 3.0) + 0.05 * sin(uTime * 7.3);
                screen *= flicker;

                // === SHADOW SILHOUETTES (pure black) ===
                float shadow = 0.0;

                // Ground plane
                shadow = max(shadow, 1.0 - step(horizon + 0.008 * sin(uv.x * 20.0), uv.y));

                // Tree silhouette
                vec2 treePos = vec2(0.25, horizon);
                float trunk = step(abs(uv.x - treePos.x), 0.008) *
                              step(treePos.y, uv.y) * step(uv.y, treePos.y + 0.18);
                shadow = max(shadow, trunk);

                // Tree canopy (detailed edges)
                vec2 canopyCenter = treePos + vec2(0.0, 0.2);
                float canopyDist = length((uv - canopyCenter) * vec2(1.0, 1.3));
                float canopyEdge = 0.09 + 0.015 * sin(atan(uv.y - canopyCenter.y, uv.x - canopyCenter.x) * 8.0);
                float canopy = step(canopyDist, canopyEdge);
                shadow = max(shadow, canopy);

                // Main character — walking figure with articulated joints (puppet style)
                vec2 cp = vec2(0.55, horizon);

                // Torso
                float torso = step(abs(uv.x - cp.x), 0.015) *
                              step(cp.y, uv.y) * step(uv.y, cp.y + 0.12);

                // Head (circle)
                float head = step(length(uv - cp - vec2(0.0, 0.15)), 0.025);

                // Arms (angular, puppet-like)
                float sway = sin(uTime * 2.0) * 0.02;
                float arm1 = step(abs(uv.y - (cp.y + 0.09) + (uv.x - cp.x + sway) * 0.8), 0.005) *
                             step(cp.x - 0.06, uv.x) * step(uv.x, cp.x);
                float arm2 = step(abs(uv.y - (cp.y + 0.08) - (uv.x - cp.x + sway) * 0.6), 0.005) *
                             step(cp.x, uv.x) * step(uv.x, cp.x + 0.07);

                // Legs
                float leg1 = step(abs(uv.x - cp.x + 0.01), 0.006) *
                             step(cp.y - 0.08, uv.y) * step(uv.y, cp.y);
                float leg2 = step(abs(uv.x - cp.x - 0.01), 0.006) *
                             step(cp.y - 0.08, uv.y) * step(uv.y, cp.y);

                shadow = max(shadow, torso);
                shadow = max(shadow, head);
                shadow = max(shadow, arm1);
                shadow = max(shadow, arm2);
                shadow = max(shadow, leg1);
                shadow = max(shadow, leg2);

                // Second smaller figure (child)
                vec2 cp2 = vec2(0.65, horizon);
                float torso2 = step(abs(uv.x - cp2.x), 0.01) *
                               step(cp2.y, uv.y) * step(uv.y, cp2.y + 0.08);
                float head2 = step(length(uv - cp2 - vec2(0.0, 0.1)), 0.018);
                shadow = max(shadow, torso2);
                shadow = max(shadow, head2);

                // Rushlight (held up — tiny flame NOT in shadow)
                vec2 flamePos = vec2(0.58, horizon + 0.17);
                float stick = step(abs(uv.x - flamePos.x), 0.003) *
                              step(cp.y + 0.12, uv.y) * step(uv.y, flamePos.y);
                shadow = max(shadow, stick);

                // The flame itself — a warm spot that breaks the shadow
                float flameDist = length((uv - flamePos) * vec2(1.0, 1.5));
                float flame = smoothstep(0.015, 0.0, flameDist);
                float flameGlow = smoothstep(0.08, 0.0, flameDist);

                // Compose
                vec3 col = screen;

                // Apply shadows
                col = mix(col, vec3(0.02, 0.01, 0.01), shadow);

                // Flame punches through shadow with warm light
                col = mix(col, vec3(1.0, 0.85, 0.4), flame);
                col += vec3(0.4, 0.2, 0.05) * flameGlow * 0.4 * (1.0 - shadow * 0.5);

                FragColor = vec4(col, 1.0);
            }
            """;

    // -----------------------------------------------------------------
    // STYLE 5: CHALK ON SLATE
    // Muted, dusty, textured — like colored chalk on a dark grey board.
    // Think: a child's drawing that's somehow beautiful and haunting.
    // -----------------------------------------------------------------
    private static final String CHALK_FRAG = """
            #version 330 core
            in vec2 vUV;
            out vec4 FragColor;
            uniform float uTime;

            float hash(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
            }

            float noise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                float a = hash(i);
                float b = hash(i + vec2(1.0, 0.0));
                float c = hash(i + vec2(0.0, 1.0));
                float d = hash(i + vec2(1.0, 1.0));
                return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
            }

            float fbm(vec2 p) {
                float v = 0.0;
                float a = 0.5;
                for (int i = 0; i < 4; i++) {
                    v += a * noise(p);
                    p *= 2.0;
                    a *= 0.5;
                }
                return v;
            }

            void main() {
                vec2 uv = vUV;

                // Slate background (dark grey-blue, textured)
                float slate = 0.15 + 0.03 * noise(uv * 150.0);
                vec3 slateCol = vec3(slate * 0.9, slate * 0.92, slate);

                // Chalk dust effect — irregular coverage
                float coverage = 0.6 + 0.4 * noise(uv * 60.0 + 5.0);
                float grain = noise(uv * 300.0);

                float horizon = 0.45;

                // Sky — light blue/grey chalk strokes
                float skyMask = smoothstep(horizon - 0.02, horizon + 0.04, uv.y);
                vec3 skyChalk = vec3(0.5, 0.55, 0.7);
                // Streaky horizontal strokes
                float skyStroke = noise(vec2(uv.x * 3.0, uv.y * 50.0)) * coverage;

                // Stars (tiny chalk dots)
                float stars = step(0.985, hash(floor(uv * 80.0)));
                float starTwinkle = 0.5 + 0.5 * sin(uTime * 3.0 + hash(floor(uv * 80.0)) * 30.0);

                // Ground — earthy chalk
                float groundMask = 1.0 - skyMask;
                vec3 groundChalk = vec3(0.4, 0.5, 0.3);
                float groundStroke = noise(vec2(uv.x * 8.0, uv.y * 40.0)) * coverage;

                // Grass strokes (vertical dashes)
                float grass = step(0.7, noise(vec2(uv.x * 50.0, uv.y * 5.0))) *
                              step(uv.y, horizon + 0.02) * step(horizon - 0.08, uv.y);

                // Tree (chalk-drawn, slightly wobbly)
                vec2 treePos = vec2(0.3, horizon);
                float wobble = noise(vec2(uv.y * 20.0, 3.0)) * 0.01;
                float trunk = smoothstep(0.012, 0.006, abs(uv.x - treePos.x - wobble)) *
                              step(treePos.y - 0.02, uv.y) * step(uv.y, treePos.y + 0.16);
                vec3 trunkCol = vec3(0.45, 0.3, 0.2);

                // Canopy (scribbled circle)
                vec2 canopyCenter = treePos + vec2(0.0, 0.18);
                float canopyDist = length((uv - canopyCenter) * vec2(1.0, 1.2));
                float canopyRing = smoothstep(0.09, 0.07, canopyDist) - smoothstep(0.07, 0.04, canopyDist);
                float canopyFill = smoothstep(0.07, 0.05, canopyDist) * noise(uv * 40.0);
                vec3 canopyCol = vec3(0.3, 0.55, 0.25);

                // Character (chalk stick figure — childlike)
                vec2 cp = vec2(0.6, horizon);

                // Simple stick body
                float stickBody = smoothstep(0.006, 0.002, abs(uv.x - cp.x + noise(vec2(uv.y * 15.0, 1.0)) * 0.005)) *
                                  step(cp.y, uv.y) * step(uv.y, cp.y + 0.12);
                float stickHead = smoothstep(0.025, 0.018, length(uv - cp - vec2(0.0, 0.15)));

                // Arms (V shape)
                float armAngle = 0.7;
                float arm1 = smoothstep(0.005, 0.002,
                    abs(uv.y - (cp.y + 0.09) - (uv.x - cp.x) * armAngle)) *
                    step(cp.x - 0.05, uv.x) * step(uv.x, cp.x + 0.005);
                float arm2 = smoothstep(0.005, 0.002,
                    abs(uv.y - (cp.y + 0.09) + (uv.x - cp.x) * armAngle)) *
                    step(cp.x - 0.005, uv.x) * step(uv.x, cp.x + 0.05);

                // Legs
                float leg1 = smoothstep(0.005, 0.002,
                    abs(uv.x - cp.x + 0.02 - (uv.y - cp.y + 0.06) * 0.3)) *
                    step(cp.y - 0.06, uv.y) * step(uv.y, cp.y);
                float leg2 = smoothstep(0.005, 0.002,
                    abs(uv.x - cp.x - 0.02 + (uv.y - cp.y + 0.06) * 0.3)) *
                    step(cp.y - 0.06, uv.y) * step(uv.y, cp.y);

                vec3 charCol = vec3(0.8, 0.7, 0.5); // chalk white/cream

                float figure = max(max(stickBody, stickHead), max(max(arm1, arm2), max(leg1, leg2)));

                // Rushlight flame — yellow chalk
                vec2 flamePos = vec2(0.62, horizon + 0.14);
                float flameDist = length((uv - flamePos) * vec2(1.0, 1.4));
                float flame = smoothstep(0.025, 0.008, flameDist) * coverage;
                vec3 flameCol = vec3(1.0, 0.85, 0.3);

                // Glow (smudged chalk)
                float glow = smoothstep(0.12, 0.0, flameDist);
                vec3 glowCol = vec3(0.6, 0.4, 0.15);

                // Compose on slate
                vec3 col = slateCol;

                // Sky chalk
                col = mix(col, skyChalk * (0.3 + 0.5 * grain), skyMask * skyStroke * 0.5);

                // Stars
                col = mix(col, vec3(0.9, 0.9, 0.8), skyMask * stars * starTwinkle * 0.7);

                // Ground chalk
                col = mix(col, groundChalk * (0.3 + 0.5 * grain), groundMask * groundStroke * 0.5);
                col = mix(col, groundChalk * 0.8, grass * 0.4 * grain);

                // Tree
                col = mix(col, trunkCol * (0.5 + 0.5 * grain), trunk * coverage);
                col = mix(col, canopyCol * (0.4 + 0.4 * grain), canopyRing * coverage);
                col = mix(col, canopyCol * (0.3 + 0.3 * grain), canopyFill * 0.5);

                // Character
                col = mix(col, charCol * (0.5 + 0.4 * grain), figure * coverage);

                // Flame and glow
                col += glowCol * glow * 0.2;
                col = mix(col, flameCol * (0.6 + 0.4 * grain), flame);

                FragColor = vec4(col, 1.0);
            }
            """;
}
