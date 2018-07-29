package com.ouroboros;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.IntStream;

public class Main {

    private static final int FILE_LINE_THRESHOLD = 2000;
    private static final int WORD_LENGTH_THRESHOLD = 50;

    private static final String TEMP_FOLDER = "temp";
    private static final String TEMP_LONG_WORD_FOLDER = "temp/long";
    private static final String TEMP_SORTED_WORD_FOLDER = "temp/sorted";

    private static final class TextArray implements Comparable<TextArray> {

        private int[] array;
        private String[] strArray;

        TextArray(int l) {
            this.array = new int[l];
            this.strArray = new String[l];
            reset();
        }

        void reset() {
            for (int i = 0; i < array.length; i++) {
                array[i] = -1;
                strArray[i] = null;
            }
        }

        void set(int index, int value) {
            array[index] = value;
            strArray[index] = Character.toString((char) value);
        }

        void write(final FileWriter outputStream) throws IOException {
            if (strArray[0] != null) {
                Arrays.stream(strArray).filter(Objects::nonNull).forEachOrdered(s -> {
                    try {
                        outputStream.write(s);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                outputStream.write("\n");
            }
        }

        boolean isEmpty() {
            return strArray[0] == null;
        }

        boolean clone(TextArray textArray) {
            if (this.array.length == textArray.array.length) {
                for (int i = 0; i < this.array.length; i++) {
                    this.array[i] = textArray.array[i];
                    this.strArray[i] = textArray.strArray[i];
                }

                return true;
            }

            return false;
        }

        @Override
        public int compareTo(TextArray o) {
            return Arrays.compare(this.strArray, o.strArray);
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

    private static final Comparator<int[]> INT_ARRAY_COMPARATOR = (o1, o2) -> {

        if (o1.length == o2.length) {
            for (int i = 0; i < o1.length; i++) {
                if (o1[i] != o2[i]) {
                    return o1[i] - o2[i];
                }
            }
        } else if (o1.length > o2.length) {
            for (int i = 0; i < o2.length; i++) {
                if (o1[i] != o2[i]) {
                    return o1[i] - o2[i];
                }
            }

            return 1;
        } else {
            for (int i = 0; i < o1.length; i++) {
                if (o1[i] != o2[i]) {
                    return o1[i] - o2[i];
                }
            }

            return -1;
        }

        return 0;
    };


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
            createTempFolder(TEMP_SORTED_WORD_FOLDER);

            Path analyzeFile = analyzeFile(path);
            splitFileAndSortWord(analyzeFile);
            Path tempResultPath = mergeSortWord();

            System.out.println(tempResultPath.toFile().getAbsolutePath());

        } catch (IOException | RuntimeException e) {
            e.printStackTrace();

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

    private static Path analyzeFile(Path filePath) throws IOException {

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
                        if (wordLength > WORD_LENGTH_THRESHOLD) {
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
            String s;
            while ((i = inputStream.read()) != -1) {
                s = Character.toString((char) i);

                if (!isBlank(s)) {
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
        tempSortResultPath.add(createTempFile(TEMP_SORTED_WORD_FOLDER));
        boolean[] usedFirstPath = new boolean[1];
        usedFirstPath[0] = false;

        TextArray currentText1 = new TextArray(WORD_LENGTH_THRESHOLD);
        TextArray currentText2 = new TextArray(WORD_LENGTH_THRESHOLD);
        TextArray previousText = new TextArray(WORD_LENGTH_THRESHOLD);

        Files.walk(Paths.get(TEMP_SORTED_WORD_FOLDER)).filter(Files::isRegularFile).forEach(path -> {
            try {
                if (tempSortResultPath.size() < 2) {
                    tempSortResultPath.add(path);
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
        String s;
        while ((i = inputStream.read()) != -1) {
            s = Character.toString((char) i);

            if (isBlank(s)) {
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
