package com.ouroboros;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.UUID;

public class Main {

    private static final int FILE_LINE_THRESHOLD = 100;
    private static final int WORD_LENGTH_THRESHOLD = 20;

    private static final String TEMP_FOLDER = "temp";
    private static final String TEMP_LONG_WORD_FOLDER = "temp/long";

    public static void main(String[] args) {
        if (args == null || args.length != 1) {
            //TODO
        }

        String filePath = args[0];
        Path path = Paths.get(filePath);

        try {
            if (Files.notExists(path) || !Files.isReadable(path) || !Files.isRegularFile(path) || Files.size(path) > 0L) {
                //TODO
            }
        } catch (IOException e) {
            //TODO
        }

//        try {
//            Thread.sleep(10000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

        try {
            createTempFolder(TEMP_FOLDER);
            createTempFolder(TEMP_LONG_WORD_FOLDER);

            analyzeFile(path);

        } catch (IOException e) {
            //TODO
        } finally {
//            try {
//                deleteTempFolder();
//                deleteTempFolder();
//            } catch (IOException e) {
//                //TODO
//            }
        }


    }

    private static void createTempFolder(String folderName) throws IOException {
        Path tempFolderPath = Paths.get(folderName);
        if (Files.notExists(tempFolderPath) || !Files.isDirectory(tempFolderPath)) {
            Files.createDirectory(tempFolderPath);
        }
    }

    private static void deleteTempFolder(String folderName) throws IOException {
        Path tempFolderPath = Paths.get(folderName);

        if (Files.exists(tempFolderPath) && Files.isDirectory(tempFolderPath)) {
            Files.walk(tempFolderPath)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    private static Path createTempFile(String folderName) throws IOException {
        while (true) {
            String fileName = UUID.randomUUID().toString();
            Path filePath = Paths.get(folderName + "/" + fileName);

            if (Files.notExists(filePath)) {
                Files.createFile(filePath);
                return filePath;
            }
        }
    }


    private static void analyzeFile(Path filePath) throws IOException {

        try (FileReader inputStream = new FileReader(filePath.toFile().getAbsolutePath())) {

            Path analyzeResultFilePath = createTempFile(TEMP_FOLDER);

            try (FileWriter outputStream = new FileWriter(analyzeResultFilePath.toFile().getAbsolutePath())) {

                int wordLength = 0;
                StringBuilder stringBuilder = new StringBuilder(WORD_LENGTH_THRESHOLD + 2);

                int i;
                String s;
                while ((i = inputStream.read()) != -1) {
                    s = Character.toString((char) i);
                    wordLength++;

                    if (isBlank(s)) {
                        if (wordLength > 1) {
                            stringBuilder.append('\n');
                            outputStream.write(stringBuilder.toString());
                            stringBuilder.setLength(0);
                        }
                        wordLength = 0;
                    } else {
                        // If the word is too long for the buffer
                        if (wordLength >= WORD_LENGTH_THRESHOLD) {
                            Path longWordFilePath = createTempFile(TEMP_LONG_WORD_FOLDER);

                            try (FileWriter longWordOutputStream = new FileWriter(longWordFilePath.toFile().getAbsolutePath())) {
                                longWordOutputStream.write(stringBuilder.toString());
                                stringBuilder.setLength(0);
                                wordLength = 0;

                                longWordOutputStream.write(s);

                                while ((i = inputStream.read()) != -1) {
                                    s = Character.toString((char) i);

                                    if (!isBlank(s)) {
                                        longWordOutputStream.write(s);
                                    } else {
                                        break;
                                    }
                                }
                            }
                        } else {
                            stringBuilder.append(s);
                        }
                    }
                }

                stringBuilder.append('\n');
                outputStream.write(stringBuilder.toString());
            }

        }

    }

    private static boolean isBlank(final CharSequence cs) {
        int strLen;
        if (cs == null || (strLen = cs.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
