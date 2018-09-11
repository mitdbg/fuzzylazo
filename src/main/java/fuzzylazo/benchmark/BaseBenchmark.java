package fuzzylazo.benchmark;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import fuzzylazo.analysis.BetaMatrix;
import fuzzylazo.analysis.BetaMatrix.MatrixStats;
import fuzzylazo.index.BaseIndex;
import fuzzylazo.sketch.NGramSignature;
import lazo.benchmark.Pair;
import lazo.benchmark.Utils;

public class BaseBenchmark {

    // metrics
    private long io_time;
    private long index_time;
    private long query_time;
    private long ech_time;
    private long post_time;
    private int corrections;
    private float magChange;
    private int js_impact_corrections;
    private int jcx_impact_corrections;

    public Map<Integer, String> matrixIndexToName = new HashMap<>();

    private CsvParser parser;
    private Map<Integer, String> hashIdToName;

    private Map<String, File> nameToFile = new HashMap<>();

    public BaseBenchmark() {
	// csv parser
	CsvParserSettings settings = new CsvParserSettings();
	settings.getFormat().setLineSeparator("\n");
	this.parser = new CsvParser(settings);

	// id, names, etc
	this.hashIdToName = new HashMap<>();

    }

    private File[] enumerateFiles(String path) {
	File folder = new File(path);
	File[] files = folder.listFiles();
	return files;
    }

    private int hashName(String fileName, String columnName) {
	return (fileName + columnName).hashCode();
    }

    public Reader getReader(File file) throws FileNotFoundException {
	FileReader fr = new FileReader(file);
	BufferedReader br = new BufferedReader(fr);
	return br;
    }

    int failed = 0;
    List<String> failedFiles = new ArrayList<>();

    public Map<Integer, Set<String>> obtainColumns(File file) {
	long s = System.currentTimeMillis();
	Map<Integer, Set<String>> tableSets = new HashMap<>();
	Map<Integer, Integer> indexToHashId = new HashMap<>();

	List<String[]> allRows = null;
	try {
	    allRows = parser.parseAll(getReader(file));
	} catch (Exception ex) {
	    String absPath = file.getAbsolutePath();
	    failedFiles.add(absPath);
	    this.failed += 1;
	    ex.printStackTrace();
	    return null;
	}
	try {
	    String[] header = allRows.get(0);
	    int idx = 0;
	    for (String columnName : header) {
		int id = hashName(file.getName(), columnName);
		tableSets.put(id, new HashSet<>());
		indexToHashId.put(idx, id);
		this.hashIdToName.put(id, file.getName() + "->" + columnName);
		idx++;
	    }
	    for (int i = 1; i < allRows.size(); i++) {
		String[] row = allRows.get(i);
		for (int j = 0; j < row.length; j++) {
		    // add value to correct column
		    tableSets.get(indexToHashId.get(j)).add(row[j]);
		}
	    }
	} catch (Exception npe) {
	    String absPath = file.getAbsolutePath();
	    failedFiles.add(absPath);
	    this.failed += 1;
	    npe.printStackTrace();
	    return null;
	}
	long e = System.currentTimeMillis();
	this.io_time += (e - s);
	return tableSets;
    }

    private Map<Integer, Set<String>> getTable(String table, Map<String, Map<Integer, Set<String>>> cache) {
	if (cache.containsKey(table)) {
	    return cache.get(table);
	}
	File f = this.nameToFile.get(table);
	Map<Integer, Set<String>> cols = this.obtainColumns(f);
	cache.put(table, cols);
	return cols;
    }

    public Set<Pair<Integer, Integer>> postProcessing(Set<Pair<Integer, Integer>> candidates, float threshold) {
	Set<Pair<Integer, Integer>> verifiedPairs = new HashSet<>();
	Map<String, Map<Integer, Set<String>>> cache = new HashMap<>();
	for (Pair<Integer, Integer> candidate : candidates) {
	    String fullName1 = this.hashIdToName.get(candidate.x);
	    String tokens1[] = fullName1.split("->");
	    String tableName1 = tokens1[0];
	    Map<Integer, Set<String>> tableX = this.getTable(tableName1, cache);
	    String fullName2 = this.hashIdToName.get(candidate.y);
	    String tokens2[] = fullName2.split("->");
	    String tableName2 = tokens2[0];
	    Map<Integer, Set<String>> tableY = this.getTable(tableName2, cache);
	    float realJS = Utils.computeJS(tableX.get(candidate.x), tableY.get(candidate.y));
	    if (realJS >= threshold) {
		verifiedPairs.add(candidate);
	    }
	}
	return verifiedPairs;
    }

    private Set<String> readColumnFile(File f) {
	Set<String> strings = new HashSet<>();
	BufferedReader br;
	try {
	    br = new BufferedReader(new FileReader(f));
	    String line = null;
	    while ((line = br.readLine()) != null) {
		strings.add(line);
	    }
	} catch (FileNotFoundException e) {
	    e.printStackTrace();
	} catch (IOException e) {
	    e.printStackTrace();
	}
	return strings;
    }

    public float[][] computeBetaMatrix(File[] files, float threshold, int k, int n) {
	BaseIndex index = new BaseIndex(k, n);
	// Create sketches and index
	Map<Integer, NGramSignature> idToSketch = new HashMap<>();
	for (int i = 0; i < files.length; i++) {
	    System.out.println("Processing: " + i + "/" + files.length);
	    System.out.println(files[i].getAbsolutePath());

	    // Read file
	    Map<Integer, Set<String>> table = obtainColumns(files[i]);
	    long s = System.currentTimeMillis();
	    for (Entry<Integer, Set<String>> e : table.entrySet()) {
		int id = e.getKey();
		NGramSignature ls = new NGramSignature(n, k);
		Set<String> values = e.getValue();
		boolean valid = false;
		for (String value : values) {
		    if (value != null) {
			ls.update(value);
			valid = true;
		    }
		}
		if (valid) {
		    index.insert(id, ls);
		    idToSketch.put(id, ls);
		}
	    }

	    long e = System.currentTimeMillis();
	    this.index_time += (e - s);
	}

	// // Query to retrieve pairs
	long s = System.currentTimeMillis();
	List<Object> keys = new ArrayList<>();
	List<NGramSignature> signatures = new ArrayList<>();
	int colIndex = 0;
	for (Entry<Integer, NGramSignature> entry : idToSketch.entrySet()) {
	    keys.add(entry.getKey());
	    signatures.add(entry.getValue());
	    this.matrixIndexToName.put(colIndex, this.hashIdToName.get(entry.getKey()));
	    colIndex++;
	}
	float[][] ixMatrix = index.calculateBeta(keys, signatures, n);

	long e = System.currentTimeMillis();
	this.query_time = (e - s);
	return ixMatrix;
    }

    public static void main(String args[]) {

	BaseBenchmark bb = new BaseBenchmark();

	if (args.length < 5) {
	    System.out.println("Usage: <inputPath> <outputPath> <similarityThreshold> <minhash-permutations> <n>");
	}

	String inputPath = args[0];
	String outputPath = args[1];
	float similarityThreshold = Float.parseFloat(args[2]);
	int k = Integer.parseInt(args[3]);
	int n = Integer.parseInt(args[4]);

	File[] filesInPath = bb.enumerateFiles(inputPath);
	for (File f : filesInPath) {
	    bb.nameToFile.put(f.getName(), f);
	}

	System.out.println("Found " + filesInPath.length + " files to process");
	long start = System.currentTimeMillis();
	float[][] ixMatrix = bb.computeBetaMatrix(filesInPath, similarityThreshold, k, n);
	long end = System.currentTimeMillis();

	MatrixStats ms = BetaMatrix.computeStats(ixMatrix);

	System.out.println("Total time: " + (end - start));
	System.out.println("Total failed tasks: " + bb.failed);
	System.out.println("io time: " + (bb.io_time));
	System.out.println("index time: " + (bb.index_time));
	System.out.println("query time: " + (bb.query_time));
	System.out.println("ech time (part of query time): " + (bb.ech_time));
	System.out.println("post time: " + bb.post_time);

	File f1 = new File(outputPath);
	BufferedWriter bw1 = null;
	try {
	    bw1 = new BufferedWriter(new FileWriter(f1));
	    for (int i = 0; i < ixMatrix.length; i++) {
		StringBuffer sb = new StringBuffer();
		for (int j = 0; j < ixMatrix[i].length; j++) {
		    sb.append(ixMatrix[i][j] + ",");
		}
		String line = sb.toString();
		bw1.write(line + '\n');
	    }
	    bw1.flush();
	    bw1.close();
	} catch (IOException eio) {
	    eio.printStackTrace();
	}

	System.out.println("Matrix output to: " + outputPath);

	File f2 = new File(outputPath + "_nameMap");
	bw1 = null;
	try {
	    bw1 = new BufferedWriter(new FileWriter(f2));
	    for (Entry<Integer, String> e : bb.matrixIndexToName.entrySet()) {
		int id = e.getKey();
		String name = e.getValue();
		String line = id + "," + name;
		bw1.write(line + '\n');
	    }
	    bw1.flush();
	    bw1.close();
	} catch (IOException eio) {
	    eio.printStackTrace();
	}

	System.out.println("id-map output to: " + outputPath);
    }

}
