// Author: Ryan Varnell, 2023
// CSCI 4130 - Information Retrieval

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.IOUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Analyzer analyzer = new StandardAnalyzer();

        try {
            // Create a temporary directory to hold the index for Lucene.
            Path indexPath = Files.createTempDirectory("tempIndex");
            Directory directory = FSDirectory.open(indexPath);
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            IndexWriter iwriter = new IndexWriter(directory, config);

            // Iterate through each file in the corpus, adding the files to Lucene's index.
            File corpus = new File("corpus");
            for (File file : Objects.requireNonNull(corpus.listFiles())) {
                Document doc = new Document();
                BufferedReader br = new BufferedReader(new FileReader(file));
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    body.append(line).append("\n");
                }

                doc.add(new Field("body", body, TextField.TYPE_STORED));
                doc.add(new Field("title", file.getName(), TextField.TYPE_STORED));
                iwriter.addDocument(doc);
            }

            iwriter.close();

            // Search the directory.
            DirectoryReader ireader = DirectoryReader.open(directory);
            IndexSearcher isearcher = new IndexSearcher(ireader);
            QueryParser parser = new QueryParser("body", analyzer);

            // Get user query.
            Scanner scanner = new Scanner(System.in);
            System.out.print("Type your query: ");
            Query query = parser.parse(scanner.nextLine());

            // Search the index for the top 5 relevant documents.
            ScoreDoc[] hits = isearcher.search(query, 5).scoreDocs;
            assert(1 == hits.length);

            // Print out the results to the user.
            StoredFields storedFields = isearcher.storedFields();
            for (ScoreDoc hit : hits) {
                Document hitDoc = storedFields.document(hit.doc);
                System.out.println(hitDoc.get("title"));
            }

            ireader.close();
            directory.close();
            IOUtils.rm(indexPath);
        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }
    }
}