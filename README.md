# minperf
A Minimal Perfect Hash Function Library.

* Written in Java.
* Can generate, in linear time, MPHFs that need less than 1.58 bits per key.
* Concurrent generation.
* Tested up to 1 billion keys.
* Two parameters to configure space needed, generation time, and evaluation time.
* Performance very similar than the [Sux4J](https://github.com/vigna/Sux4J) CHD and GOV algorithms, but configurable, with ability to use less space.

This library should already be usable, but it is still work in progress. The plan is to publish a paper.

The algorithm used is [described here](https://github.com/thomasmueller/minperf/raw/master/src/test/java/org/minperf/simple/recsplit.pdf).
