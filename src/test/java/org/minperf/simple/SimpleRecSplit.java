package org.minperf.simple;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.minperf.universal.StringHash;

/**
 * A very simple implementation of the RecSplit algorithm. Compression is not
 * used.
 */
public class SimpleRecSplit {

    /**
     * The average bucket size.
     */
    static final int AVERAGE_BUCKET_SIZE = 100;

    /**
     * The maximum size of a leaf.
     */
    static final int LEAF_SIZE = 8;

    /**
     * The universal hash function.
     */
    static final StringHash UNIVERSAL_HASH = new StringHash();

    /**
     * Calculate the universal hash of the string modulo the given value.
     *
     * @param x the key
     * @param index the universal hash index
     * @param mod the modulo
     * @return a value between 0 (including) and the modulo (excluding)
     */
    static int universalHash(String x, int index, int mod) {
        return (int) Math.abs(UNIVERSAL_HASH.universalHash(x, index) % mod);
    }

    /**
     * The generator class (not really a class, just a collection of static
     * methods).
     */
    public static class Generator {

        /**
         * Generate the MPHF description for the given set.
         *
         * @param set the set
         * @return the description, in the form of an integer array
         *         (uncompressed)
         */
        public static int[] generate(Set<String> set) {
            int bucketCount = 1 + (set.size() / AVERAGE_BUCKET_SIZE);
            List<Set<String>> buckets = partition(set, bucketCount);
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
        static List<Set<String>> partition(Set<String> set, int bucketCount) {
            List<Set<String>> buckets = new ArrayList<Set<String>>();
            for (int i = 0; i < bucketCount; i++) {
                buckets.add(new LinkedHashSet<String>());
            }
            for (String x : set) {
                int bucketId = universalHash(x, 0, bucketCount);
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
        static void processRecursively(Set<String> set, List<Integer> description) {
            if (set.size() <= 1) {
                return;
            }
            if (set.size() <= LEAF_SIZE) {
                for (int x = 0;; x++) {
                    if (canMapDirectly(set, x)) {
                        description.add(x);
                        return;
                    }
                }
            }
            for (int x = 0;; x++) {
                List<Set<String>> list = trySplit(set, x);
                if (list != null) {
                    description.add(x);
                    for (Set<String> s : list) {
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
        static boolean canMapDirectly(Set<String> set, int index) {
            Set<Integer> used = new HashSet<Integer>();
            for (String s : set) {
                used.add(universalHash(s, index, set.size()));
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
        static List<Set<String>> trySplit(Set<String> set, int index) {
            List<Set<String>> list = new ArrayList<>(2);
            for (int i = 0; i < 2; i++) {
                list.add(new LinkedHashSet<String>());
            }
            for (String s : set) {
                list.get(universalHash(s, index, 2)).add(s);
            }
            return (list.get(0).size() == set.size() / 2) ? list : null;
        }

    }

    /**
     * The evaluator class (not really a class, just a collection of static
     * methods).
     */
    public static class Evaluator {

        /**
         * Evaluate the MPHF for the given key.
         *
         * @param x the key
         * @param description the MPHF description
         * @return the return of the function
         */
        public static int evaluate(String x, int[] description) {
            int totalSize = description[0];
            int bucketCount = 1 + totalSize / AVERAGE_BUCKET_SIZE;
            int bucketId = universalHash(x, 0, bucketCount);
            int pos = 1 + 2 * bucketCount, offset = 0;
            if (bucketId > 0) {
                pos += description[1 + (bucketId - 1)];
                offset = description[1 + bucketCount + (bucketId - 1)];
            }
            int setSize = description[1 + bucketCount + bucketId] - offset;
            while (setSize > 1) {
                int index = description[pos++];
                if (setSize <= LEAF_SIZE) {
                    return offset + universalHash(x, index, setSize);
                }
                int p = universalHash(x, index, 2);
                if (p % 2 == 0) {
                    setSize = setSize / 2;
                } else {
                    offset += setSize / 2;
                    pos = skip(description, setSize / 2, pos);
                    setSize -= setSize / 2;
                }
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
        static int skip(int[] description, int setSize, int pos) {
            if (setSize <= 1) {
                return pos;
            } else if (setSize <= LEAF_SIZE) {
                return pos + 1;
            }
            pos = skip(description, setSize / 2, pos + 1);
            return skip(description, setSize - setSize / 2, pos);
        }

    }

}
