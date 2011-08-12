// Copyright 2011, Ernst de Haan
package org.znerd.lessc2java;

import java.io.File;
import java.io.IOException;

import org.znerd.lessc2java.ant.Buffer;
import static org.znerd.util.log.Limb.log;
import static org.znerd.util.log.LogLevel.*;
import static org.znerd.util.text.TextUtils.isEmpty;
import static org.znerd.util.io.CheckDirUtils.checkDir;

class LesscExecutor {

    public LesscExecutor(CommandRunner commandRunner, File sourceDir, File targetDir, String command, boolean overwrite) {
        // TODO: Check arguments?

        _commandRunner = commandRunner;
        _sourceDir = sourceDir;
        _targetDir = targetDir;
        _command = command;
        _overwrite = overwrite;
    }

    private final CommandRunner _commandRunner;
    private final File _sourceDir;
    private final File _targetDir;
    private final String _command;
    private final boolean _overwrite;

    public void execute() throws IOException {
        checkDirs();
        determineVersionAndProcessFiles();
    }

    private void checkDirs() throws IOException {
        checkDir("Source directory", _sourceDir, true, false, false);
        checkDir("Destination directory", _targetDir, false, true, true);
    }

    private void determineVersionAndProcessFiles() throws IOException {
        determineCommandVersion();
        processFiles(_sourceDir, _targetDir);
    }

    private void determineCommandVersion() throws IOException {
        String output = executeVersionCommand();
        String version = parseVersionString(output);
        log(INFO, "Using command " + quote(_command) + ", version is " + quote(version) + '.');
    }

    private String executeVersionCommand() throws IOException {
        CommandRunResult runResult = _commandRunner.runCommand(_command, "-v");
        IOException cause = runResult.getException();
        if (cause != null) {
            throw new IOException("Failed to execute LessCSS command " + quote(_command) + '.', cause);
        }
        int exitCode = runResult.getExitCode();
        if (exitCode != 0) {
            throw new IOException("Failed to execute LessCSS command " + quote(_command) + " with the argument \"-v\". Received exit code " + exitCode + '.');
        }
        return runResult.getOutString();
    }

    private String parseVersionString(final String output) {
        String version = output.trim();
        if (version.startsWith(_command)) {
            version = version.substring(_command.length()).trim();
        }
        if (version.startsWith("v")) {
            version = version.substring(1).trim();
        }
        return version;
    }

    private static final String quote(String s) {
        return s == null ? "(null)" : "\"" + s + '"';
    }

    private void processFiles(File sourceDir, File destDir) throws IOException {
        log(INFO, "Transforming from " + sourceDir.getPath() + " to " + destDir.getPath() + '.');
        String[] includedFiles = getDirectoryScanner(sourceDir).getIncludedFiles();
        long start = System.currentTimeMillis();
        int failedCount = 0, successCount = 0, skippedCount = 0;
        for (String inFileName : includedFiles) {
            switch (processFile(sourceDir, destDir, inFileName)) {
                case SKIPPED:
                    skippedCount++;
                    break;
                case SUCCEEDED:
                    successCount++;
                    break;
                case FAILED:
                    failedCount++;
                    break;
            }
        }
        handleGrandResult(start, skippedCount, successCount, failedCount);
    }

    private void handleGrandResult(long start, int skippedCount, int successCount, int failedCount) throws IOException {
        long duration = System.currentTimeMillis() - start;
        if (failedCount > 0) {
            throw new IOException("" + failedCount + " file(s) failed to transform, while " + successCount + " succeeded. Total duration is " + duration + " ms.");
        } else {
            log(NOTICE, "" + successCount + " file(s) transformed in " + duration + " ms; " + skippedCount + " file(s) skipped.");
        }
    }

    private FileTransformResult processFile(File sourceDir, File destDir, String inFileName) {

        File inFile = new File(sourceDir, inFileName);
        long thisStart = System.currentTimeMillis();
        String outFileName = inFile.getName().replaceFirst("\\.less$", ".css");
        File outFile = new File(destDir, outFileName);
        String outFilePath = outFile.getPath();
        String inFilePath = inFile.getPath();

        FileTransformResult result;
        if (shouldSkipFile(inFileName, inFile, outFile)) {
            result = FileTransformResult.SKIPPED;
        } else {
            result = transform(inFileName, thisStart, outFilePath, inFilePath);
        }
        return result;
    }

    private boolean shouldSkipFile(String inFileName, File inFile, File outFile) {
        boolean skip = false;
        if (!_overwrite) {
            if (outFile.exists() && (outFile.lastModified() > inFile.lastModified())) {
                log(INFO, "Skipping " + quote(inFileName) + " because output file is newer.");
                skip = true;
            }
        }
        return skip;
    }

    private FileTransformResult transform(String inFileName, long thisStart, String outFilePath, String inFilePath) {
        CommandRunResult runResult = _commandRunner.runCommand(_command, inFilePath, outFilePath);
        long duration = System.currentTimeMillis() - thisStart;
        if (runResult.isSucceeded()) {
            logSucceededTransformation(inFileName, duration);
            return FileTransformResult.SUCCEEDED;
        } else {
            logFailedTransformation(inFilePath, duration, runResult.getErrString());
            return FileTransformResult.FAILED;
        }
    }

    private void logFailedTransformation(String inFilePath, long duration, String errorOutput) {
        String logMessage = "Failed to transform " + quote(inFilePath) + " (took " + duration + " ms)";
        if (isEmpty(errorOutput)) {
            logMessage += '.';
        } else {
            logMessage += ':' + System.getProperty("line.separator") + errorOutput;
        }
        log(ERROR, logMessage);
    }

    private void logSucceededTransformation(String inFileName, long duration) {
        log(INFO, "Transformed " + quote(inFileName) + " (took " + duration + " ms).");
    }

    private enum FileTransformResult {
        SKIPPED, SUCCEEDED, FAILED;
    }
}