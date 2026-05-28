import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class MergeSort {

    private static final ForkJoinPool POOL = new ForkJoinPool();

    //Sequentieller Merge Sort
    public static void mergeSortSequential(int[] arr) {
        mergeSortSequential(arr, 0, arr.length - 1);
    }

    private static void mergeSortSequential(int[] arr, int left, int right) {
        //Abbruchbedingung
        if (left >= right) return;
        //verhindert integer overflow
        int mid = left + (right - left) / 2;
        //rekursiver Aufruf
        mergeSortSequential(arr, left, mid);
        mergeSortSequential(arr, mid + 1, right);
        //verschmelzen der "Teilarrays"
        merge(arr, left, mid, right);

    }

    private static void merge(int[] arr, int left, int mid, int right) {
        int i = left;
        int j = mid + 1;

        //Temporaeres Array zum Zwischenspeichern der sortierten Elemente
        int[] tmp = new int[right - left + 1];
        int k = 0;

        while (i <= mid && j <= right) {
            //Fuegt aktuelles Element des linken Teilarrays in tmp ein
            if (arr[i] <= arr[j]) {
                tmp[k] = arr[i];
                i++;
            } else { //Fuegt aktuelles Element aus rechten Teilarray in tmp ein
                tmp[k] = arr[j];
                j++;
            }
            k++;
        }
        //Fuegt ggf. den Rest aus dem ersten Teilarray in tmp
        while (i <= mid) {
            tmp[k] = arr[i];
            i++;
            k++;
        }
        //Fuegt ggf. den Rest aus dem zweiten Teilarray in tmp
        while (j <= right) {
            tmp[k] = arr[j];
            j++;
            k++;
        }
        //Kopiert das sortierte Teilarray zurück in das Originalarray
        System.arraycopy(tmp, 0, arr, left, tmp.length);
    }


    public static void mergeSortParallel(int[] arr) {
        POOL.invoke(new MergeSortTask(arr, 0, arr.length - 1));
    }

    static class MergeSortTask extends RecursiveAction {
        private final int[] arr;
        private final int left;
        private final int right;

        private static final int THRESHOLD = 1000;

        public MergeSortTask(int[] arr, int left, int right) {
            this.arr = arr;
            this.left = left;
            this.right = right;
        }

        @Override
        protected void compute() {

            if (left >= right) return;

            // kleine Bereiche sequentiell
            if (right - left <= THRESHOLD) {
                mergeSortSequential(arr, left, right);
                return;
            }

            int mid = left + (right - left) / 2;

            MergeSortTask leftTask = new MergeSortTask(arr, left, mid);
            MergeSortTask rightTask = new MergeSortTask(arr, mid + 1, right);

            invokeAll(leftTask, rightTask);

            merge(arr, left, mid, right);
        }
    }

    public static void main(String[] args) {

        int size = 1_000_000;
        int[] original = new int[size];

        // zufällige Daten
        for (int i = 0; i < size; i++) {
            original[i] = (int) (Math.random() * 100_000);
        }

        // Sequentiell TEST
        long sumSeq = 0;
        for (int i = 0; i < 50; i++) {
            int[] seq = Arrays.copyOf(original, original.length);

            long startSeq = System.nanoTime();
            mergeSortSequential(seq);
            long endSeq = System.nanoTime();
            sumSeq += endSeq - startSeq;
        }
        long avgSeq = sumSeq / 50;


        // Parallel TEST
        long sumPar = 0;
        for (int i = 0; i < 50; i++) {
            int[] par = Arrays.copyOf(original, original.length);

            long startPar = System.nanoTime();
            mergeSortParallel(par);
            long endPar = System.nanoTime();
            sumPar += endPar - startPar;
        }
        long avgPar = sumPar / 50;


        // VALIDIERUNG
        int[] seqTest = Arrays.copyOf(original, original.length);
        int[] parTest = Arrays.copyOf(original, original.length);

        mergeSortSequential(seqTest);
        mergeSortParallel(parTest);

        int[] sortedArray = Arrays.copyOf(original, original.length);
        Arrays.sort(sortedArray);
        System.out.println("Sequentiell sorted? " + Arrays.equals(seqTest, sortedArray));
        System.out.println("Parallel sorted? " + Arrays.equals(parTest, sortedArray));

        // PERFORMANCE
        System.out.println("Sequential Durschnittliche Laufzeit: " + avgSeq + " ns");
        System.out.println("Parallel Durschnittliche Laufzeit:   " + avgPar + " ns");
        System.out.println("Speedup Parallel im Vergleic zu Sequentiell: " + avgSeq / (double) avgPar);
    }
}