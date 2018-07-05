package org.minperf.hem;

import java.io.File;
import java.util.HashSet;
import java.util.PrimitiveIterator;

import org.minperf.BitCodes;
import org.minperf.FunctionInfo;
import org.minperf.RandomizedTest;

public class HEM<T> {

    /**

generate command line:
- keys file (lines as text, or fixed number of bytes per key)
- estimated (?) size -> to calculate signature size, chunk size
- signature size (overrideable)
- chunk size (overrideable)
- number of keys to batch
- option to either (a) merge duplicates (on clash), or (b) stop when finding a clash - defaults to merge?
- how to do parallel key processing? only do first part, in parallel, with multiple key files
- second step is: merge sort signature files (option to just do that, so 64:1 merge is possible)
- also generate mphf (by streaming; chunk by chunk)
- how to do parallel construction? by defining the chunk range, and merge MPHF at the end

raw MPHF data format:
* 64 bit: total size (number of keys)
* 1 byte: number of bits per chunk (number of chunks is 2^n)
* per chunk:
* - 1 byte: signature hash (usually 0)
* - chunk data (includes size of chunk)
* - filler to full byte (so chunks can be easily concatenated)

in-memory data format:
* signature mask
* chunk shift (chunk id = signature >>> shift)
* array of evaluators
* each evaluator knows the offset, and signature hash

generate algorithm:
* calc signature size (or use override)
* calc number of chunks
* read data, calc signatures, truncate to required size (truncate left part?)
* sort & store signatures; one file per chunk (use uuid to allow concurrent generation)

evaluation algorithm:
* calc 128 bit signature
* mask (to truncate, same as for generation)
* shift to get chunk size

target: <50 seconds for 1 billion keys
page 7
50 ns / key?


24 threads
10^12 64-bit keys
3.7 bits/key
35.4 hours and required 637 GB RAM
bit arrays ( 459 GB)
the memory required for loading 20 billion keys in memory ( 178 GB).
keys were loaded in memory when |Fi|  2% of total keys
(i.e. when remaining number of keys to index was lower than 20 billion).
The final MPHF occupied 3.71 bits per key.
Query time (ns)  around 216 ns ?
xor-shift based hash function
Retrieval and Perfect Hashing Using Fingerprinting
Xorshift128*

2124 min
127440 sec
127440000000000 ns
1000000000000 keys
127.44 ns / key
24 threads, 1 thread might be 3058.56 ns / key


1000000000
35000000000 ns
35 ns / key, 8 threads; 280 ns / key
229000000000 ns, 1 thread; 229 ns / key


     */

    public static void main(String... args) throws InterruptedException {
        // 3.7 bits/key
//        250 ns / key with 1 thread
//        System.out.println(250 * 1000 * 1000000L / 1_000_000_000);

        for (int i = 0; i < 10; i++) {
            HashSet<Long> set = RandomizedTest.createSet(1000000, 1);
            for (int leafSize = 2; leafSize < 8; leafSize++) {
                for (int averageBucketSize = 4; averageBucketSize < 32; averageBucketSize *= 2) {
                    FunctionInfo info = RandomizedTest.test(leafSize, averageBucketSize, set.size(), false, 1, true);
                    System.out.println(info);
                }
            }
        }

//        test(args[0]);
//        test(args[1]);
    }

    private static void test(String fileName) throws InterruptedException {

        // dd if=/dev/urandom of=~/temp/hash/key64.bin bs=1048576 count=1024
        // (13'017'359 bytes/sec)
        // int shift = 36;

        int shift;
        // dd if=/dev/urandom of=~/temp/hash/key64b.bin bs=1048576 count=4096

        boolean varLong = false;
        int count = (int) (new File(fileName).length() / 8);
        double mean = Long.MAX_VALUE / count * 2;
        shift = BitCodes.calcBestGolombRiceShiftFromMean(mean);
        long[] data = new long[count];
        long[] d2 = new long[count];
        String diffsFileName = fileName + ".diffs";

        for (int test = 0; test < 2; test++) {
            varLong ^= true;
            long time;
            System.out.println("");
            System.out.println("test #" + test + " " + (varLong ? "varlong" : "golomb"));
            Thread.sleep(1000);
            time = System.nanoTime();
            PrimitiveIterator.OfLong it = KeyReader.readSignaturesFromTextFile64(fileName);
            for (int i = 0; it.hasNext(); i++) {
                data[i] = it.nextLong();
            }
            time = System.nanoTime() - time;
            System.out.println("read time: " + time / count + " ns/key, count=" + count);
            Thread.sleep(1000);
            time = System.nanoTime();
            Sort.parallelSortUnsigned(data);
            // Sort.sortUnsignedSimple(data);
            time = System.nanoTime() - time;
            System.out.println("sort time: " + time / count + " ns/key");
            SortedSignatures.FileWriter w = new SortedSignatures.FileWriter(diffsFileName);
            Thread.sleep(1000);
            time = System.nanoTime();
            if (varLong) {
                w.writeDiffsVarLong(data);
            } else {
                w.writeDiffsGolombRice(data, shift);
            }
            w.close();
            time = System.nanoTime() - time;
            System.out.println("diff write time: " + time / count + " ns/key");
            System.out.println("  diff file size: " + new File(diffsFileName).length() / (double) count + " bytes/key");
            Thread.sleep(1000);
            time = System.nanoTime();
            SortedSignatures.FileIterator r = new SortedSignatures.FileIterator(diffsFileName);
            PrimitiveIterator.OfLong resultIterator;
            if (varLong) {
                resultIterator = r.iteratorVarLong(count);
            } else {
                resultIterator = r.iteratorGolombRice(count, shift);
            }
            for (int i = 0; i < count; i++) {
                d2[i] = resultIterator.nextLong();
            }
            time = System.nanoTime() - time;
            System.out.println("diff read time: " + time / count + " ns/key");
            for (int i = 0; i < d2.length; i++) {
                if (data[i] != d2[i]) {
                    throw new AssertionError("" + i + " " + data[i] + "<>" + d2[i]);
                }
            }
        }
    }

}
