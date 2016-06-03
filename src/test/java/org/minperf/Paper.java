package org.minperf;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A helper class to produce data of the RecSplit paper.
 */
public class Paper {

    public static void main(String... args) {

        // does not always work (depends on the hardware):
        // 4.1 Parameters
        RandomizedTest.verifyParameters();

        simpleTest();

        // 4.3 Split Rule
        SettingsTest.printSplitRule();
        // 4.4 Data Format
        BitCodes.printPositiveMapping();
        Graphics.generateSampleTikz();
        // 4.7 Probabilities
        Probability.bucketTooLarge();
        Probability.asymmetricCase();
        // 4.8 Rice
        BitCodes.printRiceExamples();
        BitCodes.printEliasDeltaExample();
        // 4.9 Space Usage and Generation Time
        TimeAndSpaceEstimator.spaceUsageEstimateSmallSet();
        TimeAndSpaceEstimator.spaceUsageEstimate();
        // 5.2 Time and Space Complexity of Evaluation
        TimeAndSpaceEstimator.listMaxRecursionDepth();
        // 6.1 Reasonable Parameter Values
        RandomizedTest.reasonableParameterValues();
        // 6.2 Using Real-World Data
        WikipediaTest.main();

        // A Evaluation Time
        TimeAndSpaceEstimator.listEvalulationTimes();

        // B Generation Time Versus Space
        RandomizedTest.printGenerationTimeVersusSpace();

        // slow:

        // 4.1 Parameters
        TimeAndSpaceEstimator.calcBestSizes();
        RandomizedTest.verifyParametersBestSize();
        // 6 Experimental Results
        RandomizedTest.experimentalResults();

        supplementalHashPerfTest();

    }

    private static void simpleTest() {
        for (int i = 8; i < 1000; i *= 2) {
            RandomizedTest.test(2, i, i, true);
        }
        for (int i = 100; i < 10000; i *= 2) {
            RandomizedTest.test(6, 20, i, true);
        }
    }


    private static void supplementalHashPerfTest() {
        final AtomicLong count = new AtomicLong();
        final AtomicLong dummy = new AtomicLong();
          for (int i = 0; i < 8; i++) {
              new Thread() {
                  @Override
                  public void run() {
                      Random r = new Random();
                      long x = r.nextLong();
                      int sum = 0;
                      while (count.get() >= 0) {
                          x += r.nextLong();
                          for (int i = 0; i < 1000; i++) {
                              sum += Settings.supplementalHash(x, i, 100);
                          }
                          count.incrementAndGet();
                      }
                      dummy.set(sum);
                  }
              }.start();
          }
          try {
              long c0 = count.get();
              Thread.sleep(1000);
              long c2 = count.get();
              count.set(-11111111);
              System.out.println("count: " + c2 + " " + c0);
              Thread.sleep(1000);
          } catch (InterruptedException e) {
              throw new RuntimeException(e);
          }
      }

}
