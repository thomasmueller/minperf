package org.minperf.medium;

import java.util.BitSet;
import java.util.Random;

public class EstimateTwoBillionEntries {

    public static void main(String... args) {
        long count = 2_000_000_000L;
        long memGBInBits = 1 * 1024L * 1024 * 1024 * 8;
        System.out.println("one gb, bits: " + memGBInBits);
        System.out.println("bitField " + count / 8 / 1024 / 1024 + " mb");
        double mphfBitsPerKey = 1.8;
        long mphfBits = (long) (mphfBitsPerKey * count);
        System.out.println("mphf bits " + mphfBits + " " + (mphfBits / 8 / 1024 / 1024) + " mb");
        long remainBits = memGBInBits - mphfBits;
        System.out.println("remain " + remainBits);

        double p0 = probDuplicate((int) (count / 32), 64);
        System.out.println("p0=" + (1 + p0));

        int steps = 32;
        long perStep = (int) ((count + steps - 1) / steps);
        long bitsPerStep = perStep * steps;
        System.out.println("steps: " + steps + " perStep: " + perStep + " total " + (long) perStep * steps);
        int bitsBuild = 64; // (int) (remainBits / perStep);
        System.out.println("bits per step " + bitsPerStep + " " + bitsPerStep / 8 / 1024 / 1024 + " mb");
        System.out.println("mphf per step " + mphfBitsPerKey * perStep + " " + mphfBitsPerKey * perStep / 8 / 1024 / 1024 + " mb" );
        double p = probDuplicate((int) perStep, bitsBuild);
        System.out.println("p of duplicate =" + p);

        int segmentsDone = 0;
        int i = 0;
        while (segmentsDone < steps) {
            long mem = (long) (memGBInBits - (segmentsDone + 1) * perStep * mphfBitsPerKey);
            long segments = mem / (bitsBuild * perStep);
            System.out.println("mem: " + mem / 1024 / 1024 / 8 + " mb, segments: " + segments);
            segmentsDone += segments;
            i++;
        }
        System.out.println("i=" + i);

        // 238 + 14 = 252;
        // 1024; 4, ->
        // 968; 3, ->
        // 926; 3, ->
        // 884, 3 ->

    }

    static double testMultipleLists(long size, double factor) {
        Random r = new Random(1);
        // System.out.println("loop " + loop + " remaining: " + size + " factor " + factor);
        BitSet set = new BitSet((int) (size * factor));
        BitSet duplicates = new BitSet((int) (size * factor));
        for(int i=0; i<size;i++) {
            int x = r.nextInt((int)(size * factor));
            if (set.get(x)) {
                duplicates.set(x);
            }
            set.set(x);
        }
        int sole = set.cardinality() - duplicates.cardinality();
//        System.out.println("  duplicates " + card);
        return (double) sole / size;
    }

    private static double probDuplicate(int n, int bits) {
        // System.out.println(msg);
        double x = Math.pow(2, bits);
        // System.out.println(x);
        double p = 1.0 - Math.pow(Math.E, -(double) n * n / 2 / x);
        return p;
    }

/**


Create a minimal perfect hash function (MPHF).
At around 1.8 bits per key
(using the [RecSplit](https://github.com/thomasmueller/minperf)
  [algorithm](https://www.slideshare.net/ThomasMueller12/recsplit-minimal-perfect-hashing)).
, this uses about 429 MB.
(here, 1 MB is 2^20 bytes, 1 GB is 2^30 bytes.)
For each user, allocate one bit as a marker, about 238 MB. So memory usage is around 667 MB.
Then read the posts, for each user calculate the hash, and set the related bit if needed.
Read the user table again, calculate the hash, check if the bit is set.

Generating the MPHF is a bit tricky, not because it is slow
(this may take around 30 minutes of CPU time),
but due to memory usage. With 1 GB or RAM, It needs to be done in segments.
Let's say we use 32 segments of about the same size, as follows:
* Loop segmentId from 0 to 31.
* For each user, calculate the hash code, modulo 32 (or bitwise and 31).
* If this doesn't match the current segmentId, ignore this user.
* Calculate a 64 bit hash code (using a second hash function), and add that to the list.
* Do this until all users are read.
* A segment will contain about 62.5 million keys (2 billion divided by 32), which needs about 238 MB.
* Sort this list by key (in place) to detect duplicates.
  With 64 bit entries, the probability of duplicates is very low,
  but if there are any, use a different hash function and try again.
* Now calculate the MPHF for this segment.
 The RecSplit algorithm is the fastest I know for the given space budget.
  The [CHD](https://github.com/zvelo/cmph/blob/master/src/chd.c) algorithm can be used as well,
  but needs more space / is slower to generate.
* Repeat until all segments are processed.

The above algorithm reads the user list 32 times.
This could be reduced to about 10 if more segments are used (for example one million),
and many segments per step are read as fits in memory. With smaller segments,
less bits per key are needed to the reduced probability of duplicates within one segment.

 */

}
