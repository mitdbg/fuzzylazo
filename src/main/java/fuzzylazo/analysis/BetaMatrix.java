package fuzzylazo.analysis;

public class BetaMatrix {

    public class MatrixStats {
	public float[] aggregatedIx;
	public int[] totalZeros;
    }

    public static MatrixStats computeStats(float[][] intersectionMatrix) {

	float[] aggregatedIx = new float[intersectionMatrix.length];
	int[] totalZeros = new int[intersectionMatrix.length];

	for (int i = 0; i < intersectionMatrix.length; i++) {
	    float aggrIx = 0;
	    int numZeros = 0;
	    for (int j = 0; j < intersectionMatrix.length; j++) {
		float value = intersectionMatrix[i][j];
		aggrIx += value;
		if (value == 0) {
		    numZeros += 1;
		}
	    }
	    aggregatedIx[i] = aggrIx;
	    totalZeros[i] = numZeros;
	}

	MatrixStats stats = new BetaMatrix().new MatrixStats();
	stats.aggregatedIx = aggregatedIx;
	stats.totalZeros = totalZeros;
	return stats;

    }

}
