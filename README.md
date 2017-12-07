# minperf
A Minimal Perfect Hash Function Library.

* Mainly written in Java. Includes a C version (currently only evaluation of a MPHF).
* Can generate, in linear time, MPHFs that need less than 1.58 bits per key.
* Can generate MPHFs in less than 100 ns/key, evaluation faster than 100 ns/key, at less than 3 bits per key.
* Concurrent generation.
* Tested up to 1 billion keys.
* Two parameters to configure space needed, generation time, and evaluation time.
* Can be used as a static bloom filter, by storing a hash fingerprint per key.
* Performance very similar than the [Sux4J](https://github.com/vigna/Sux4J) CHD and GOV algorithms, but configurable, with ability to use less space.

This library should already be usable, but it is still work in progress. The plan is to publish a paper.

The algorithm used is described [here as text](https://github.com/thomasmueller/minperf/blob/master/src/test/java/org/minperf/simple/recsplit.md), and
[here as slideshow](https://github.com/thomasmueller/minperf/raw/master/src/test/java/org/minperf/simple/recsplit.pdf) ([also available on SlideShare](https://www.slideshare.net/ThomasMueller12/recsplit-minimal-perfect-hashing)).
