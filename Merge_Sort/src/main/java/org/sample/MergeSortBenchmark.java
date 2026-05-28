package org.sample;

import org.openjdk.jmh.annotations.*;

import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.ForkJoinTask.invokeAll;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 2)
@State(Scope.Benchmark)
public class MergeSortBenchmark {


    @State(Scope.Benchmark)
    public static class SharedState {
        @Param({"1000", "10000", "100000", "1000000"})
        public int size;

        @Param({"0", "256", "512", "1024", "2048", "4096"})
        public int cutoff;

        public int[] source;

        @Setup(Level.Trial)
        public void setup() {
            source = new int[size];
            SplittableRandom rnd = new SplittableRandom(123456789L);

            for (int i = 0; i < size; i++) {
                source[i] = rnd.nextInt();
            }
        }
    }

    // Nur für den parallelen Benchmark
    @State(Scope.Benchmark)
    public static class ParState {
        @Param({"2", "4", "8", "16"})
        public int parallelism;

        public ForkJoinPool pool;

        @Setup(Level.Trial)
        public void setup() {
            pool = new ForkJoinPool(parallelism);
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            if (pool != null) {
                pool.shutdown();
            }
        }
    }

    @Benchmark
    @Threads(1)
    public int sequentialMergeSort(SharedState s) {
        int[] data = Arrays.copyOf(s.source, s.source.length);
        MergeSortSequential.mergeSortSequential(data);
        return data[0] ^ data[data.length - 1];
    }

    @Benchmark
    @Threads(1)
    public int parallelMergeSort(SharedState s, ParState p) {
        int[] data = Arrays.copyOf(s.source, s.source.length);
        ParallelMergeSort.sort(data, p.pool, s.cutoff);
        return data[0] ^ data[data.length - 1];
    }

    static class MergeSortSequential {

        public static void mergeSortSequential(int[] arr) {
            mergeSortSequential(arr, 0, arr.length - 1);
        }

        private static void mergeSortSequential(int[] arr, int left, int right) {
            if (left >= right) return;

            int mid = left + (right - left) / 2;
            mergeSortSequential(arr, left, mid);
            mergeSortSequential(arr, mid + 1, right);
            merge(arr, left, mid, right);
        }

        private static void merge(int[] arr, int left, int mid, int right) {
            int i = left;
            int j = mid + 1;

            int[] tmp = new int[right - left + 1];
            int k = 0;

            while (i <= mid && j <= right) {
                if (arr[i] <= arr[j]) {
                    tmp[k++] = arr[i++];
                } else {
                    tmp[k++] = arr[j++];
                }
            }

            while (i <= mid) {
                tmp[k++] = arr[i++];
            }

            while (j <= right) {
                tmp[k++] = arr[j++];
            }

            System.arraycopy(tmp, 0, arr, left, tmp.length);
        }
    }

    // Paralleler Merge Sort mit parallelisiertem Merge
    static class ParallelMergeSort {

        public static void sort(int[] arr, ForkJoinPool pool, int cutoff) {
            if (arr == null || arr.length < 2) return;

            // aux enthält zunächst eine Kopie der Daten
            int[] aux = arr.clone();
            pool.invoke(new SortTask(aux, arr, 0, arr.length, cutoff));
        }

        private static class SortTask extends RecursiveAction {
            private final int[] src;
            private final int[] dst;
            private final int lo;
            private final int hi;
            private final int cutoff;

            SortTask(int[] src, int[] dst, int lo, int hi, int cutoff) {
                this.src = src;
                this.dst = dst;
                this.lo = lo;
                this.hi = hi;
                this.cutoff = cutoff;
            }

            @Override
            protected void compute() {
                int size = hi - lo;

                if (size <= 1 || (cutoff > 0 && size <= cutoff)) {
                    System.arraycopy(src, lo, dst, lo, size);
                    MergeSortSequential.mergeSortSequential(dst, lo, hi - 1);
                    return;
                }

                int mid = lo + size / 2;

                // Rollen tauschen:
                // Kinder sortieren aus dst nach src,
                // danach wird aus src nach dst gemerged.
                SortTask left = new SortTask(dst, src, lo, mid, cutoff);
                SortTask right = new SortTask(dst, src, mid, hi, cutoff);

                invokeAll(left, right);

                mergeParallel(src, lo, mid, hi, dst, lo, cutoff);
            }
        }

        // Paralleles Merge gemäß der Idee aus MergeP
        private static void mergeParallel(int[] src,
                                          int left, int mid, int right,
                                          int[] dst, int dstOffset,
                                          int cutoff) {
            int lenA = mid - left;
            int lenB = right - mid;

            if (lenA == 0) {
                System.arraycopy(src, mid, dst, dstOffset, lenB);
                return;
            }
            if (lenB == 0) {
                System.arraycopy(src, left, dst, dstOffset, lenA);
                return;
            }

            // kleinere Seite zuerst halten
            if (lenA < lenB) {
                mergeParallel(src, mid, right, left, dst, dstOffset, cutoff);
                return;
            }

            if (lenA + lenB <= cutoff || lenA == 1 || lenB == 1) {
                sequentialMerge(src, left, mid, mid, right, dst, dstOffset);
                return;
            }

            int aMid = left + lenA / 2;
            int pivot = src[aMid];

            // lowerBound in B: erstes Element >= pivot
            int h = lowerBound(src, mid, right, pivot);

            int leftCount = (aMid - left) + (h - mid);
            dst[dstOffset + leftCount] = pivot;

            MergeTask leftTask = new MergeTask(src, left, aMid, mid, h, dst, dstOffset, cutoff);
            MergeTask rightTask = new MergeTask(src, aMid + 1, mid, h, right, dst, dstOffset + leftCount + 1, cutoff);

            invokeAll(leftTask, rightTask);
        }

        private static class MergeTask extends RecursiveAction {
            private final int[] src;
            private final int aLo;
            private final int aHi;
            private final int bLo;
            private final int bHi;
            private final int[] dst;
            private final int dstOffset;
            private final int cutoff;

            MergeTask(int[] src, int aLo, int aHi, int bLo, int bHi, int[] dst, int dstOffset, int cutoff) {
                this.src = src;
                this.aLo = aLo;
                this.aHi = aHi;
                this.bLo = bLo;
                this.bHi = bHi;
                this.dst = dst;
                this.dstOffset = dstOffset;
                this.cutoff = cutoff;
            }

            @Override
            protected void compute() {
                int lenA = aHi - aLo;
                int lenB = bHi - bLo;

                if (lenA == 0) {
                    System.arraycopy(src, bLo, dst, dstOffset, lenB);
                    return;
                }
                if (lenB == 0) {
                    System.arraycopy(src, aLo, dst, dstOffset, lenA);
                    return;
                }

                if (lenA < lenB) {
                    // Rollen tauschen
                    new MergeTask(src, bLo, bHi, aLo, aHi, dst, dstOffset, cutoff).compute();
                    return;
                }

                if (lenA + lenB <= cutoff || lenA == 1 || lenB == 1) {
                    sequentialMerge(src, aLo, aHi, bLo, bHi, dst, dstOffset);
                    return;
                }

                int aMid = aLo + lenA / 2;
                int pivot = src[aMid];

                int h = lowerBound(src, bLo, bHi, pivot);
                int leftCount = (aMid - aLo) + (h - bLo);

                dst[dstOffset + leftCount] = pivot;

                MergeTask leftTask = new MergeTask(src, aLo, aMid, bLo, h, dst, dstOffset, cutoff);
                MergeTask rightTask = new MergeTask(src, aMid + 1, aHi, h, bHi, dst, dstOffset + leftCount + 1, cutoff);

                invokeAll(leftTask, rightTask);
            }
        }

        private static void sequentialMerge(int[] src,
                                            int aLo, int aHi,
                                            int bLo, int bHi,
                                            int[] dst,
                                            int dstOffset) {
            int i = aLo;
            int j = bLo;
            int k = dstOffset;

            while (i < aHi && j < bHi) {
                if (src[i] <= src[j]) {
                    dst[k++] = src[i++];
                } else {
                    dst[k++] = src[j++];
                }
            }

            while (i < aHi) {
                dst[k++] = src[i++];
            }

            while (j < bHi) {
                dst[k++] = src[j++];
            }
        }

        private static int lowerBound(int[] arr, int from, int to, int value) {
            int lo = from;
            int hi = to;

            while (lo < hi) {
                int mid = lo + ((hi - lo) >>> 1);
                if (arr[mid] < value) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            return lo;
        }
    }

    public static void main(String[] args) throws Exception {
        String[] jmhArgs = {
                "-rf", "csv",
                "-rff", "results.csv"
        };
        org.openjdk.jmh.Main.main(jmhArgs);
    }
}