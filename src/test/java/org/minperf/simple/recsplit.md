## Minimal Perfect Hashing with RecSplit

## Use Cases

This could be a Facebook interview question:
Your task is to find which users didn't ever create a post with an emoji.
You have the list of users (2 billion), which you can scan at most 40 times,
and a huge list of posts (user and text), which you can scan only once. 
Also, you have one computer, with 1 GB of RAM. 
And no external disk, so it has to be done all in RAM. Within two days.
Can you do it? How?

Of course you need one bit per user. That is 240 MB.
So that leaves around 3 bit per user, which is not enough for a regular hash table.
So the answer is, you use minimal perfect hashing.
This needs just 2 bits for the index (a bit more during construction).

Some use cases for minimal perfect hashing are:

* Page Rank: there are billions of web sites (URLs or host names), 
    and for each entry, you need a few bits for a counter to calculate the rank.
* Garbage collection in a de-duplication store: 
    billion of keys, each entry needs a one-bit marker.
* Eye-based tracking: each word has many thousand
    "near matches" (tracking is very inaccurate)

For the above use cases, you could use a database of some kind,
but that requires much more space and IO.

In addition to the above, minimum perfect hash tables 
are sometimes used in compilers, to index entries in a "switch" statement.
The main benefit is that case is the speed of hash functions,
and not the small size of the index.

## Minimal Perfect Hashing

What is minimal perfect hashing exactly, and how is it different from regular hashing?

A regular hash function can have collisions, meaning multiple keys can hash to the same code.
That means in a regular hash table, we need to keep the keys, so that we can resolve possible conflicts.
For a fixed set of keys, it is possible, with some effort, to find a perfect hash function (PHF),
that is a hash function that has no collisions for the given set: 
such a PHF maps n keys to m distinct values, where m >= n.
With such a PHF, we can save space, 
as we don't necessarily need to keep the keys to resolve conflicts.
We can save even more space if there are no gaps;
for that we need to find a minimal perfect hash function (MPHF),
that is a hash function that maps the n keys to n distinct values.
This only needs around 2 bits per key.

The main disadvantage of a minimal perfect hash functions is that it 
requires a fixed, pre-defined set of keys
(even thought there exist techniques that support modifications,
under the name Dynamic Perfect Hashing).
Then, not storing the key means you must only do lookups
by a key that are in the set, or that false positives are OK.
For example for garbage collection, there are simply no lookups by keys not in the set. 
You can detect with high probability if a key is not in the set,
for example using a Bloom filter, or, even faster and more space saving,
by keeping hash fingerprints, for example 8 bit per key for a detection rate of 99%.

## Universal Hashing

How can we construct a (minimal) perfect hash function?
A simple way, which unfortunately only works for small sets, is to use a technique
called "Universal Hashing". This is basically a seeded hash function.
As an example, a regular hash function is (Java code):

    public static int hashCode(byte a[]) {
        int result = 1;
        for (byte element : a)
            result = 31 * result + element;
        return result;
    }
    
A seeded hash function, on the other hand, is something like this:

    public static int universalHash(byte a[], int index) {
        int result = index;
        for (byte element : a)
            result = 31 * result + element + index;
        return result;
    }

This is a sample implementation; it is not very "random".
It is better to use a different algorithms, such as
seeded Murmur Hash, or SpookyHash, or SipHash.
Assuming we use this hash function for the keys  "a", "b", "c", and "d",
we get the following results for the formula
universalHash(k, index) mod 4:

Index: | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7
------ | - | - | - | - | - | - | - | -
Key: "a" | 1 | 2 | 3 | 2 | 3 | 2 | 3 | 2
Key: "b" | 0 | 1 | 2 | 2 | 2 | 2 | 2 | 1
Key: "c" | 0 | 3 | 2 | 2 | 3 | 1 | 1 | 0
Key: "d" | 2 | 1 | 2 | 2 | 0 | 1 | 0 | 0

We see that at index 6, the result for each key is different.
Therefore, f(k) = universalHash(k, 6) mod 4 
is actually a MPHF (minimal perfect hash function) for these keys!
The only thing we need to store is the set size, and the index (the seed value).

Finding a MPHF for a set in this way is brute force: 
find the first index where universalHash(key, index) mod size
has no collision.
This works well for small sets, but quickly get very slow.

The challenge is to support huge sets, O(n)
generation, and constant time evaluation.

All existing algorithms for "large" MPHFs internally use universal hashing.

## Best Algorithms

There is a proven theoretical lower bound for MPHFs, which is around 1.44 bits per key.
The best algorithms that work well for large sets 
(that is, can generate an MPHF in linear time and space, and can evaluate it in constant time) are:

* RecSplit: around 1.8 bits/key
* CHD: around 2.1 bits/key
* BDZ: around 2.7 bits/key

This is assuming somewhat reasonable generation time; both RecSplit and
CHD can approach the theoretical lower bound given enough time.

## RecSplit Algorithm Explained

We now explain how the RecSplit algorithm works.
The algorithm has three phases: partitioning, bucket processing, and storing.

### Partitioning

The first phase partition the n keys into, let's say, n/100 buckets.
That means the average bucket size is about 100.

After this is done, buckets with more than, say, 2000 keys are
merged and processed with a different fallback algorithm, for example BDZ. 
This makes RecSplit a hybrid algorithm in theory, 
however the probability of this is so low that this case can not occur in reality,
except if the hash function is broken.
Having this fallback guarantees that generation is strictly O(n), 
and lookups are strictly O(1).

### Bucket Processing

In this phase, the entries of each bucket are split into small sets
until they can be processed using the brute force algorithm described in Universal Hashing above.
There are three cases:

* Sets of size 0 and 1 are not further processed.

* Set up to size 8 are processed using the brute force algorithm
  described in Universal Hashing above.
  We store the index.

* Larger sets of size s are split as follows:
  in a brute force loop, we find the first x such that
  universalHash(key, index) modulo 2 is 0 in half the cases
  (therefore splits into two subsets, the first one of size s/2, and the second one the rest).
  We store that index, and then process the subsets recursively.
 
The bucket description is then the list of indexes that split the set.

### Storing

In order to use less than 2 bits per key, some compression is needed.

First, the size of the complete set is stored using the Elias Delta code.
This needs few bits for small values, and more bits for larger values.

Then, the bucket metadata is stored, in the form of two lists:
This first list contains the number of entries per bucket,
the second one the positions of the bucket descriptions.
The lists are stored in the form of Elias-Fano monotone lists.

Last, each bucket description is stored.
The indexes are stored using the Rice code, so that
 small indexes use less bits that large ones.
 
### Observations

Most time of the RecSplit algorithm is spend in the Bucket Processing phase.
That part is easy to parallelize, as buckets are independent of each other.
It also allows to generate the MPHF incrementally if needed,
without having to keep it fully in memory all the time.

Time can be traded for space. If larger sets are processed
directly (without splitting them first into smaller subsets),
then the space used shrinks, even below 1.52 bits per key. 
However, this will slow down generation a lot.

### Perfect Hashing and k-Perfect Hashing

Non-minimal perfect hashing allows for gaps,
that is it maps n keys to a value 0..m where m > n,
still without conflicts.
The algorithm described above can be easily modified for this case,
all that is needed is to change the the last stage,
how sets up to size 8 are processed, to use a larger modulo 
(for example modulo 9, in which case m is 1.125 n).
This reduces space usage and speeds up generation.

k-perfect hashing allows for conflicts, so that at most k keys map to the same value.
The algorithm described can also be modified for this case,
by changing the last stage to for example use modulo 4.

### Comparison to Competing Algorithms

The most space saving competing algorithms, CHD and BDZ,
are not easily to parallelize, because they don't partition the set
in the same way, and require processing in a certain order.
Also, they require more memory during construction.
When generating the hash function sequentially,
they both require more CPU time for the same space usage.
