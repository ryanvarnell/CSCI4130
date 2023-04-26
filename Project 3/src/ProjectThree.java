// Author: Ryan Varnell, 2023
// CSCI 4130 - Information Retrieval

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProjectThree {

    // List of all the terms, together with their document frequency.
    private static final ArrayList<Term> TERM_LIST = new ArrayList<>();

    // List of terms used in each document with term frequency.
    private static final TreeMap<String, HashMap<Term, Integer>> DOCUMENT_TERM_FREQUENCIES = new TreeMap<>();

    // We'll use the system's temp directory to hold the folders for query and document processing.
    private static final File TEMP_DIRECTORY = new File((System.getProperty("java.io.tmpdir")));

    public static void main(String[] args) {
        // Create temporary folders for the queries and documents.
        // The folder's will be stored as subdirectories in the system's temp folder.
        // For some reason, when we execute mkdirs() to create the folders, we have to assign the result to a boolean
        // variable, or else the folder isn't created. Not sure why, but you'll see it done for most file operations
        // throughout the program.
        File queriesFolder = new File(TEMP_DIRECTORY.getAbsolutePath() +  "/queries");
        boolean temp = queriesFolder.exists() || queriesFolder.mkdirs();
        File corpusFolder = new File(TEMP_DIRECTORY.getAbsolutePath() +  "/corpus");
        temp =  corpusFolder.exists() || corpusFolder.mkdirs();

        // Extract all the documents out of the Cranfield collection into individual files for easier processing.
        // These files will be stored in {temp}/corpus
        String corpusFileName = "cran-1.all.1400";
        File corpusFile = new File(corpusFileName);
        clean(corpusFile, corpusFolder);

        // Build a list containing each document, the terms in the document, and the term frequency of each term.
        getTermFrequencies(corpusFolder);

        // Build a list containing each term found across all documents, and their document frequency.
        getDocumentFrequencies();

        // Calculate the tf-idf score for each term.
        getTFIDF();

        // We'll section out the Cranfield queries and trim them like we did the documents for ease of processing.
        // These files will be stored in {temp}/queries
        String queriesFileName = "cran.qry";
        File queriesFile = new File(queriesFileName);
        clean(queriesFile, queriesFolder);

        // Create a folder to hold the ratings for each query.
        File ratingsFolder = new File("ratings");
        temp = ratingsFolder.mkdirs();

        // Now we'll execute the queries and determine which of the documents are most relevant.
        // Iterate through each query in the queries' folder.
        for (File file : Objects.requireNonNull(queriesFolder.listFiles())) {

            try {
                // A TreeMap will sort our ratings for us as we go. For this to work, we have to hold the rating in the
                // key field.
                TreeMap<Double, ArrayList<String>> ratings = new TreeMap<>();

                // Create a file to hold the ratings for the query.
                File ratingFile = new File(ratingsFolder.getAbsolutePath() + "/" + file.getName());
                if (ratingFile.exists()) {
                    temp = ratingFile.delete();
                }
                temp = ratingFile.createNewFile();

                // Construct the query into a string
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append(" ");
                }

                // Get the results.
                TreeMap<Double, String> results = search(sb.toString());

                // Write all the query's relevant document results to the ratings file.
                FileWriter fw = new FileWriter(ratingFile, true);
                for (Map.Entry<Double, String> entry : results.entrySet()) {
                    fw.write(entry.getValue() + " " + entry.getKey() + "\n");
                }
                fw.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // Get and print the MAP of the system.
        try {
            double MAP = getMAP(ratingsFolder, new File("cranqrel"));
             System.out.print("MAP: " + MAP);
            if (MAP == 0.0)
                System.out.print(", That's either a bad MAP, or the calculator's broken.");
            System.out.println();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Let the user try a search.
        Scanner scanner = new Scanner(System.in);
        System.out.println("Type \"Quit\" to exit the search engine.");
        String query = "";
        while (!query.toLowerCase().trim().equals("quit")) {
            System.out.print("Enter your query: ");
            query = scanner.nextLine();
            if (query.toLowerCase().trim().equals("quit"))
                System.out.println("Goodbye.");
            else {
                TreeMap<Double, String> results = search(query);
                if (results.size() == 1)
                    System.out.println("No relevant documents.");
                else {
                    System.out.println("Relevant documents: ");
                    while (results.size() != 0) {
                        Map.Entry<Double, String> entry = results.pollLastEntry();
                        if (entry.getKey() != 0) {
                            System.out.println(entry.getValue() + "\t tf-idf rating: " + entry.getKey());
                        }
                    }
                }
            }
        }

        queriesFolder.deleteOnExit();
        corpusFolder.deleteOnExit();
    }

    /**
     * Search the documents.
     * @param query The query.
     * @return A list of up to 10 relevant results.
     */
    private static TreeMap<Double, String> search(String query) {
        String line;
        ArrayList<String> tokens = new ArrayList<>(normalizeIntoArray(query));

        // We'll go through each document in DOCUMENT_TERM_FREQUENCIES and compare the terms in the document
        // with the terms in the query. We'll add up the tf-idf values of each matching term to get the overall
        // rating for the document.

        TreeMap<Double, String> ratings = new TreeMap<>();
        for (Map.Entry<String, HashMap<Term, Integer>> entry : DOCUMENT_TERM_FREQUENCIES.entrySet()) {
            double rating = 0;

            // For every word in the query.
            for (String token : tokens) {

                // For every word in the document.
                for (Term term : entry.getValue().keySet()) {

                    // If the word in the query matches the word in the document, multiply the word's tf-idf
                    // value by the number of occurrences in the document, and add that to the overall score.
                    if (Objects.equals(token, term.toString()))
                        rating += term.tfIdf * entry.getValue().get(term);
                }
            }

            ratings.put(rating, entry.getKey());
        }

        // Grab the top 10 results and return those.
        TreeMap<Double, String> results = new TreeMap<>();
        while (results.size() < 10 && ratings.size() != 0) {
            Map.Entry<Double, String> rating = ratings.pollLastEntry();
            results.put(rating.getKey(), rating.getValue());
        }

        return results;
    }

    /**
     * Calculates the Mean Average Precision of the IR system.
     * @param ratingsDirectory The directory containing the relevancy results for the provided queries.
     * @param relevantDocs The document containing the relevancy assessment provided by the collection.
     * @return The Mean Average Precision of the system.
     */
    private static double getMAP(File ratingsDirectory, File relevantDocs) throws IOException {

        // Grab all the known relevant documents for comparison. We'll ignore the relevancy scale.
        // They'll be stored in 'relevant' with the format <Query #, {relevant doc 1, relevant doc 2, ...}>
        HashMap<String, ArrayList<String>> relevant = new HashMap<>();
        BufferedReader br = new BufferedReader(new FileReader(relevantDocs));
        String line;
        while ((line = br.readLine()) != null) {
            String[] values = line.split(" ");
            if (relevant.containsKey(values[0]))
                relevant.get(values[0]).add(values[1]);
            else {
                ArrayList<String> temp = new ArrayList<>();
                temp.add(values[1]);
                relevant.put(values[0], temp);
            }
        }

        // Calculate the average precision for each query. We'll be using the top 10 results for each query as
        // instructed.
        // For each file in the results' folder.
        ArrayList<Double> allAveragePrecisions = new ArrayList<>();

        for (File queryResults : Objects.requireNonNull(ratingsDirectory.listFiles())) {
            String queryNumber = queryResults.getName().substring(0, queryResults.getName().indexOf('.'));
            int totalNumRelevant;
            if (relevant.containsKey(queryNumber)) {
                totalNumRelevant = relevant.get(queryNumber).size();

                // Get the list of ranks from the file.
                TreeMap<Double, String> ranks = new TreeMap<>();
                br = new BufferedReader(new FileReader(queryResults));
                while ((line = br.readLine()) != null) {
                    String[] values = line.split(" ");
                    String docNumber = values[0];
                    double rating = Double.parseDouble(values[1]);
                    ranks.put(rating, docNumber);
                }
                br.close();

                // Calculate the recall and precision at each stage for 10 stages.
                ArrayList<Double> precisionValues = new ArrayList<>();
                ArrayList<Double> precWhenRelevant = new ArrayList<>();
                int numRelevant = 0;
                int index = 0;
                while (precisionValues.size() < 10) {

                    // If we're out of relevant retrieved documents, we need to fill the recall and precision values
                    // until we have 10 total values in each.
                    if (ranks.size() == 0) {
                        while (precisionValues.size() < 10) {
                            precisionValues.add((double) (numRelevant / (precisionValues.size() + 1)));
                        }
                    } else {
                        Map.Entry<Double, String> temp = ranks.pollLastEntry();
                        String retRelDocNum = temp.getValue().substring(0, temp.getValue().indexOf('.'));

                        // We have to check if the known relevant scores contain a specific query because there are more
                        // queries available than those that are listed in the relevant document.
                        if (relevant.get(queryNumber).contains(retRelDocNum)) {
                            numRelevant++;
                            precisionValues.add((double) (numRelevant / (index + 1)));
                            precWhenRelevant.add(precisionValues.get(precisionValues.size() - 1));
                        } else {
                            precisionValues.add((double) (numRelevant / (index + 1)));
                        }
                        index++;
                    }
                }

                // Calculate the average precision for the query.
                Double queryAveragePrecision = 0.0;
                for (Double precision : precWhenRelevant) {
                    queryAveragePrecision += precision;
                }
                queryAveragePrecision /= precisionValues.size();

                allAveragePrecisions.add(queryAveragePrecision);
            }
        }

        // Calculate the mean average preicison for all queries.
        double meanAveragePrecision = 0;
        for (Double precision : allAveragePrecisions) {
            meanAveragePrecision += precision;
        }
        meanAveragePrecision /= allAveragePrecisions.size();

        return meanAveragePrecision;
    }

    /**
     * Divides up the files into individual documents/queries, and trims them up to only leave the abstract/query.
     * @param file The file to clean.
     * @param folder The folder to store the resulting files in.
     */
    private static void clean(File file, File folder) {
        try {
            extractDocuments(file, folder);
        } catch (IOException e) {
            throw new RuntimeException("Problem reading file.", e);
        }

        // Trim the files down to only the abstracts.
        for (File untrimmedFile : Objects.requireNonNull(folder.listFiles())) {
            try {
                trimFile(untrimmedFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Extracts all the documents from the provided Cranfield collection.
     * @param file The file that contains all the documents
     * @param directory The directory to store the files in.
     * @throws IOException If there's a problem trying to read the file.
     */
    private static void extractDocuments(File file, File directory) throws IOException {
        if (directory.listFiles() != null) {
            FileReader fr = new FileReader(file);
            BufferedReader br = new BufferedReader(fr);
            FileWriter fw;
            boolean temp;

            File currentFile;
            currentFile = null;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(".I")) {
                    String articleNumber = line.replaceAll(".I ", "");
                    currentFile = new File(directory.getAbsolutePath() + "/" + articleNumber + ".txt");
                    temp = currentFile.createNewFile();
                }
                assert currentFile != null;

                // Creating a new FileWriter for each individual line probably isn't very efficient, but it works. It'll
                // function for the purpose of this program.
                fw = new FileWriter(currentFile, true);
                fw.write(line + "\n");
                fw.close();
            }
        }
    }

    /**
     * Trims up a file, removing all the fields except the abstract.
     * @param file File to be trimmed
     * @throws IOException If there's an issue reading the file.
     */
    private static void trimFile(File file) throws IOException {
        String content = Files.readString(Path.of(file.getPath()));
        int abstractStartIndex = content.indexOf(".W") + 3;
        FileWriter fw = new FileWriter(file);
        fw.write(content.substring(abstractStartIndex));
        fw.close();
    }

    /**
     * Takes a string of text, normalizes it, breaks it into tokens, then puts each token into a list.
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
     * Calculates the term frequencies for a collection of documents.
     * @param directory Directory containing the documents.
     */
    private static void getTermFrequencies(File directory) {
        BufferedReader br;
        String line;

        // Iterate through each file individually.
        for (File file : Objects.requireNonNull(directory.listFiles())){

            // We'll use this to keep track of the terms in each individual document.
            HashMap<Term, Integer> terms = new HashMap<>();

            // Open the file and tokenize each line.
            try {
                br = new BufferedReader(new FileReader(file));
                while ((line = br.readLine()) != null) {
                    ArrayList<String> tokens = normalizeIntoArray(line);

                    // For each word in the line...
                    for (String word : tokens) {
                        Term newTerm = new Term(word);

                        // If the word is already in the terms list, we'll update the count by one.
                        if (terms.containsKey(newTerm)) {
                            int currentCount = terms.get(newTerm);
                            terms.put(newTerm, currentCount + 1);
                        }

                        // Else, we'll create a new entry and set the value to one.
                        else {
                            terms.put(newTerm, 1);
                        }
                    }
                }
            }
            catch (IOException ex) {
                System.err.println("File " + file.getName() + " not found. Program terminated.\n");
                System.exit(1);
            }

            // Include an entry for the current document in the termFrequencies map.
            DOCUMENT_TERM_FREQUENCIES.put(file.getName(), terms);
        }
    }

    /**
     * Calculates document frequencies.
     */
    private static void getDocumentFrequencies() {

        // Iterate through each document's list of terms.
        for (HashMap<Term, Integer> docTerms : DOCUMENT_TERM_FREQUENCIES.values()) {
            for (Map.Entry<Term, Integer> entry : docTerms.entrySet()) {

                // If the termList is empty, we can just increase the term's document frequency by one and add it.
                if (TERM_LIST.isEmpty()) {
                    entry.getKey().documentFrequency++;
                    TERM_LIST.add(entry.getKey());
                }

                // Else, we'll need to iterate through the termList and make sure we're not adding a duplicate.
                else {
                    boolean inList = false;
                    short index = 0;
                    for (int i = 0; i < TERM_LIST.size(); i++) {

                        // If it sees the word is already in the termList, we'll set inList to true and record the
                        // index relative to the individual document's term list. We'll break out of the loop afterward
                        // to speed things up.
                        if (Objects.equals(entry.getKey(), TERM_LIST.get(i))) {
                            inList = true;
                            index = (short) i;
                            break;
                        }
                    }

                    // If the word was already in the termList, we'll just increase its document frequency.
                    if (inList)
                        TERM_LIST.get(index).documentFrequency++;

                    // Else, we'll increase the document frequency of the term to 1, and add it to the termList.
                    else {
                        entry.getKey().documentFrequency++;
                        TERM_LIST.add(entry.getKey());
                    }

                }
            }
        }
    }

    /**
     * Calculates TF-IDF weighting for each query.
     */
    private static void getTFIDF() {
        int n = DOCUMENT_TERM_FREQUENCIES.keySet().size();
        for (Term term : TERM_LIST) {
            term.tfIdf = Math.log((double) n / term.documentFrequency);
        }
    }
}

/**
 * Class to hold information for an individual term.
 */
class Term {
    String term;
    short documentFrequency = 0;
    double tfIdf = 0;

    Term(String term) {
        this.term = term.toLowerCase();
    }

    // We need to override equals, so we can compare two Terms, e.g. term1 == term2.
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Term term2 = (Term) obj;
        if (term == null) {
            return term2.term == null;
        } else return this.term.equals(term2.term);
    }

    // If we override equals, we need to override hashCode to maintain internal consistency.
    @Override
    public int hashCode() {

        // We'll return the hashcode of the text as an identifier. This works because if we have two Term objects with
        // the same value in their text field, they will be considered the same object when compared.
        return term.hashCode();
    }

    // Just to make sure if you call the term by itself, it will only return the text.
    @Override
    public String toString() {
        return this.term;
    }
}