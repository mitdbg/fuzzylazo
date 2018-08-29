package fuzzylazo.analysis;

public class BetaMatrix {

    class MatrixStats {
	public int[] aggregatedIx;
	public int[] totalZeros;
    }

    public static MatrixStats computeStats(int[][] intersectionMatrix) {

	int[] aggregatedIx = new int[intersectionMatrix.length];
	int[] totalZeros = new int[intersectionMatrix.length];

	for (int i = 0; i < intersectionMatrix.length; i++) {
	    int aggrIx = 0;
	    int numZeros = 0;
	    for (int j = 0; j < intersectionMatrix.length; j++) {
		int value = intersectionMatrix[i][j];
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
