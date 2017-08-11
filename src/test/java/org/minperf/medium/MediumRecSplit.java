package org.minperf.medium;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.minperf.Settings;
import org.minperf.universal.UniversalHash;

/**
 * A very simple implementation of the RecSplit algorithm. Compression is not
 * used.
 */
public class MediumRecSplit<T> {

    /**
     * Calculate the universal hash of the string modulo the given value.
     *
     * @param x the key
     * @param index the universal hash index
     * @param mod the modulo
     * @return a value between 0 (including) and the modulo (excluding)
     */
    static <T> int universalHash(UniversalHash<T> hash, T x, int index, int mod) {
        return (int) Math.abs(hash.universalHash(x, index) % mod);
    }

    /**
     * The generator class (not really a class, just a collection of static
     * methods).
     */
    public static class Generator<T> {

        private final UniversalHash<T> hash;
        private final Settings settings;

        public Generator(UniversalHash<T> hash, Settings settings) {
            this.hash = hash;
            this.settings = settings;
        }

        /**
         * Generate the MPHF description for the given set.
         *
         * @param set the set
         * @return the description, in the form of an integer array
         *         (uncompressed)
         */
        public int[] generate(Set<T> set) {
            int averageBucketSize = settings.getAverageBucketSize();
            int bucketCount = (set.size() + averageBucketSize - 1) / averageBucketSize;
            List<Set<T>> buckets = partition(set, bucketCount);
            List<List<Integer>> bucketData = new ArrayList<>();
            int bucketsSize = 0;
            for (int i = 0; i < bucketCount; i++) {
                ArrayList<Integer> bucket = new ArrayList<>();
                processRecursively(buckets.get(i), bucket);
                bucketData.add(bucket);
                bucketsSize += bucket.size();
            }
            int[] description = new int[1 + 2 * bucketCount + bucketsSize];
            description[0] = set.size();
            int pos = 1 + 2 * bucketCount, size = 0, d = 0;
            for (int i = 0; i < bucketCount; i++) {
                List<Integer> b = bucketData.get(i);
                d += b.size();
                description[1 + i] = d;
                size += buckets.get(i).size();
                description[1 + bucketCount + i] = size;
                for (int j = 0; j < b.size(); j++) {
                    description[pos++] = b.get(j);
                }
            }
            return description;
        }

        /**
         * Partition the set.
         *
         * @param set the input set
         * @param bucketCount the number of buckets
         * @return the list of buckets, each one with a set
         */
        List<Set<T>> partition(Set<T> set, int bucketCount) {
            List<Set<T>> buckets = new ArrayList<>();
            for (int i = 0; i < bucketCount; i++) {
                buckets.add(new LinkedHashSet<T>());
            }
            for (T x : set) {
                int bucketId = universalHash(hash, x, 0, bucketCount);
                buckets.get(bucketId).add(x);
            }
            return buckets;
        }

        /**
         * Process a set recursively.
         *
         * @param set the set
         * @param description the description (uncompressed)
         */
        void processRecursively(Set<T> set, List<Integer> description) {
            if (set.size() <= 1) {
                return;
            }
            int leafSize = settings.getLeafSize();
            if (set.size() <= leafSize) {
                for (int x = 0;; x++) {
                    if (canMapDirectly(set, x)) {
                        description.add(x);
                        return;
                    }
                }
            }
            for (int x = 0;; x++) {
                List<Set<T>> list = trySplit(set, x);
                if (list != null) {
                    description.add(x);
                    for (Set<T> s : list) {
                        processRecursively(s, description);
                    }
                    return;
                }
            }
        }

        /**
         * Whether the given set can be mapped directly at this index.
         *
         * @param set the set
         * @param index the universal hash index
         * @return true if yes
         */
        boolean canMapDirectly(Set<T> set, int index) {
            Set<Integer> used = new HashSet<Integer>();
            for (T s : set) {
                used.add(universalHash(hash, s, index, set.size()));
            }
            return used.size() == set.size();
        }

        /**
         * Try to split the set into two subsets of equal size (the size of the
         * first subset must be size / 2).
         *
         * @param set the set
         * @param index the universal hash index
         * @return null if it didn't work out, or the list splitting worked
         */
        List<Set<T>> trySplit(Set<T> set, int index) {
            int setSize = set.size();
            int split = settings.getSplit(setSize);
            int splitBy, firstSize, otherSize;
            if (split < 0) {
                splitBy = 2;
                firstSize = -split;
                otherSize = setSize - firstSize;
            } else {
                splitBy = split;
                firstSize = setSize - (setSize / splitBy) * (splitBy - 1);
                otherSize = setSize / splitBy;
            }
            List<Set<T>> list = new ArrayList<>(splitBy);
            for (int i = 0; i < splitBy; i++) {
                list.add(new LinkedHashSet<T>());
            }
            if (firstSize == otherSize) {
                for (T s : set) {
                    list.get(universalHash(hash, s, index, splitBy)).add(s);
                }
            } else {
                for (T s : set) {
                    int x = universalHash(hash, s, index, setSize) >= firstSize ? 1 : 0;
                    list.get(x).add(s);
                }
            }
            for (int i = 0; i < list.size() - 1; i++) {
                int s2 = (i == 0) ? firstSize : otherSize;
                if (list.get(i).size() != s2) {
                    return null;
                }
            }
            return list;
        }

    }

    /**
     * The evaluator class (not really a class, just a collection of static
     * methods).
     */
    public static class Evaluator<T> {

        private final UniversalHash<T> hash;
        private final Settings settings;
        private final int[] description;

        Evaluator(UniversalHash<T> hash, Settings settings, int[] description) {
            this.hash = hash;
            this.settings = settings;
            this.description = description;
        }

        /**
         * Evaluate the MPHF for the given key.
         *
         * @param x the key
         * @param description the MPHF description
         * @return the return of the function
         */
        public int evaluate(T x) {
//System.out.println("eval " + x);
            int totalSize = description[0];
            int averageBucketSize = settings.getAverageBucketSize();
            int leafSize = settings.getLeafSize();
            int bucketCount = (totalSize + averageBucketSize - 1) / averageBucketSize;
            int bucketId = universalHash(hash, x, 0, bucketCount);
            int pos = 1 + 2 * bucketCount, offset = 0;
            if (bucketId > 0) {
                pos += description[1 + (bucketId - 1)];
                offset = description[1 + bucketCount + (bucketId - 1)];
            }
            int setSize = description[1 + bucketCount + bucketId] - offset;
            while (setSize > 1) {
                int index = description[pos++];
//System.out.println("  setSize " + setSize + " offset " + offset + " index " + index);
                if (setSize <= leafSize) {
                    return offset + universalHash(hash, x, index, setSize);
                }
                int split = settings.getSplit(setSize);
                int splitBy, firstSize, otherSize;
                if (split < 0) {
                    splitBy = 2;
                    firstSize = -split;
                    otherSize = setSize - firstSize;
                } else {
                    splitBy = split;
                    firstSize = setSize - (setSize / splitBy) * (splitBy - 1);
                    otherSize = setSize / splitBy;
                }
                int p;
                if (firstSize == otherSize) {
                    p = universalHash(hash, x, index, splitBy) % splitBy;
                } else {
                    p = universalHash(hash, x, index, setSize) >= firstSize ? 1 : 0;
                }
//System.out.println("   p=" + p);
                for (int i = 0; i < p; i++) {
                    int s2 = (i == 0) ? firstSize : otherSize;
                    offset += s2;
                    pos = skip(description, s2, pos);
                }
                setSize = (p == 0) ? firstSize : otherSize;
            }
            return offset;
        }

        /**
         * Skip the description of a subset.
         *
         * @param description the MPHF description
         * @param setSize the size of the set to skip
         * @param pos the current position in the description list
         * @return the new position in the description list
         */
        int skip(int[] description, int setSize, int pos) {
            int leafSize = settings.getLeafSize();
            if (setSize <= 1) {
                return pos;
            } else if (setSize <= leafSize) {
                return pos + 1;
            }
            pos++;
            int split = settings.getSplit(setSize);
            int splitBy, firstSize, otherSize;
            if (split < 0) {
                splitBy = 2;
                firstSize = -split;
                otherSize = setSize - firstSize;
            } else {
                splitBy = split;
                firstSize = setSize - (setSize / splitBy) * (splitBy - 1);
                otherSize = setSize / splitBy;
            }
            for (int i = 0; i < splitBy; i++) {
                int s2 = (i == 0) ? firstSize : otherSize;
                pos = skip(description, s2, pos);
            }
            return pos;
        }

    }

}
