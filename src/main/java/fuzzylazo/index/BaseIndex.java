package fuzzylazo.index;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fuzzylazo.sketch.NGramSignature;
import lazo.index.LazoIndex;
import lazo.index.LazoIndex.LazoCandidate;
import lazo.sketch.LazoSketch;

public class BaseIndex {

    private int k;
    private int n;
    private final int ORIGINAL_STRING = 0;
    private final float D = 0.05f;
    private Map<Integer, LazoIndex> indexes;

    public BaseIndex(int k, int n) {
	this.k = k;
	this.n = n;
	indexes = new HashMap<>();

	for (int ngramSize = 2; ngramSize < n + 1; ngramSize++) {
	    indexes.put(ngramSize, new LazoIndex(k, D));
	}
	indexes.put(ORIGINAL_STRING, new LazoIndex(k, D));

    }

    public boolean insert(Object key, NGramSignature signature) {
	assert (signature.getN() == this.n);

	for (int ngramSize = 2; ngramSize < n + 1; ngramSize++) {
	    LazoIndex index = indexes.get(ngramSize);
	    LazoSketch sketch = signature.getSketch(ngramSize);
	    index.insert(key, sketch);
	}
	indexes.get(ORIGINAL_STRING).insert(key, signature.getSketch(ORIGINAL_STRING));

	return true;
    }

    public Set<LazoCandidate> queryNgram(NGramSignature signature, int n, float js, float jc) {
	LazoSketch sketch = signature.getSketch(n);
	LazoIndex index = this.indexes.get(n);
	Set<LazoCandidate> results = index.query(sketch, js, jc);
	return results;
    }

    public int[][] calculateBeta(List<Object> keys, List<NGramSignature> signatures, int ngramSize) {
	assert (keys.size() == signatures.size());

	// assign an index in the matrix to each key
	Map<Object, Integer> mapKeyToIndex = new HashMap<>();
	for (int i = 0; i < keys.size(); i++) {
	    mapKeyToIndex.put(keys.get(i), i);
	}

	// matrix where to store the ix
	int[][] intersectionMatrix = new int[signatures.size()][signatures.size()];

	// fill in the matrix
	for (int i = 0; i < signatures.size(); i++) {
	    NGramSignature signature = signatures.get(i);
	    Object key = keys.get(i);
	    int index = mapKeyToIndex.get(key);
	    Set<LazoCandidate> results = this.queryNgram(signature, ngramSize, 0, 0);
	    long cardinality = signature.getCardinality(ngramSize);
	    for (LazoCandidate result : results) {
		Object resultKey = result.key;
		float jc = result.jcx;
		int ix = (int) (jc * cardinality); // jc = ix/c so ix = jc*c
		int resultIndex = mapKeyToIndex.get(resultKey);
		intersectionMatrix[index][resultIndex] = ix;
	    }
	}
	return intersectionMatrix;
    }

}
