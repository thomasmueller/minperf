package org.minperf.bloom;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;

public class BatchTest {
  final int N = 500000;
  long[] keys = new long[N];
  HashSet<Long> keyset = new HashSet<Long>();
  final int Ntest = 500000;
  long[] testkeys = new long[Ntest];
  long[] testpresentkeys = new long[Ntest];
  long[] testmixedkeys = new long[Ntest];
  int testcount, testpresentcount, testmixedcount;

  @Before
  public void setup() {
    System.out.println("Generating large random array");
    Random r = new Random(1234);
    for (int i = 0; i < N; i++) {
      keys[i] = r.nextLong();
      keyset.add(keys[i]);
    }
    for (int i = 0; i < Ntest; i++) {
      testkeys[i] = r.nextLong();
      testpresentkeys[i] = keys[r.nextInt(N)];
      testmixedkeys[i] = (r.nextBoolean()) ? testkeys[i] : testpresentkeys[i];
      if (keyset.contains(testkeys[i]))
        testcount++;
      if (keyset.contains(testpresentkeys[i]))
        testpresentcount++;
      if (keyset.contains(testmixedkeys[i]))
        testmixedcount++;
    }
  }

  @Test 
  public void testXor8Repeatedly() {
    double worsefpp = 0;
    double avgfpp = 0;
    int time = 100;
    for(int k = 0; k < time; k++) {
        final int N = 500000;
        HashSet<Long> keyset = new HashSet<Long>();
        final int Ntest = 500000;
        long[] testkeys = new long[Ntest];
        long[] testpresentkeys = new long[Ntest];
        long[] testmixedkeys = new long[Ntest];
        int testcount = 0, testpresentcount = 0, testmixedcount = 0;
        long[] keys = new long[N];
        Random r = new Random(1234 + k);
        for (int i = 0; i < N; i++) {
          keys[i] = r.nextLong();
          keyset.add(keys[i]);
        }
        for (int i = 0; i < Ntest; i++) {
          testkeys[i] = r.nextLong();
          testpresentkeys[i] = keys[r.nextInt(N)];
          testmixedkeys[i] = (r.nextBoolean()) ? testkeys[i] : testpresentkeys[i];
          if (keyset.contains(testkeys[i]))
            testcount++;
          if (keyset.contains(testpresentkeys[i]))
            testpresentcount++;
          if (keyset.contains(testmixedkeys[i]))
            testmixedcount++;
        }
        XorFilter_8bit xor8 = XorFilter_8bit.construct(keys);
        int count = 0;
        int presentcount = 0;
        int mixedcount = 0;
        for (int i = 0; i < Ntest; ++i) {
          if (xor8.mayContain(testkeys[i]))
            count++;
          if (xor8.mayContain(testpresentkeys[i]))
            presentcount++;
          if (xor8.mayContain(testmixedkeys[i]))
            mixedcount++;
        }
        double mixedfpp = (mixedcount - testmixedcount) * 1.0 / testmixedcount;
        System.out.println("mixed fpp "+ mixedfpp+ " (expected: "+1.0/256+")");
        if(mixedfpp > worsefpp) worsefpp = mixedfpp;
        avgfpp += mixedfpp;
     }
     avgfpp /= time;
     System.out.println("avg mixed fpp "+ avgfpp+ " (expected: "+1.0/256+")");
     System.out.println("worse mixed fpp "+ worsefpp);
     assertTrue(avgfpp < 1.0 / 256 * 1.05); // should be improbable
     assertTrue(worsefpp < 3.0 / 256); // should be improbable
   }


  @Test 
  public void testXor16Repeatedly() {
    double worsefpp = 0;
    double avgfpp = 0;
    int time = 100;
    for(int k = 0; k < time; k++) {
        final int N = 500000;
        HashSet<Long> keyset = new HashSet<Long>();
        final int Ntest = 500000;
        long[] testkeys = new long[Ntest];
        long[] testpresentkeys = new long[Ntest];
        long[] testmixedkeys = new long[Ntest];
        int testcount = 0, testpresentcount = 0, testmixedcount = 0;
        long[] keys = new long[N];
        Random r = new Random(1234 + k);
        for (int i = 0; i < N; i++) {
          keys[i] = r.nextLong();
          keyset.add(keys[i]);
        }
        for (int i = 0; i < Ntest; i++) {
          testkeys[i] = r.nextLong();
          testpresentkeys[i] = keys[r.nextInt(N)];
          testmixedkeys[i] = (r.nextBoolean()) ? testkeys[i] : testpresentkeys[i];
          if (keyset.contains(testkeys[i]))
            testcount++;
          if (keyset.contains(testpresentkeys[i]))
            testpresentcount++;
          if (keyset.contains(testmixedkeys[i]))
            testmixedcount++;
        }
        XorFilter_16bit xor16 = XorFilter_16bit.construct(keys);
        int count = 0;
        int presentcount = 0;
        int mixedcount = 0;
        for (int i = 0; i < Ntest; ++i) {
          if (xor16.mayContain(testkeys[i]))
            count++;
          if (xor16.mayContain(testpresentkeys[i]))
            presentcount++;
          if (xor16.mayContain(testmixedkeys[i]))
            mixedcount++;
        }
        double mixedfpp = (mixedcount - testmixedcount) * 1.0 / testmixedcount;
        System.out.println("mixed fpp "+ mixedfpp+ " (expected: "+1.0/65536+")");
        if(mixedfpp > worsefpp) worsefpp = mixedfpp;
        avgfpp += mixedfpp;
     }
     avgfpp /= time;
     System.out.println("avg mixed fpp "+ avgfpp+ " (expected: "+1.0/65536+")");
     System.out.println("worse mixed fpp "+ worsefpp);
     assertTrue(avgfpp < 1.0 / 65536 * 1.05); // should be improbable
     assertTrue(worsefpp < 3.0 / 65536); // should be improbable
   }

  @Test
  public void testXor8() {
    System.out.println("testXor8");
    XorFilter_8bit xor8 = XorFilter_8bit.construct(keys);
    int count = 0;
    int presentcount = 0;
    int mixedcount = 0;
    int bcount = countbatch8(xor8, testkeys, 128);
    int bpresentcount = countbatch8(xor8, testpresentkeys, 128);
    int bmixedcount = countbatch8(xor8, testmixedkeys, 128);
    for (int i = 0; i < Ntest; ++i) {
      if (xor8.mayContain(testkeys[i]))
        count++;
      if (xor8.mayContain(testpresentkeys[i]))
        presentcount++;
      if (xor8.mayContain(testmixedkeys[i]))
        mixedcount++;
    }
    assertEquals(count, bcount);
    assertEquals(presentcount, bpresentcount);
    assertEquals(mixedcount, bmixedcount);
    if(testcount>0) System.out.println("fpp "+(count - testcount) * 1.0 / testcount);
    System.out.println("fpp "+(presentcount - testpresentcount) * 1.0 / testpresentcount);
    double mixedfpp = (mixedcount - testmixedcount) * 1.0 / testmixedcount;
    System.out.println("mixed fpp "+ mixedfpp+ " (expected: "+1.0/256+")");
    assertTrue(mixedfpp < 3.0 /256); // should be improbable
  }

  @Test
  public void testXor16() {
    System.out.println("testXor16");
    XorFilter_16bit xor16 = XorFilter_16bit.construct(keys);
    int count = 0;
    int presentcount = 0;
    int mixedcount = 0;
    int bcount = countbatch16(xor16, testkeys, 128);
    int bpresentcount = countbatch16(xor16, testpresentkeys, 128);
    int bmixedcount = countbatch16(xor16, testmixedkeys, 128);
    for (int i = 0; i < Ntest; ++i) {
      if (xor16.mayContain(testkeys[i]))
        count++;
      if (xor16.mayContain(testpresentkeys[i]))
        presentcount++;
      if (xor16.mayContain(testmixedkeys[i]))
        mixedcount++;
    }
    assertEquals(count, bcount);
    assertEquals(presentcount, bpresentcount);
    assertEquals(mixedcount, bmixedcount);
    if(testcount>0) System.out.println("fpp "+(count - testcount) * 1.0 / testcount);
    System.out.println("fpp "+(presentcount - testpresentcount) * 1.0 / testpresentcount);
    double mixedfpp = (mixedcount - testmixedcount) * 1.0 / testmixedcount;
    System.out.println("mixed fpp "+ mixedfpp+ " (expected: "+1.0/65536+")");
    assertTrue(mixedfpp < 3.0 / 65536); // should be improbable
  }

  private static int countbatch8(XorFilter_8bit xor8, long[] testkeys, int batchsize) {
    int sum = 0;
    final long[] buffer = new long[batchsize];
    final int[] tmp = new int[4 * batchsize];
    int k = 0;
    for (; k + batchsize <= testkeys.length; k += batchsize) {
      sum += (xor8.mayContainBatch(testkeys, k, batchsize, buffer, tmp));
    }
    sum += (xor8.mayContainBatch(testkeys, k, testkeys.length % batchsize, buffer, tmp));
    return sum;
  }

  private static int countbatch16(XorFilter_16bit xor16, long[] testkeys, int batchsize) {
    int sum = 0;
    final long[] buffer = new long[batchsize];
    final int[] tmp = new int[4 * batchsize];
    int k = 0;
    for (; k + batchsize <= testkeys.length; k += batchsize) {
      sum += (xor16.mayContainBatch(testkeys, k, batchsize, buffer, tmp));
    }
    sum += (xor16.mayContainBatch(testkeys, k, testkeys.length % batchsize, buffer, tmp));
    return sum;
  }
}
