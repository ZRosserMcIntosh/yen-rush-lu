package com.therushlight;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * On Time
 * After John Milton's poem.
 * "A small light is still a light."
 *
 * A narrative adventure in 100 chapters.
 * For Yenevieve, Rush, and Luísa.
 *
 * The lie: "If I do everything right, I can keep everyone safe."
 * The truth: You can't. But you can choose who you become in the wreckage.
 */
public class Main {

    private static final String RESTART_FLAG = "rushlight.restarted";
    public static final String VERSION = "0.1.0";

    public static void main(String[] args) {
        // macOS requires -XstartOnFirstThread for GLFW
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac") && System.getProperty(RESTART_FLAG) == null) {
            List<String> vmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
            boolean hasFirstThread = vmArgs.stream()
                    .anyMatch(arg -> arg.contains("XstartOnFirstThread"));

            if (!hasFirstThread) {
                try {
                    String javaHome = System.getProperty("java.home");
                    String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
                    String classpath = System.getProperty("java.class.path");

                    List<String> command = new ArrayList<>();
                    command.add(javaBin);
                    command.add("-XstartOnFirstThread");
                    command.add("-D" + RESTART_FLAG + "=true");
                    command.addAll(vmArgs);
                    command.add("-cp");
                    command.add(classpath);
                    command.add(Main.class.getName());
                    for (String arg : args) command.add(arg);

                    ProcessBuilder pb = new ProcessBuilder(command);
                    pb.inheritIO();
                    Process process = pb.start();
                    System.exit(process.waitFor());
                } catch (Exception e) {
                    System.err.println("Failed to restart with -XstartOnFirstThread: " + e.getMessage());
                    System.exit(1);
                }
                return;
            }
        }

        System.out.println();
        System.out.println("  ╔══════════════════════════════════════╗");
        System.out.println("  ║             O N   T I M E           ║");
        System.out.println("  ║    \"A small light is still a light\" ║");
        System.out.println("  ║                                      ║");
        System.out.println("  ║          v" + VERSION + "                        ║");
        System.out.println("  ╚══════════════════════════════════════╝");
        System.out.println();

        GameEngine engine = new GameEngine();
        engine.run();
    }
}
