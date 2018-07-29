package com.ouroboros;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.IntStream;

import static java.lang.Character.isWhitespace;
import static java.lang.Character.toChars;

public class Main {

    private static final int FILE_LINE_THRESHOLD = 2000;
    private static final int WORD_LENGTH_THRESHOLD = 50;

    private static final String TEMP_FOLDER = "temp";
    private static final String TEMP_LONG_WORD_FOLDER = "temp/long";
    private static final String TEMP_SORTED_WORD_FOLDER = "temp/sorted";

    private static final class TextArray implements Comparable<TextArray> {

        private int[] array;

        TextArray(int l) {
            this.array = new int[l];
            reset();
        }

        void reset() {
            for (int i = 0; i < array.length; i++) {
                array[i] = -1;
            }
        }

        void set(int index, int value) {
            array[index] = value;
        }

        void write(final FileWriter outputStream) throws IOException {
            if (array[0] != -1) {
                Arrays.stream(array).filter(i -> i >= 0).forEachOrdered(i -> {
                    try {
                        outputStream.write(toChars(i));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                outputStream.write("\n");
            }
        }

        boolean isEmpty() {
            return array[0] == -1;
        }

        boolean clone(TextArray textArray) {
            if (this.array.length == textArray.array.length) {
                for (int i = 0; i < this.array.length; i++) {
                    this.array[i] = textArray.array[i];
                }

                return true;
            }

            return false;
        }

        @Override
        public int compareTo(TextArray o) {
            return Arrays.compare(this.array, o.array);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TextArray textArray = (TextArray) o;
            return Arrays.equals(array, textArray.array);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(array);
        }
    }


    public static void main(String[] args) {
        if (args == null || args.length != 2) {
            System.out.println("Invalid arguments for the program.");
            return;
        }

        String inputFile = args[0];
        Path inputFilePath = Paths.get(inputFile);

        String outputFile = args[1];
        Path outputFilePath = Paths.get(outputFile);

        if (Files.notExists(inputFilePath) || !Files.isReadable(inputFilePath) || !Files.isRegularFile(inputFilePath)) {
            System.out.println("Invalid input file.");
            return;
        }


        if (Files.notExists(outputFilePath)) {
            try {
                Files.createFile(outputFilePath);
            } catch (IOException e) {
                System.out.println("Failed to create output file.");
                e.printStackTrace();
                return;
            }
        }

        if (Files.notExists(outputFilePath) || !Files.isWritable(outputFilePath) || !Files.isRegularFile(outputFilePath)) {
            System.out.println("Invalid output file.");
            return;
        }


        try {
            createTempFolder(TEMP_FOLDER);
            createTempFolder(TEMP_LONG_WORD_FOLDER);
            createTempFolder(TEMP_SORTED_WORD_FOLDER);

            Path analyzeFile = analyzeFile(inputFilePath);
            splitFileAndSortWord(analyzeFile);
            Path tempResultPath = mergeSortWord();
            tempResultPath = mergeLongWord(tempResultPath);

            copyFile(tempResultPath, outputFilePath);

        } catch (IOException | RuntimeException e) {
            System.out.println("Error occurred when running the program: ");
            e.printStackTrace();
        } finally {
            try {
                deleteTempFolder(TEMP_SORTED_WORD_FOLDER);
                deleteTempFolder(TEMP_LONG_WORD_FOLDER);
                deleteTempFolder(TEMP_FOLDER);
            } catch (IOException e) {
                System.out.println("Error occurred when deleting temp generated files.");
            }
        }
    }

    private static Path analyzeFile(Path filePath) throws IOException {

        try (FileReader inputStream = new FileReader(filePath.toFile().getAbsolutePath())) {

            Path analyzeResultFilePath = createTempFile(TEMP_FOLDER);

            try (FileWriter outputStream = new FileWriter(analyzeResultFilePath.toFile().getAbsolutePath())) {

                int wordLength = 0;
                StringBuilder stringBuilder = new StringBuilder(WORD_LENGTH_THRESHOLD + 2);

                int i;
                while ((i = inputStream.read()) != -1) {
                    wordLength++;

                    if (isWhitespace(i)) {
                        if (wordLength > 1) {
                            stringBuilder.append('\n');
                            outputStream.write(stringBuilder.toString());
                            stringBuilder.setLength(0);
                        }
                        wordLength = 0;
                    } else {
                        // If the word is too long for the buffer
                        if (wordLength > WORD_LENGTH_THRESHOLD) {
                            Path longWordFilePath = createTempFile(TEMP_LONG_WORD_FOLDER);

                            try (FileWriter longWordOutputStream = new FileWriter(longWordFilePath.toFile().getAbsolutePath())) {
                                longWordOutputStream.write(stringBuilder.toString());
                                stringBuilder.setLength(0);
                                wordLength = 0;

                                longWordOutputStream.write(toChars(i));

                                while ((i = inputStream.read()) != -1) {
                                    if (!isWhitespace(i)) {
                                        longWordOutputStream.write(toChars(i));
                                    } else {
                                        break;
                                    }
                                }
                            }
                        } else {
                            stringBuilder.append(toChars(i));
                        }
                    }
                }

                stringBuilder.append('\n');
                outputStream.write(stringBuilder.toString());
            }

            return analyzeResultFilePath;
        }
    }

    private static void splitFileAndSortWord(Path filePath) throws IOException {

        try (FileReader inputStream = new FileReader(filePath.toFile().getAbsolutePath())) {
            List<TextArray> textArrays = new ArrayList<>(FILE_LINE_THRESHOLD);
            IntStream.range(0, FILE_LINE_THRESHOLD).forEach(i -> textArrays.add(new TextArray(WORD_LENGTH_THRESHOLD)));

            int wordCount = 0;
            int charCount = 0;

            int i;
            while ((i = inputStream.read()) != -1) {
                if (!isWhitespace(i)) {
                    textArrays.get(wordCount).set(charCount, i);
                    charCount++;
                } else {
                    wordCount++;

                    if (wordCount == FILE_LINE_THRESHOLD) {
                        sortWordAndWriteFile(textArrays);
                        textArrays.forEach(TextArray::reset);
                        wordCount = 0;
                    }

                    charCount = 0;
                }
            }

            sortWordAndWriteFile(textArrays);
        }
    }

    private static void sortWordAndWriteFile(List<TextArray> textArrays) throws IOException {
        TreeSet<TextArray> textSet = new TreeSet<>(textArrays);

        Path sortedFilePath = createTempFile(TEMP_SORTED_WORD_FOLDER);

        try (FileWriter outputStream = new FileWriter(sortedFilePath.toFile().getAbsolutePath())) {
            textSet.forEach(a -> {
                try {
                    a.write(outputStream);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static Path mergeSortWord() throws IOException {
        List<Path> tempSortResultPath = new ArrayList<>(2);
        boolean[] usedFirstPath = new boolean[1];


        TextArray currentText1 = new TextArray(WORD_LENGTH_THRESHOLD);
        TextArray currentText2 = new TextArray(WORD_LENGTH_THRESHOLD);
        TextArray previousText = new TextArray(WORD_LENGTH_THRESHOLD);

        Files.walk(Paths.get(TEMP_SORTED_WORD_FOLDER)).filter(Files::isRegularFile).forEach(path -> {
            try {
                if (tempSortResultPath.isEmpty()) {
                    Path filePath1 = createTempFile(TEMP_FOLDER);
                    Path filePath2 = createTempFile(TEMP_FOLDER);

                    tempSortResultPath.add(filePath1);
                    tempSortResultPath.add(filePath2);

                    copyFile(path, filePath2);

                    usedFirstPath[0] = false;
                } else {
                    Path lastResultPath = tempSortResultPath.get(usedFirstPath[0] ? 0 : 1);
                    Path outputPath = tempSortResultPath.get(usedFirstPath[0] ? 1 : 0);
                    usedFirstPath[0] = !usedFirstPath[0];


                    try (FileWriter outputStream = new FileWriter(outputPath.toFile().getAbsolutePath(), false);
                         FileReader fileStream1 = new FileReader(path.toFile().getAbsolutePath());
                         FileReader fileStream2 = new FileReader(lastResultPath.toFile().getAbsolutePath())) {

                        readText(fileStream1, currentText1);
                        readText(fileStream2, currentText2);

                        while (!currentText1.isEmpty() || !currentText2.isEmpty()) {
                            if (currentText1.isEmpty()) {
                                writeText(outputStream, currentText2, previousText);
                                readText(fileStream2, currentText2);
                            } else if (currentText2.isEmpty()) {
                                writeText(outputStream, currentText1, previousText);
                                readText(fileStream1, currentText1);
                            } else {
                                int comp = currentText1.compareTo(currentText2);
                                if (comp <= 0) {
                                    writeText(outputStream, currentText1, previousText);
                                    readText(fileStream1, currentText1);
                                } else {
                                    writeText(outputStream, currentText2, previousText);
                                    readText(fileStream2, currentText2);
                                }
                            }

                        }
                    }
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        return tempSortResultPath.get(usedFirstPath[0] ? 0 : 1);
    }

    private static void readText(FileReader inputStream, TextArray textArray) throws IOException {
        textArray.reset();

        int count = 0;

        int i;
        while ((i = inputStream.read()) != -1) {
            if (isWhitespace(i)) {
                return;
            } else {
                textArray.set(count, i);
                count++;
            }
        }
    }

    private static void writeText(FileWriter outputStream, TextArray textArray, TextArray pTextArray) throws IOException {
        if (pTextArray.compareTo(textArray) != 0) {
            textArray.write(outputStream);
            pTextArray.clone(textArray);
        }
    }

    private static Path mergeLongWord(Path tempResultFilePath) throws IOException {
        List<Path> sortResultPath = new ArrayList<>(2);
        sortResultPath.add(tempResultFilePath);
        sortResultPath.add(createTempFile(TEMP_FOLDER));
        boolean[] usedFirstPath = new boolean[1];
        usedFirstPath[0] = true;

        Files.walk(Paths.get(TEMP_LONG_WORD_FOLDER)).filter(Files::isRegularFile).forEach(path -> {

            Path lastResultPath = sortResultPath.get(usedFirstPath[0] ? 0 : 1);
            Path outputPath = sortResultPath.get(usedFirstPath[0] ? 1 : 0);

            try (FileReader fileStream1 = new FileReader(lastResultPath.toFile().getAbsolutePath())) {

                int lineCount = 0;
                boolean finish = false;

                int i1;
                boolean b1;
                int i2;
                boolean b2;

                while (true) {
                    try (FileReader fileStream2 = new FileReader(path.toFile().getAbsolutePath())) {

                        while (true) {
                            i1 = fileStream1.read();
                            i2 = fileStream2.read();

                            b1 = isWhitespace(i1);
                            b2 = isWhitespace(i2);

                            if (i2 == -1 || b2) {
                                if (i1 != -1 && !b1) {
                                    finish = true;
                                    break;
                                } else {
                                    return;
                                }
                            } else {
                                if (i1 == -1) {
                                    lineCount++;
                                    finish = true;
                                    break;
                                } else if (b1) {
                                    break;
                                } else {
                                    if (i1 == i2) {
                                        continue;
                                    } else if (i1 > i2) {
                                        finish = true;
                                        break;
                                    } else  {
                                        while ((i1 = fileStream1.read()) != -1 && !isWhitespace(i1)) {
                                        }
                                        break;
                                    }
                                }
                            }

                        }

                    }

                    if (finish) {
                        appendWordLineToFile(path, lastResultPath, outputPath, lineCount);
                        usedFirstPath[0] = !usedFirstPath[0];
                        return;
                    }

                    lineCount++;
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        return sortResultPath.get(usedFirstPath[0] ? 0 : 1);
    }


    private static void copyFile(Path srcPath, Path destPath) throws IOException {
        try (FileWriter outputStream = new FileWriter(destPath.toFile().getAbsolutePath(), false);
             FileReader inputStream = new FileReader(srcPath.toFile().getAbsolutePath())) {
            int i;
            while ((i = inputStream.read()) != -1) {
                outputStream.write(toChars(i));
            }
        }
    }

    private static void appendWordLineToFile(Path wordFilePath, Path srcFilePath, Path destFilePath, int lineNum) throws IOException {
        try (FileReader inputStream = new FileReader(srcFilePath.toFile().getAbsolutePath());
            FileWriter outputStream = new FileWriter(destFilePath.toFile().getAbsoluteFile(), false)) {

            if (lineNum == 0) {
                writeWordLineToFile(wordFilePath, outputStream);

                int i;
                while ((i = inputStream.read()) != -1) {
                    outputStream.write(toChars(i));
                }
            } else {
                int lineCount = 0;

                int i;
                while ((i = inputStream.read()) != -1) {
                    outputStream.write(toChars(i));

                    if (isWhitespace(i)) {
                        lineCount++;

                        if (lineCount == lineNum) {
                            writeWordLineToFile(wordFilePath, outputStream);
                        }
                    }
                }

                if (lineCount < lineNum) {
                    writeWordLineToFile(wordFilePath, outputStream);
                }
            }
        }
    }

    private static void writeWordLineToFile(Path wordFilePath, FileWriter outputStream) throws IOException {
        try (FileReader wordStream = new FileReader(wordFilePath.toFile().getAbsoluteFile())) {
            int iw;
            while ((iw = wordStream.read()) != -1 && !isWhitespace(iw)) {
                outputStream.write(toChars(iw));
            }
            outputStream.write('\n');
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


}
