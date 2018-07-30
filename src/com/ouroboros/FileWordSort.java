package com.ouroboros;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.IntStream;

import static java.lang.Character.isWhitespace;
import static java.lang.Character.toChars;

/**
 * Run the program:<br>
 *      1. Compile the program: javac com.ouroboros.FileWordSort <br>
 *      2. Run the program: java -Xms2m -Xmx5m com.ouroboros.FileWordSort "path to input file" "path to output file" <br>
 * <br>
 * Assumptions:<br>
 *      1. The program can run with max heap size 5M<br>
 *      2. The input file contains text words only, separated by blank characters<br>
 *      3. There is enough free disk space: at least three times of the input file size<br>
 *      4. The number of words in the file is not greater than the number of files allowed to be created by the file system<br>
 * <br>
 * Algorithm:<br>
 *     In general, break the file to small files and sort the words in the small files.
 *     Then merge-sort the words from the small files.
 *     The sorting uses the unicode values of the characters in order of their appearance in the words. <br>
 *      1. Analyze the input file. Read the file character by character. Identify the words in the files.<br>
 *          a) For the words, the length of which are greater than <code>WORD_LENGTH_THRESHOLD</code>,
 *              save them character by character directly to separate files. One word takes one file.<br>
 *          b) For the short words, when reading it character by character, keep it in a reused buffer.
 *              Save each word to a file. Each word takes one line of the file.<br>
 *      2. Read the file containing the short words. Load the words batch by batch to a reused buffer in memory.
 *          The buffer has a number of entries not exceeding <code>FILE_LINE_THRESHOLD</code>.
 *          Each entry can save a number of characters not exceeding <code>WORD_LENGTH_THRESHOLD</code>.
 *          Sort the words in the buffer when the buffer is filled and eliminate duplicate words with a TreeSet.
 *          Save the sorted results of a batch to a separate file.<br>
 *      3. Merge-sort the words from the files containing the step 2 sorted results:
 *          each time, pick one file containing the sorted results from step 2,
 *          and merge-sort the words from this file with the previously merge-sorted results.
 *          When doing the merge-sort, each time, read the two words being compared to two reused buffers.<br>
 *      4. Insert the long words to the merge-sorted words.
 *          Read the long words from the files character by character, and read the sorted words from step 3 character by character.
 *          Compare the long words character by character with the sorted words.
 *          Insert the long words to the sorted words at the right position.<br>
 * <br>
 * Future Improvement:<br>
 *      1. Use NIO to improve performance<br>
 *      2. Create unit tests for the methods<br>
 *      3. Refactor code and tune the parameters for better performance<br>
 */
public class FileWordSort {

    private static final int FILE_LINE_THRESHOLD = 2000;        // The number of words in a batch to be kept buffer for sorting
    private static final int WORD_LENGTH_THRESHOLD = 50;        // The max number of characters each word is allowed to take in the buffer

    private static final String TEMP_FOLDER = UUID.randomUUID().toString();
    private static final String TEMP_LONG_WORD_FOLDER = TEMP_FOLDER + "/long";
    private static final String TEMP_SORTED_WORD_FOLDER = TEMP_FOLDER + "/sorted";

    /**
     * This class stores the unicode of the characters of a word in an int array.
     */
    private static final class TextArray implements Comparable<TextArray> {

        private int[] array;

        TextArray(int l) {
            this.array = new int[l];
            reset();
        }

        /**
         * Reset all entries of the array to -1.
         */
        void reset() {
            for (int i = 0; i < array.length; i++) {
                array[i] = -1;
            }
        }

        /**
         * Set the value of given entry in the array.
         *
         * @param index
         * @param value
         */
        void set(int index, int value) {
            array[index] = value;
        }

        /**
         * Write the characters of the word to the output stream.
         *
         * @param outputStream
         * @throws IOException
         */
        void write(final FileWriter outputStream) throws IOException {
            if (array[0] != -1) {
                // Ignore -1 value
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

        /**
         * Check if characters in the array are not set.
         * Check only the first entry, since the entries are supposed to be set in order.
         *
         * @return
         */
        boolean isNoChar() {
            return array[0] == -1;
        }

        /**
         * Duplicate the characters of another word.
         *
         * @param textArray
         * @return
         */
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


    /**
     * Main method of the program.
     *
     * @param args
     */
    public static void main(String[] args) {
        // Check if the input file and output file are specified in the arguments
        if (args == null || args.length != 2) {
            System.out.println("Invalid arguments for the program.");
            return;
        }

        String inputFile = args[0];
        Path inputFilePath = Paths.get(inputFile);

        String outputFile = args[1];
        Path outputFilePath = Paths.get(outputFile);

        // Check if the input file is valid
        if (Files.notExists(inputFilePath) || !Files.isReadable(inputFilePath) || !Files.isRegularFile(inputFilePath)) {
            System.out.println("Invalid input file.");
            return;
        }

        // Create the output file if it doesn't exist
        if (Files.notExists(outputFilePath)) {
            try {
                Files.createFile(outputFilePath);
            } catch (IOException e) {
                System.out.println("Failed to create output file.");
                e.printStackTrace();
                return;
            }
        }

        // Check if the output file is valid
        if (Files.notExists(outputFilePath) || !Files.isWritable(outputFilePath) || !Files.isRegularFile(outputFilePath)) {
            System.out.println("Invalid output file.");
            return;
        }


        try {
            // Create folders for the temp generated files
            createTempFolder(TEMP_FOLDER);
            createTempFolder(TEMP_LONG_WORD_FOLDER);
            createTempFolder(TEMP_SORTED_WORD_FOLDER);

            // Analyze the words from the input file
            // Save words which are too long to separate files
            Path analyzeFile = analyzeFile(inputFilePath);

            // Read the words batch by batch
            // And save each batch of sorted words to separate files
            splitFileAndSortWord(analyzeFile);
            Files.delete(analyzeFile);

            // Merge sort the batches of sorted words to one file
            Path tempResultPath = mergeSortWord();
            deleteTempFolder(TEMP_SORTED_WORD_FOLDER);

            // Insert long words to the sorted word
            tempResultPath = mergeLongWord(tempResultPath);
            deleteTempFolder(TEMP_LONG_WORD_FOLDER);

            // Copy to results to the output file
            copyFile(tempResultPath, outputFilePath);

        } catch (IOException | RuntimeException | Error e) {
            System.out.println("Error occurred when running the program: ");
            e.printStackTrace();
        } finally {
            try {
                // Cleanup the temp generated files
                deleteTempFolder(TEMP_FOLDER);
            } catch (IOException e) {
                System.out.println("Error occurred when deleting temp generated files.");
            }
        }
    }

    /**
     * Analyze the input file. Read the words one by one from the input file to a buffer.
     * And save each word to a file, each word takes one line.
     * For the words, the length of which exceed the threshold, save each of them to a separate file.
     *
     * @param filePath
     * @return
     * @throws IOException
     */
    private static Path analyzeFile(Path filePath) throws IOException {

        try (FileReader inputStream = new FileReader(filePath.toFile().getAbsolutePath())) {

            Path analyzeResultFilePath = createTempFile(TEMP_FOLDER);

            try (FileWriter outputStream = new FileWriter(analyzeResultFilePath.toFile().getAbsolutePath())) {

                int wordLength = 0;

                // Create a buffer of the given length to save each word in memory
                StringBuilder stringBuilder = new StringBuilder(WORD_LENGTH_THRESHOLD + 2);

                // Read the word from the input file, character by character
                int i;
                while ((i = inputStream.read()) != -1) {
                    wordLength++;


                    if (isWhitespace(i)) {      // Finish reading a word, save it to the file
                        if (wordLength > 1) {
                            stringBuilder.append('\n');
                            outputStream.write(stringBuilder.toString());
                            stringBuilder.setLength(0);
                        }
                        wordLength = 0;
                    } else {
                        // If the word is too long for the buffer, save it directly to a separate file, character by character
                        if (wordLength > WORD_LENGTH_THRESHOLD) {
                            Path longWordFilePath = createTempFile(TEMP_LONG_WORD_FOLDER);

                            try (FileWriter longWordOutputStream = new FileWriter(longWordFilePath.toFile().getAbsolutePath())) {
                                // First save what is already in the buffer
                                longWordOutputStream.write(stringBuilder.toString());
                                stringBuilder.setLength(0);
                                wordLength = 0;

                                // Then save the rest of the characters
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
                            // Keep adding character to buffer
                            stringBuilder.append(toChars(i));
                        }
                    }
                }

                // Finish reading the input file, save what's left in the buffer to the file
                stringBuilder.append('\n');
                outputStream.write(stringBuilder.toString());
            }

            return analyzeResultFilePath;
        }
    }

    /**
     * Read the words from the given file, batch by batch.
     * Store each batch of words in the buffer.
     * Sort each bacth of words in the buffer.
     * Save each batch of sorted words to a separate file.
     *
     * @param filePath
     * @throws IOException
     */
    private static void splitFileAndSortWord(Path filePath) throws IOException {

        try (FileReader inputStream = new FileReader(filePath.toFile().getAbsolutePath())) {
            // Create a buffer to save a batch of word in memory for sorting
            List<TextArray> textArrays = new ArrayList<>(FILE_LINE_THRESHOLD);
            IntStream.range(0, FILE_LINE_THRESHOLD).forEach(i -> textArrays.add(new TextArray(WORD_LENGTH_THRESHOLD)));

            int wordCount = 0;
            int charCount = 0;

            // Read the words from the given file
            int i;
            while ((i = inputStream.read()) != -1) {
                if (!isWhitespace(i)) {     // Read one character of a word and put it to the buffer
                    textArrays.get(wordCount).set(charCount, i);
                    charCount++;
                } else {        // Finish reading a word
                    wordCount++;

                    // If the buffer is filled
                    // Sort the words in the buffer
                    // Save the sorted words to a new file
                    // Reset the buffer
                    if (wordCount == FILE_LINE_THRESHOLD) {
                        sortWordAndWriteFile(textArrays);
                        textArrays.forEach(TextArray::reset);
                        wordCount = 0;
                    }

                    charCount = 0;
                }
            }

            // Sort the last batch of words
            // Save them to a file
            sortWordAndWriteFile(textArrays);
        }
    }

    private static final TreeSet<TextArray> SORT_TOOL = new TreeSet<>();

    /**
     * With a TreeSet, sort the words in the given list and remove duplicate words.
     * Save the sorted words to a new file.
     *
     * @param textArrays
     * @throws IOException
     */
    private static void sortWordAndWriteFile(List<TextArray> textArrays) throws IOException {
        SORT_TOOL.addAll(textArrays);     // Sort and remove duplicates

        Path sortedFilePath = createTempFile(TEMP_SORTED_WORD_FOLDER);

        // Save to a new file
        try (FileWriter outputStream = new FileWriter(sortedFilePath.toFile().getAbsolutePath())) {
            SORT_TOOL.forEach(a -> {
                try {
                    a.write(outputStream);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } finally {
            SORT_TOOL.clear();
        }
    }

    /**
     * Merge sort the sorted words to a single file.
     *
     * @return the path of the file that contains the final results
     * @throws IOException
     */
    private static Path mergeSortWord() throws IOException {
        // Create two files to save temp merge results turn by turn.
        // If in this turn, results are saved in file one,
        // then in next turn, words are read from file one to be merged, and the merge results are saved in file two.
        List<Path> tempSortResultPath = new ArrayList<>(2);
        boolean[] usedFirstPath = new boolean[1];       // Record which file was used to save results in last turn

        // Buffers to keep the two current comparing words and the last merged word
        TextArray currentText1 = new TextArray(WORD_LENGTH_THRESHOLD);
        TextArray currentText2 = new TextArray(WORD_LENGTH_THRESHOLD);
        TextArray previousText = new TextArray(WORD_LENGTH_THRESHOLD);

        // Go through files of the sorted words
        Files.walk(Paths.get(TEMP_SORTED_WORD_FOLDER)).filter(Files::isRegularFile).forEach(path -> {
            try {
                if (tempSortResultPath.isEmpty()) {     // Take the words from the first encountered file as the initial merge results
                    Path filePath1 = createTempFile(TEMP_FOLDER);
                    Path filePath2 = createTempFile(TEMP_FOLDER);

                    tempSortResultPath.add(filePath1);
                    tempSortResultPath.add(filePath2);

                    copyFile(path, filePath2);

                    usedFirstPath[0] = false;
                } else {
                    // Decide which file has the results from last turn and which file to save the results for current turn
                    Path lastResultPath = tempSortResultPath.get(usedFirstPath[0] ? 0 : 1);
                    Path outputPath = tempSortResultPath.get(usedFirstPath[0] ? 1 : 0);
                    usedFirstPath[0] = !usedFirstPath[0];

                    // Read the words from current encountered file
                    // Read the previously merge sorted words
                    try (FileWriter outputStream = new FileWriter(outputPath.toFile().getAbsolutePath(), false);
                         FileReader fileStream1 = new FileReader(path.toFile().getAbsolutePath());
                         FileReader fileStream2 = new FileReader(lastResultPath.toFile().getAbsolutePath())) {

                        readText(fileStream1, currentText1);
                        readText(fileStream2, currentText2);

                        // Loop until words from both files are visited
                        while (!currentText1.isNoChar() || !currentText2.isNoChar()) {
                            if (currentText1.isNoChar()) {      // If no more words from the first file, save all the rest words from the second file
                                writeText(outputStream, currentText2, previousText);
                                readText(fileStream2, currentText2);
                            } else if (currentText2.isNoChar()) {       // If no more words from the second file, save all the rest words from the first file
                                writeText(outputStream, currentText1, previousText);
                                readText(fileStream1, currentText1);
                            } else {
                                // Compare the words and save the smaller one
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

    /**
     * Read a word from file to buffer
     *
     * @param inputStream
     * @param textArray
     * @throws IOException
     */
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

    /**
     * Write a word from buffer to file, only if the word is not the same as the previously saved one
     *
     * @param outputStream
     * @param textArray
     * @param pTextArray
     * @throws IOException
     */
    private static void writeText(FileWriter outputStream, TextArray textArray, TextArray pTextArray) throws IOException {
        if (pTextArray.compareTo(textArray) != 0) {
            textArray.write(outputStream);
            pTextArray.clone(textArray);        // Update the previously save word for comparison in next turn
        }
    }

    /**
     * Insert the words, which are too long for the buffer, to the sorted words
     *
     * @param tempResultFilePath
     * @return
     * @throws IOException
     */
    private static Path mergeLongWord(Path tempResultFilePath) throws IOException {
        // Create two files to save temp insertion results turn by turn.
        // If in this turn, results are saved in file one,
        // then in next turn, words are read from file one, and the insertion results are saved in file two.
        List<Path> sortResultPath = new ArrayList<>(2);
        sortResultPath.add(tempResultFilePath);
        sortResultPath.add(createTempFile(TEMP_FOLDER));
        boolean[] usedFirstPath = new boolean[1];       // Record which file was used to save results in last turn
        usedFirstPath[0] = true;

        // Go through all the files containing the long words
        Files.walk(Paths.get(TEMP_LONG_WORD_FOLDER)).filter(Files::isRegularFile).forEach(path -> {

            // Decide which file has the results from last turn and which file to save the results for current turn
            Path lastResultPath = sortResultPath.get(usedFirstPath[0] ? 0 : 1);
            Path outputPath = sortResultPath.get(usedFirstPath[0] ? 1 : 0);

            try (FileReader fileStream1 = new FileReader(lastResultPath.toFile().getAbsolutePath())) {

                int lineCount = 0;
                boolean finish = false;

                int i1;
                boolean b1;
                int i2;
                boolean b2;

                // Read the long word again for every word comparison
                while (true) {
                    try (FileReader fileStream2 = new FileReader(path.toFile().getAbsolutePath())) {

                        // Read every character of the words to compare
                        while (true) {
                            i1 = fileStream1.read();
                            i2 = fileStream2.read();

                            b1 = isWhitespace(i1);
                            b2 = isWhitespace(i2);

                            if (i2 == -1 || b2) {   // Reach the end of the long word
                                if (i1 != -1 && !b1) {  // The sorted word is longer than the long word,
                                                        // insert the long word here
                                    finish = true;
                                    break;
                                } else {    // Also reach the end of the sorted word,
                                            // means the long word is a duplicate, skip it
                                    return;
                                }
                            } else {    // Not reach the end of the long word
                                if (i1 == -1) {     // Reach the end of the sorted word file,
                                                    // Insert the long word to the end of the file
                                    lineCount++;
                                    finish = true;
                                    break;
                                } else if (b1) {    // Reach the end of the sorted word,
                                                    // compare with next sorted word
                                    break;
                                } else {    // Compare one character of the long word with one character of the sorted word
                                    if (i1 == i2) {
                                        continue;
                                    } else if (i1 > i2) {   // if the character of the long word is smaller, insert long word here
                                        finish = true;
                                        break;
                                    } else  {   // if the character of the long word is greater, skip to the next sorted word
                                        while ((i1 = fileStream1.read()) != -1 && !isWhitespace(i1)) {
                                        }
                                        break;
                                    }
                                }
                            }

                        }

                    }

                    if (finish) {   // insert the long word to the file
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

    /**
     * Read the contents of the source file, character by character.
     * Write the characters sequentially to the destination file.
     *
     * @param srcPath
     * @param destPath
     * @throws IOException
     */
    private static void copyFile(Path srcPath, Path destPath) throws IOException {
        try (FileWriter outputStream = new FileWriter(destPath.toFile().getAbsolutePath(), false);
             FileReader inputStream = new FileReader(srcPath.toFile().getAbsolutePath())) {
            int i;
            while ((i = inputStream.read()) != -1) {
                outputStream.write(toChars(i));
            }
        }
    }

    /**
     * Insert a word to the given line of the file
     *
     * @param wordFilePath  read the word to be inserted from this file
     * @param srcFilePath  insert the word among the words of this file
     * @param destFilePath  save the words of the insertion results to this file
     * @param lineNum  the line number where the word should be inserted
     * @throws IOException
     */
    private static void appendWordLineToFile(Path wordFilePath, Path srcFilePath, Path destFilePath, int lineNum) throws IOException {
        try (FileReader inputStream = new FileReader(srcFilePath.toFile().getAbsolutePath());
            FileWriter outputStream = new FileWriter(destFilePath.toFile().getAbsoluteFile(), false)) {

            // If insert to the first line
            if (lineNum == 0) {
                // Insert the word
                writeWordLineToFile(wordFilePath, outputStream);

                // Append the rest of the words
                int i;
                while ((i = inputStream.read()) != -1) {
                    outputStream.write(toChars(i));
                }
            } else {
                int lineCount = 0;

                // Copy the words until the given line is reached
                int i;
                while ((i = inputStream.read()) != -1) {
                    outputStream.write(toChars(i));

                    if (isWhitespace(i)) {
                        lineCount++;        // new line

                        if (lineCount == lineNum) {     // if the given line is reached, insert the given word
                            writeWordLineToFile(wordFilePath, outputStream);
                        }
                    }
                }

                // Insert the word to the end
                if (lineCount < lineNum) {
                    writeWordLineToFile(wordFilePath, outputStream);
                }
            }
        }
    }

    /**
     * Read the first word from the file of the given path, a character by character.
     * And write the characters to the output stream.
     *
     * @param wordFilePath
     * @param outputStream
     * @throws IOException
     */
    private static void writeWordLineToFile(Path wordFilePath, FileWriter outputStream) throws IOException {
        try (FileReader wordStream = new FileReader(wordFilePath.toFile().getAbsoluteFile())) {
            int iw;
            while ((iw = wordStream.read()) != -1 && !isWhitespace(iw)) {
                outputStream.write(toChars(iw));
            }
            outputStream.write('\n');
        }
    }

    /**
     * Create a folder with the given name.
     *
     * @param folderName
     * @throws IOException
     */
    private static void createTempFolder(String folderName) throws IOException {
        Path tempFolderPath = Paths.get(folderName);
        if (Files.notExists(tempFolderPath) || !Files.isDirectory(tempFolderPath)) {
            Files.createDirectory(tempFolderPath);
        }
    }

    /**
     * Delete the given folder as well as all the sub-folders and files.
     *
     * @param folderName
     * @throws IOException
     */
    private static void deleteTempFolder(String folderName) throws IOException {
        Path tempFolderPath = Paths.get(folderName);

        if (Files.exists(tempFolderPath) && Files.isDirectory(tempFolderPath)) {
            Files.walk(tempFolderPath)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    /**
     * Create a temp file in the given folder.
     *
     * @param folderName
     * @return
     * @throws IOException
     */
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
