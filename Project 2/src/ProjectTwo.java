// Author: Ryan Varnell, 2023
// CSCI 4130 - Information Retrieval

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InformationRetrieval {
    public static final HashMap<String, ArrayList<Integer>> positionalIndex = new HashMap<>();

    public static void main(String[] args) {
        // Store the relative path to the directory containing all the files to be included.
        String path = "corpus";
        File directory = new File(path);

        // Tokenize the files and build a positional index.
        buildIndex(directory);

        // Prompt the user for a query.
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter a query: ");
        String query = scanner.nextLine();

        // Build another HashMap with the user's query using relevant doc index values in the positional index.
        // Using a Linked Hash Map ensures there will be no duplicate lists, but there shouldn't be anyway.
        HashMap<String, ArrayList<Integer>> queryMap = new LinkedHashMap<>();
        for (String word : normalizeIntoArray(query)) {
            ArrayList<Integer> indexList = positionalIndex.get(word);
            if (indexList != null) {
                queryMap.put(word, positionalIndex.get(word));
            } else {
                queryMap.put(word, new ArrayList<>());
            }
        }

        // Get all the relevant documents and give them to the user.
        ArrayList<Integer> relevantDocs = mapIntersect(queryMap);
        System.out.println("Relevant Documents: " + relevantDocs);

        // For project two: builds and posts the Variable Byte encoded gap list.
        for (Map.Entry<String, ArrayList<String>> entry : encodeMap().entrySet()) {
            System.out.println(entry);
        }
    }

    /**
     * Encodes an entire positional index as a gap list in Variable Byte formatting.
     *
     * @return The positional index in Variable Byte formatting.
     */
    private static HashMap<String, ArrayList<String>> encodeMap() {
        HashMap<String, ArrayList<String>> encodedMap = new HashMap<>();

        // For each key-value entry in the map, get the Variable Byte encoded format of the values and then enter the
        // new key-value pair into 'encodedMap'.
        for (Map.Entry<String, ArrayList<Integer>> entry : InformationRetrieval.positionalIndex.entrySet()) {
            encodedMap.put(entry.getKey(), VBEncode(entry.getValue()));
        }

        return encodedMap;
    }

    /**
     * Encodes a list of indices in Variable Byte formatting.
     * @param list The list of indices to be encoded.
     * @return The gap list of indices in Variable Byte formatting.
     */
    private static ArrayList<String> VBEncode(ArrayList<Integer> list) {
        ArrayList<String> encodedList = new ArrayList<>();
        Stack<Character> stack = new Stack<>();

        // Convert each value in the list into a binary string.
        int previousValue = 0;
        for (int value : list) {
            int gap = value - previousValue;
            StringBuilder variableByte = new StringBuilder();
            char[] binary = Integer.toBinaryString(gap).toCharArray();

            // Place each character onto the stack.
            for (int i = binary.length - 1; i >= 0; i--) {
                stack.add(binary[i]);

                // We need to check for and add the continuation bits as we go.
                if (stack.size() == 7 && i != 0) {
                    stack.add('1');
                    stack.add(' ');
                } else if (stack.size() % 8 == 7 && i != 0) {
                    stack.add('0');
                    stack.add(' ');
                }
            }

            // Top-off the stack, adding characters until the size is divisible by 8.
            // The if-statement takes into account continuation bits.
            if (stack.size() < 8) {
                while (stack.size() % 7 != 0) {
                    stack.add('0');
                }
                stack.add('1');
            } else {
                while (stack.size() % 8 != 0) {
                    stack.add('0');
                }
            }

            // Build the VB-encoded string.
            while (!stack.empty()) {
                variableByte.append(stack.pop());
            }

            encodedList.add(variableByte.toString());
            previousValue = value;
        }

        return encodedList;
    }

    /**
     * Finds the intersection of a whole HashMap's values.
     * @param map The map to find the intersection of.
     */
    private static ArrayList<Integer> mapIntersect(HashMap<String, ArrayList<Integer>> map) {
        HashMap<String, ArrayList<Integer>> smallest = new HashMap<>();
        HashMap<String, ArrayList<Integer>> secondSmallest = new HashMap<>();
        String smallestKey = null, secondSmallestKey = null;
        ArrayList<Integer> smallestValue = null, secondSmallestValue = null;

        // To save on time, we want to compare the two smallest lists first. This for-loop finds the two smallest lists
        // present in the map and stores them in 'smallest' and 'secondSmallest', respectively.
        for (Map.Entry<String, ArrayList<Integer>> entry : map.entrySet()) {
            String key = entry.getKey();
            ArrayList<Integer> value = entry.getValue();

            // If the map size is 1 then it's either already at the end of the intersection algorithm, or doesn't need
            // intersecting anyway.
            if (map.size() == 1) {
                return map.get(key);
            }

            // -> If there's nothing in 'smallest', add the values to it.
            // -> Else, if the list present in this iteration is shorter than the ArrayList currently stored in
            // 'smallest', move the values in 'smallest' to 'secondSmallest', and store the current iteration's list in
            // 'smallest'.
            // -> Else, if 'smallest' isn't empty but 'secondSmallest' is, add the values to 'secondSmallest'.
            // -> Else, if the list present in this iteration is shorter than the list currently stored in
            // 'secondSmallest' (but not smaller than the list currently stored in 'smallest'), store the current
            // iteration's list in 'secondSmallest'.
            if (smallest.size() == 0) {
                smallest.put(key, value);
                smallestKey = key;
                smallestValue = value;
            } else if (value.size() < smallestValue.size()) {
                secondSmallest.clear();
                secondSmallest.put(smallestKey, smallestValue);
                secondSmallestKey = smallestKey;
                secondSmallestValue = smallestValue;
                smallest.clear();
                smallest.put(key, value);
                smallestKey = key;
                smallestValue = value;
            } else if (secondSmallest.size() == 0) {
                secondSmallest.put(key, value);
                secondSmallestKey = key;
                secondSmallestValue = value;
            } else if (value.size() < secondSmallestValue.size()) {
                secondSmallest.clear();
                secondSmallest.put(key, value);
                secondSmallestKey = key;
                secondSmallestValue = value;
            }
        }

        // Now that we have the two smallest lists in the map, we'll get the intersection of them and store that
        // intersection back into the map. We'll also remove those two lists. so we don't process them again.
        map.remove(smallestKey);
        map.remove(secondSmallestKey);
        assert smallestValue != null;
        assert secondSmallestValue != null;
        map.put("intersection", intersect(smallestValue, secondSmallestValue));

        return mapIntersect(map);
    }

    /**
     * Recursively Finds the intersection of two lists.
     * @param list1 The first list to be included in the intersection.
     * @param list2 The second list to be included in the intersection.
     * @return An ArrayList of Integer type containing the results of the intersection.
     */
    private static ArrayList<Integer> intersect(ArrayList<Integer> list1, ArrayList<Integer> list2) {
        ArrayList<Integer> intersection = new ArrayList<>();

        // Make sure both lists are empty before we do any comparisons.
        if (list1.size() != 0 && list2.size() != 0) {
            // If the value at index 0 of both lists match, add to intersection.
            if (Objects.equals(list1.get(0), list2.get(0))) {
                intersection.add(list1.get(0));
                list1.remove(0);
                list2.remove(0);
                intersection.addAll(intersect(list1, list2));
            }

            // If the value at index 0 of list1 is greater than the value at index 0 of list2, advance list2 and feed
            // both lists back into the intercept function.
            else if (list1.get(0) > list2.get(0)) {
                list2.remove(0);
                return intersect(list1, list2);
            }

            // Same as the previous else statement, but in the case the value at index 0 of list1 is less.
            else {
                list1.remove(0);
                return intersect(list1, list2);
            }
        }

        // Base case: one of the lists is empty.
        return intersection;
    }


    /**
     * Takes a string of text, normalizes it, then puts each token into a list.
     * @param line The string to be normalized.
     * @return An ArrayList containing each token in the string.
     */
    private static ArrayList<String> normalizeIntoArray(String line) {
        Matcher wordMatcher;
        Pattern wordPattern = Pattern.compile("[a-zA-Z]+");
        ArrayList<String> tokens = new ArrayList<>();

        // Process the line by extracting words using the wordPattern
        wordMatcher = wordPattern.matcher(line);

        // Extract each word in the line and add it to the tokens ArrayList.
        String word;
        while (wordMatcher.find()) {
            word = line.substring(wordMatcher.start(), wordMatcher.end()).toLowerCase();
            tokens.add(word);
        }

        return tokens;
    }

    /**
     * Modified tokenization taken from the provided Ngrams.java file.
     * Tokenizes all words found in the text files contained in a given directory. Uses these tokens to build the
     * positional index.
     * @param directory The directory containing the text files to be tokenized.
     */
    private static void buildIndex(File directory) {
        BufferedReader br;
        String line;

        // Iterate through each file individually.
        for (File file : Objects.requireNonNull(directory.listFiles())){
            // Since all the files in the given corpus are already numbered in order, we'll extract the index that way
            // instead of using a for-i loop. I know this isn't good.
            int index = Integer.parseInt(file.getName().replaceAll(".txt", ""));

            // Open the input file and feed each line into the normalizeIntoArray function, which will provide us with
            // tokens we can then feed into the positional index.
            try {
                br = new BufferedReader(new FileReader(file));
                while ((line = br.readLine()) != null) {
                    ArrayList<String> tokens = normalizeIntoArray(line);
                    for (String word : tokens) {
                        if (positionalIndex.containsKey(word)) {
                            // If a word in the positional index already contains the document ID, we don't need to
                            // insert it again.
                            if (!positionalIndex.get(word).contains(index)) {
                                positionalIndex.get(word).add(index);
                            }
                        } else {
                            positionalIndex.put(word, new ArrayList<>(List.of(index)));
                        }
                    }
                }
            }
            catch (IOException ex) {
                System.err.println("File " + file.getName() + " not found. Program terminated.\n");
                System.exit(1);
            }
        }
    }
}