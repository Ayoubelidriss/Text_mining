import safar.util.tokenization.impl.SAFARTokenizer;
import safar.util.tokenization.interfaces.ITokenizer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class Main {

    public static void main(String[] args) throws IOException {

        // === 1. Charger les stop words arabes (UTF-8) ===
        String stopWordsFile = "stop_words_arabic.txt";
        Set<String> stopWords = Files.lines(Paths.get(stopWordsFile), StandardCharsets.UTF_8)
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(Main::normalizeArabic)
                .collect(Collectors.toSet());

        // === 2. Définir les documents ===
        String doc1Path = "text1.txt";
        String doc2Path = "text2.txt";
        String doc3Path = "text3.txt";

        Map<String, String> documents = Map.of(
                "d1", Files.readString(Paths.get(doc1Path), StandardCharsets.UTF_8),
                "d2", Files.readString(Paths.get(doc2Path), StandardCharsets.UTF_8),
                "d3", Files.readString(Paths.get(doc3Path), StandardCharsets.UTF_8)
        );

        // === 3. Initialiser SAFARTokenizer ===
        ITokenizer safarTokenizer;
        try {
            safarTokenizer = new SAFARTokenizer();
        } catch (Throwable t) {
            safarTokenizer = null;
        }

        // === 4. Créer la map d'occurrence ===
        Map<String, Map<String, Long>> occurrenceMap = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : documents.entrySet()) {
            String docName = entry.getKey();
            String text = entry.getValue();

            List<String> tokens = tokenizeText(text, safarTokenizer);

            Map<String, Long> freqMap = tokens.stream()
                    .map(Main::normalizeArabic)
                    .filter(t -> !t.isBlank())
                    .filter(t -> !stopWords.contains(t))
                    .collect(Collectors.groupingBy(t -> t, Collectors.counting()));

            occurrenceMap.put(docName, freqMap);
        }

        // === 5. Calculer TF-IDF ===
        Map<String, Map<String, Double>> tfIdfMap = computeTfIdf(occurrenceMap);

        // === 6. Sauvegarder les résultats dans un fichier texte ===
        writeResultsToFile(occurrenceMap, tfIdfMap, "resultats_tfidf.txt");
    }

    // --------------------- Écriture dans fichier ---------------------

    public static void writeResultsToFile(Map<String, Map<String, Long>> occurrenceMap,
                                          Map<String, Map<String, Double>> tfIdfMap,
                                          String fileName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, StandardCharsets.UTF_8))) {
            writer.write("===== MAP D'OCCURRENCE =====\n\n");
            for (String doc : occurrenceMap.keySet()) {
                writer.write("Document: " + doc + "\n");
                for (Map.Entry<String, Long> e : occurrenceMap.get(doc).entrySet()) {
                    writer.write("  " + e.getKey() + " : " + e.getValue() + "\n");
                }
                writer.newLine();
            }

            writer.write("===== MAP TF-IDF =====\n\n");
            for (String doc : tfIdfMap.keySet()) {
                writer.write("Document: " + doc + "\n");
                for (Map.Entry<String, Double> e : tfIdfMap.get(doc).entrySet()) {
                    writer.write("  " + e.getKey() + " : " + String.format("%.6f", e.getValue()) + "\n");
                }
                writer.newLine();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --------------------- Fonctions utilitaires ---------------------

    private static String normalizeArabic(String text) {
        if (text == null) return "";
        String newText = text;

        newText = newText.replaceAll("[\u064B-\u065F\u0610-\u061A\u0670]", "");
        newText = newText.replaceAll("[.,;!?؟،؛()\"«»\\[\\]{}]", "");
        newText = newText.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا');
        newText = newText.replace('ة', 'ه');
        newText = newText.replace('ى', 'ي');
        newText = newText.replaceAll("[0-9]", "");

        if (newText.startsWith("وال")) newText = newText.substring(3);
        else if (newText.startsWith("فال")) newText = newText.substring(3);
        else if (newText.startsWith("بال")) newText = newText.substring(3);
        else if (newText.startsWith("كال")) newText = newText.substring(3);

        if (newText.startsWith("ال")) newText = newText.substring(2);

        if (newText.startsWith("و")) newText = newText.substring(1);
        else if (newText.startsWith("ف")) newText = newText.substring(1);
        else if (newText.startsWith("ب")) newText = newText.substring(1);
        else if (newText.startsWith("ل")) newText = newText.substring(1);

        if (newText.length() > 4) {
            if (newText.endsWith("ون")) newText = newText.substring(0, newText.length() - 2);
            else if (newText.endsWith("ين")) newText = newText.substring(0, newText.length() - 2);
            else if (newText.endsWith("ات")) newText = newText.substring(0, newText.length() - 2);
            else if (newText.endsWith("ان")) newText = newText.substring(0, newText.length() - 2);
        }

        if (newText.length() > 4) {
            if (newText.endsWith("هم")) newText = newText.substring(0, newText.length() - 2);
            else if (newText.endsWith("ها")) newText = newText.substring(0, newText.length() - 2);
            else if (newText.endsWith("كم")) newText = newText.substring(0, newText.length() - 2);
            else if (newText.endsWith("ه")) newText = newText.substring(0, newText.length() - 1);
            else if (newText.endsWith("ي")) newText = newText.substring(0, newText.length() - 1);
        }

        return newText.trim();
    }

    private static List<String> tokenizeText(String text, ITokenizer safarTokenizer) {
        if (text == null || text.isBlank()) return Collections.emptyList();

        try {
            if (safarTokenizer != null) {
                String[] arr = safarTokenizer.tokenize(text);
                if (arr != null && arr.length > 0) return Arrays.asList(arr);
            }
        } catch (Throwable ignored) {}

        return Arrays.asList(text.toLowerCase().split("[\\s,.;:!?()\"«»\\[\\]{}]+"));
    }

    public static Map<String, Double> computeTF(Map<String, Long> freqMap) {
        double totalTerms = freqMap.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Double> tfMap = new LinkedHashMap<>();
        if (totalTerms == 0) return tfMap;
        for (Map.Entry<String, Long> entry : freqMap.entrySet()) {
            tfMap.put(entry.getKey(), entry.getValue() / totalTerms);
        }
        return tfMap;
    }

    public static Map<String, Double> computeIDF(Map<String, Map<String, Long>> allDocs) {
        Map<String, Double> idfMap = new LinkedHashMap<>();
        int totalDocs = allDocs.size();

        Set<String> allTerms = allDocs.values().stream()
                .flatMap(m -> m.keySet().stream())
                .collect(Collectors.toSet());

        for (String term : allTerms) {
            long docsWithTerm = allDocs.values().stream()
                    .filter(m -> m.containsKey(term))
                    .count();
            double idf = Math.log((double) totalDocs / (1 + docsWithTerm));
            idfMap.put(term, idf);
        }

        return idfMap;
    }

    public static Map<String, Map<String, Double>> computeTfIdf(Map<String, Map<String, Long>> occurrenceMap) {
        Map<String, Double> idfMap = computeIDF(occurrenceMap);
        Map<String, Map<String, Double>> tfIdfMap = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, Long>> entry : occurrenceMap.entrySet()) {
            String docName = entry.getKey();
            Map<String, Double> tfMap = computeTF(entry.getValue());
            Map<String, Double> tfidf = new LinkedHashMap<>();

            for (String term : tfMap.keySet()) {
                double tfidfValue = tfMap.get(term) * idfMap.getOrDefault(term, 0.0);
                tfidf.put(term, tfidfValue);
            }
            tfIdfMap.put(docName, tfidf);
        }

        return tfIdfMap;
    }
}
