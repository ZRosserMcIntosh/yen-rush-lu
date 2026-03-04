package com.therushlight;

import com.therushlight.engine.Window;
import com.therushlight.engine.Timer;
import com.therushlight.engine.input.InputHandler;
import com.therushlight.engine.input.MouseHandler;
import com.therushlight.audio.AudioEngine;
import com.therushlight.narrative.*;
import com.therushlight.rendering.*;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The main game engine. Manages the loop, state transitions, and all subsystems.
 */
public class GameEngine {

    public static final String TITLE = "On Time";
    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;
    public static final float TARGET_UPS = 60.0f;

    private Window window;
    private Timer timer;
    private InputHandler input;
    private MouseHandler mouse;
    private AudioEngine audio;

    // Rendering
    private SceneRenderer sceneRenderer;
    private DialogueRenderer dialogueRenderer;
    private TransitionRenderer transitionRenderer;
    private UIRenderer uiRenderer;

    // Narrative
    private StoryState storyState;
    private ChapterManager chapterManager;
    private Chapter currentChapter;
    private Scene currentScene;
    private DialogueRunner dialogueRunner;
    private CutscenePlayer cutscenePlayer;

    // Game state
    private enum Phase { TITLE_SCREEN, PLAYING, CUTSCENE, PAUSED, CHAPTER_TITLE, TRANSITION }
    private Phase phase = Phase.TITLE_SCREEN;
    private boolean running = true;

    // Transition
    private float transitionAlpha = 1.0f; // Start faded to black
    private boolean fadingIn = true;
    private boolean fadingOut = false;
    private Runnable onTransitionComplete = null;

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
        window = new Window(TITLE, WIDTH, HEIGHT);
        window.init();

        timer = new Timer();
        input = new InputHandler(window.getHandle());
        mouse = new MouseHandler(window.getHandle());
        audio = new AudioEngine();

        sceneRenderer = new SceneRenderer(window);
        dialogueRenderer = new DialogueRenderer(window);
        transitionRenderer = new TransitionRenderer(window);
        uiRenderer = new UIRenderer(window);

        // Load story
        storyState = SaveManager.loadOrCreate();
        chapterManager = new ChapterManager();
        dialogueRunner = new DialogueRunner(storyState);
        cutscenePlayer = new CutscenePlayer();

        // If new game, start from prologue
        if (storyState.getCurrentChapterId() == null) {
            startChapter("prologue");
        } else {
            startChapter(storyState.getCurrentChapterId());
        }
    }

    private void loop() {
        float accumulator = 0f;
        float interval = 1f / TARGET_UPS;

        while (!window.shouldClose() && running) {
            float elapsed = timer.getElapsedTime();
            accumulator += elapsed;

            processInput();

            while (accumulator >= interval) {
                update(interval);
                accumulator -= interval;
            }

            render();
            window.update();
        }
    }

    private void processInput() {
        input.update();
        mouse.update();

        if (input.isKeyPressed(GLFW_KEY_ESCAPE)) {
            if (phase == Phase.PAUSED) {
                phase = Phase.PLAYING;
            } else if (phase == Phase.PLAYING) {
                phase = Phase.PAUSED;
            }
        }

        // Skip cutscene
        if (phase == Phase.CUTSCENE && input.isKeyPressed(GLFW_KEY_SPACE)) {
            if (cutscenePlayer.isPlaying()) {
                cutscenePlayer.skip();
            }
        }

        // Title screen - any key to start
        if (phase == Phase.TITLE_SCREEN) {
            if (input.isKeyPressed(GLFW_KEY_SPACE) || input.isKeyPressed(GLFW_KEY_ENTER)
                    || mouse.isLeftButtonPressed()) {
                fadeToBlack(() -> {
                    phase = Phase.PLAYING;
                    fadingIn = true;
                });
            }
        }
    }

    private void update(float dt) {
        // Handle transitions
        if (fadingOut) {
            transitionAlpha += dt * 1.5f;
            if (transitionAlpha >= 1.0f) {
                transitionAlpha = 1.0f;
                fadingOut = false;
                if (onTransitionComplete != null) {
                    onTransitionComplete.run();
                    onTransitionComplete = null;
                }
            }
            return;
        }

        if (fadingIn) {
            transitionAlpha -= dt * 1.5f;
            if (transitionAlpha <= 0.0f) {
                transitionAlpha = 0.0f;
                fadingIn = false;
            }
        }

        if (phase == Phase.PAUSED || phase == Phase.TITLE_SCREEN) return;

        if (phase == Phase.CUTSCENE) {
            cutscenePlayer.update(dt, audio);
            if (cutscenePlayer.isFinished()) {
                phase = Phase.PLAYING;
            }
            return;
        }

        if (phase == Phase.PLAYING && currentScene != null) {
            // Update dialogue
            dialogueRunner.update(dt, mouse, input, window);

            // Check for scene transitions
            if (dialogueRunner.isWaitingForTransition()) {
                String nextSceneId = dialogueRunner.getNextSceneId();
                if (nextSceneId != null) {
                    if (nextSceneId.startsWith("chapter:")) {
                        String nextChapter = nextSceneId.substring(8);
                        fadeToBlack(() -> startChapter(nextChapter));
                    } else {
                        fadeToBlack(() -> loadScene(nextSceneId));
                    }
                    dialogueRunner.clearTransition();
                }
            }

            // Check for notifications ("Drew will remember that.")
            dialogueRunner.updateNotifications(dt);
        }

        audio.update();
    }

    private void render() {
        sceneRenderer.begin();

        switch (phase) {
            case TITLE_SCREEN -> renderTitleScreen();
            case PLAYING, PAUSED -> renderGameplay();
            case CUTSCENE -> renderCutscene();
            case CHAPTER_TITLE -> renderChapterTitle();
            default -> {}
        }

        // Transition overlay (fade to/from black)
        if (transitionAlpha > 0.001f) {
            transitionRenderer.renderFade(transitionAlpha);
        }

        sceneRenderer.end();
    }

    private void renderTitleScreen() {
        uiRenderer.renderTitleScreen();
    }

    private void renderGameplay() {
        if (currentScene != null) {
            // Background
            sceneRenderer.renderBackground(currentScene);

            // Characters in scene
            sceneRenderer.renderCharacters(currentScene);

            // Dialogue box + choices
            dialogueRenderer.render(dialogueRunner);

            // Notifications
            dialogueRenderer.renderNotifications(dialogueRunner);

            // Pause overlay
            if (phase == Phase.PAUSED) {
                uiRenderer.renderPauseMenu();
            }
        }
    }

    private void renderCutscene() {
        cutscenePlayer.render(sceneRenderer, dialogueRenderer);
    }

    private void renderChapterTitle() {
        if (currentChapter != null) {
            uiRenderer.renderChapterTitle(currentChapter.getNumber(), currentChapter.getTitle());
        }
    }

    // --- Story management ---

    private void startChapter(String chapterId) {
        currentChapter = chapterManager.loadChapter(chapterId);
        if (currentChapter == null) {
            System.err.println("Chapter not found: " + chapterId);
            return;
        }

        storyState.setCurrentChapterId(chapterId);

        // Show chapter title card, then load first scene
        phase = Phase.CHAPTER_TITLE;
        fadingIn = true;

        // After 3 seconds, transition to first scene
        // (handled in update via a timer — simplified here)
        String firstScene = currentChapter.getFirstSceneId();
        loadScene(firstScene);
        phase = Phase.PLAYING;
    }

    private void loadScene(String sceneId) {
        if (currentChapter == null) return;

        currentScene = currentChapter.getScene(sceneId);
        if (currentScene == null) {
            System.err.println("Scene not found: " + sceneId + " in chapter " + currentChapter.getId());
            return;
        }

        // Start scene's dialogue
        dialogueRunner.startScene(currentScene);

        // Play scene music/ambience
        if (currentScene.getMusic() != null) {
            audio.playMusic(currentScene.getMusic());
        }

        fadingIn = true;
    }

    private void fadeToBlack(Runnable then) {
        fadingOut = true;
        transitionAlpha = 0.0f;
        onTransitionComplete = then;
    }

    private void cleanup() {
        // Save progress
        if (storyState != null) {
            SaveManager.save(storyState);
        }

        if (audio != null) audio.cleanup();
        if (dialogueRenderer != null) dialogueRenderer.cleanup();
        if (sceneRenderer != null) sceneRenderer.cleanup();
        if (transitionRenderer != null) transitionRenderer.cleanup();
        if (uiRenderer != null) uiRenderer.cleanup();
        if (window != null) window.cleanup();
    }
}
