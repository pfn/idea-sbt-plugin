// Copyright © 2010, Esko Luontola <www.orfjackal.net>
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

package net.orfjackal.sbt.runner;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.io.*;
import java.util.*;

public class SbtRunner {

    private static final String PROMPT = "\n> ";
    private static final String SCALA_PROMPT = "\nscala> ";
    private static final String FAILED_TO_COMPILE_PROMPT = "Hit enter to retry or 'exit' to quit:";
    private static final String PROMPT_AFTER_EMPTY_ACTION = "> ";
    private static final String ERROR_RUNNING_ACTION_PREFIX = "[error] Error running ";
    private static final String ERROR_SBT_010_PREFIX = "[error] Total time:";
    private static final Logger LOG = Logger.getInstance("#orfjackal.sbt.runner.SbtRunner");

    private final ProcessRunner sbt;
    private final String[] command;

    public SbtRunner(String javaCommand, File workingDir, File launcherJar, String[] vmParameters) {
        if (!workingDir.isDirectory()) {
            throw new IllegalArgumentException("Working directory does not exist: " + workingDir);
        }
        if (!launcherJar.isFile()) {
            throw new IllegalArgumentException("Launcher JAR file does not exist: " + launcherJar);
        }
        command = getCommand(javaCommand, launcherJar, vmParameters);
        sbt = new ProcessRunner(workingDir, command);
    }

    public final String getFormattedCommand() {
        StringBuilder sb = new StringBuilder();
        for (String s : command) {
            if (s.contains(" ")) {
                sb.append("\"").append(s).append("\"");
            } else {
                sb.append(s);
            }
            sb.append(" ");
        }
        return sb.toString();
    }

    private static String[] getCommand(String javaCommand, File launcherJar, String[] vmParameters) {
        List<String> command = new ArrayList<String>();

        command.add(javaCommand);
        command.add("-Dsbt.log.noformat=true");
        command.add("-Djline.terminal=jline.UnsupportedTerminal");
        command.addAll(Arrays.asList(vmParameters));
        command.addAll(Arrays.asList(
                "-jar",
                launcherJar.getAbsolutePath()
        ));

        LOG.info("SBT command line: " + StringUtil.join(command, " "));
        return command.toArray(new String[command.size()]);
    }

    public OutputReader subscribeToOutput() {
        return sbt.subscribeToOutput();
    }

    public void start(boolean wait) throws IOException {
        // TODO: detect if the directory does not have a project
        OutputReader output = sbt.subscribeToOutput();
        sbt.start();
        sbt.destroyOnShutdown();
        if (wait) {
            output.waitForOutput(Arrays.asList(PROMPT, FAILED_TO_COMPILE_PROMPT), Arrays.<String>asList());
        }
        output.close();
    }

    public void start(boolean wait, final Runnable onStarted) throws IOException {
        // TODO: detect if the directory does not have a project
        final OutputReader output = sbt.subscribeToOutput();
        sbt.start();
        sbt.destroyOnShutdown();
        if (wait) {
            output.waitForOutput(Arrays.asList(PROMPT, FAILED_TO_COMPILE_PROMPT), Arrays.<String>asList());
            output.close();
            onStarted.run();
        } else {
            new Thread() {
                @Override
                public void run() {
                    try {
                        output.waitForOutput(Arrays.asList(PROMPT, FAILED_TO_COMPILE_PROMPT), Arrays.<String>asList());
                        output.close();
                        onStarted.run();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }.start();
        }
    }

    public void destroy() {
        sbt.destroy();
    }

    public boolean isAlive() {
        return sbt.isAlive();
    }

    /**
     * @param action the SBT action to run, e.g. "compile"
     * @return false if an error was parsed from the output, true otherwise
     * @throws java.io.IOException
     */
    public boolean execute(String action) throws IOException {
        OutputReader output = sbt.subscribeToOutput();
        try {
            sbt.writeInput(action + "\n");
            output.waitForOutput(Arrays.asList(PROMPT, SCALA_PROMPT, FAILED_TO_COMPILE_PROMPT), Arrays.asList(PROMPT_AFTER_EMPTY_ACTION));
        } finally {
            output.close();
        }
        boolean error = output.endOfOutputContains(ERROR_RUNNING_ACTION_PREFIX) || output.endOfOutputContains(ERROR_SBT_010_PREFIX);
        LOG.debug("completed: " + action + ", error: " + error);
        return !error;
    }
}
