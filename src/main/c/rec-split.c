#include <stdio.h>
#include <stddef.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <time.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>

// file I/O

int64_t getFileSize(FILE* fp) {
    struct stat buf;
    int fd = fileno(fp);
    fstat(fd, &buf);
    int64_t size = buf.st_size;
    return size;
}

void fixEndian(uint64_t* longArray, uint64_t byteCount) {
    uint8_t* byteArray = (uint8_t*) longArray;
    uint64_t l=0;
    uint64_t b=0;
    while(b<byteCount) {
        uint64_t x = 0;
        for(int i=0; i<8; i++) {
            x = (x << 8) | byteArray[b++];
        }
        longArray[l++] = x;
    }
}

int loadFile(char* fileName, uint64_t** target) {
    if (!fileName) {
        printf("No file\n");
        return 0;
    }
    printf("Loading file %s\n", fileName);
    FILE* fp = fopen(fileName, "r");
    if (!fp) {
        printf("Could not open file\n");
        return 0;
    }
	int64_t fileSize = getFileSize(fp);
    printf("File size: %lld\n", fileSize);
    
    *target = malloc(fileSize);
    size_t len = fread(*target, fileSize, 1, fp);
    if (len != 1) {
        printf("Could not full read the file\n");
        return 0;
    }
    fclose(fp);
    printf("File %s len=%lld\n", fileName, fileSize);
    fixEndian(*target, fileSize);
    return 1;
}

// bit manipulation

uint64_t rotateLeft64(uint64_t x, uint32_t n) {
    // TODO check if this is the best way, and if it always works as expected
    return (x << (n & 63)) | (x >> (64 - (n & 63)));
}

int numberOfLeadingZeros64(uint64_t x) {
    // If x is 0, the result is undefined.
	return __builtin_clzl(x);
}

int numberOfLeadingZeros32(uint32_t x) {
    // If x is 0, the result is undefined.
	return __builtin_clz(x);
}

// hashing related

inline uint32_t reduce(uint32_t hash, uint32_t n) {
	// http://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/
	return (uint32_t) (((uint64_t) hash * n) >> 32);
}

uint64_t getScaleFactor(uint32_t multiply, uint32_t divide) {
    return divide == 0 ? 0 : ((uint64_t) multiply << 32) / divide + 1;
}

uint64_t getSipHash24b(char* b, int start, int end, uint64_t k0, uint64_t k1) {
    uint64_t v0 = k0 ^ 0x736f6d6570736575L;
    uint64_t v1 = k1 ^ 0x646f72616e646f6dL;
    uint64_t v2 = k0 ^ 0x6c7967656e657261L;
    uint64_t v3 = k1 ^ 0x7465646279746573L;
    int repeat;
    for (int off = start; off <= end + 8; off += 8) {
        long m;
        if (off <= end) {
            m = 0;
            int i = 0;
            for (; i < 8 && off + i < end; i++) {
                m |= ((uint64_t) b[off + i] & 255) << (8 * i);
            }
            if (i < 8) {
                m |= ((uint64_t) end - start) << 56;
            }
            v3 ^= m;
            repeat = 2;
        } else {
            m = 0;
            v2 ^= 0xff;
            repeat = 4;
        }
        for (int i = 0; i < repeat; i++) {
            v0 += v1;
            v2 += v3;
            v1 = rotateLeft64(v1, 13);
            v3 = rotateLeft64(v3, 16);
            v1 ^= v0;
            v3 ^= v2;
            v0 = rotateLeft64(v0, 32);
            v2 += v1;
            v0 += v3;
            v1 = rotateLeft64(v1, 17);
            v3 = rotateLeft64(v3, 21);
            v1 ^= v2;
            v3 ^= v0;
            v2 = rotateLeft64(v2, 32);
        }
        v0 ^= m;
    }
    return v0 ^ v1 ^ v2 ^ v3;
}

uint64_t getSipHash24(char* b, uint64_t k0, uint64_t k1) {
	// TODO assuming 8 bits per char
	return getSipHash24b(b, 0, strlen(b), k0, k1);
}

uint64_t universalHash(char* key, uint64_t index) {
    return getSipHash24(key, index, index);
}

// BitBuffer

uint64_t* data;
uint64_t pos;

uint64_t readBit() {
    uint64_t p = pos++;
    return (data[p >> 6] >> (63 - (p & 63))) & 1;
}

uint64_t readEliasDelta() {
    int qq = 0;
    while (readBit() == 0) {
        qq++;
    }
    int64_t q = 1;
    for (int i = qq; i > 0; i--) {
        q = (q << 1) | readBit();
    }
    uint64_t x = 1;
	for (int64_t i = q - 2; i >= 0; i--) {
        x = (x << 1) | readBit();
    }
    return x;
}

uint64_t readNumber(uint64_t pos, int bitCount) {
	if (bitCount == 0) {
		return 0;
	}
	int remainingBits = 64 - (pos & 63);
	int index = pos >> 6;
 	long x = data[index];
    if (bitCount <= remainingBits) {
	    x >>= remainingBits - bitCount;
        return x & ((1L << bitCount) - 1);
    }
    x = x & ((1L << remainingBits) - 1);
    return (x << (bitCount - remainingBits)) |
        (data[index + 1] >> (64 - bitCount + remainingBits));
}

uint32_t supplementalHash(uint64_t hash, uint64_t index) {
	// it would be better to use long,
    // but with some processors, 32-bit multiplication
    // seem to be much faster
    // (about 1200 ms for 32 bit, about 2000 ms for 64 bit)
    uint32_t x = (uint32_t) (rotateLeft64(hash, (uint32_t) index) ^ index);
    x = ((x >> 16) ^ x) * 0x45d9f3b;
    x = ((x >> 16) ^ x) * 0x45d9f3b;
    x = (x >> 16) ^ x;
    return x;
}

uint64_t unfoldSigned(uint64_t x) {
    return ((x & 1) == 1) ? (x + 1) / 2 : -(x / 2);
}

int getEliasDeltaSize(uint64_t value) {
	if (value <= 0) {
		// illegal argument
	    return -1;
    }
	int q = 64 - numberOfLeadingZeros64(value);
    int qq = 31 - numberOfLeadingZeros32(q);
    int len = qq + qq + q;
    return len;
}

int readUntilZeroMore(int count, uint64_t pos) {
    while (true) {
        long x = data[++pos];
        if (x == -1L) {
            count += 64;
            continue;
        }
        return count + numberOfLeadingZeros64(~x);
    }
}

int readUntilZero(uint64_t pos) {
    int remainingBits = 64 - (pos & 63);
    uint64_t index = pos >> 6;
    uint64_t x = data[index] << (64 - remainingBits);
    int count = numberOfLeadingZeros64(~x);
    if (count < remainingBits) {
        return count;
    }
    return readUntilZeroMore(count, index);
}

uint64_t skipGolombRice(uint64_t pos, int shift) {
    int q = readUntilZero(pos);
    return pos + q + 1 + shift;
}

// MultiStageMonotoneList

struct MultiStageMonotoneList {
	uint64_t startLevel1, startLevel2, startLevel3;
	int bitCount1, bitCount2, bitCount3;
	uint32_t count1, count2, count3;
	uint64_t factor;
	uint32_t add;
};

struct MultiStageMonotoneList list;

#define SHIFT1 6
#define SHIFT2 3
#define FACTOR1 32
#define FACTOR2 16

void MultiStageMonotoneList_load(struct MultiStageMonotoneList* this) {
    this->count3 = (uint32_t) readEliasDelta() - 1;
    int diff = (uint32_t) readEliasDelta() - 1;
    this->factor = getScaleFactor(diff, this->count3);
    this->add = (uint32_t) unfoldSigned(readEliasDelta() - 1);
    this->bitCount1 = (int) readEliasDelta() - 1;
    this->bitCount2 = (int) readEliasDelta() - 1;
    this->bitCount3 = (int) readEliasDelta() - 1;
    this->startLevel1 = pos;
    this->count2 = (this->count3 + (1 << SHIFT2) - 1) >> SHIFT2;
    this->count1 = (this->count3 + (1 << SHIFT1) - 1) >> SHIFT1;
    this->startLevel2 = this->startLevel1 + this->count1 * this->bitCount1;
    this->startLevel3 = this->startLevel2 + this->count2 * this->bitCount2;
    pos = (this->startLevel3 + this->bitCount3 * this->count3);
}

uint32_t MultiStageMonotoneList_get(struct MultiStageMonotoneList* this, uint32_t i) {
    int expected = (int) ((i * this->factor) >> 32) + this->add;
    long a = readNumber(this->startLevel1 + (i >> SHIFT1) * this->bitCount1, this->bitCount1);
    long b = readNumber(this->startLevel2 + (i >> SHIFT2) * this->bitCount2, this->bitCount2);
    long c = readNumber(this->startLevel3 + i * this->bitCount3, this->bitCount3);
    return (int) (expected + a * FACTOR1 + b * FACTOR2 + c);
}

uint64_t MultiStageMonotoneList_getPair(struct MultiStageMonotoneList* this, uint32_t i) {
    return ((uint64_t) MultiStageMonotoneList_get(this, i) << 32) | (MultiStageMonotoneList_get(this, i + 1));
}

// Settings

#define MAX_SIZE 4096
#define SUPPLEMENTAL_HASH_SHIFT 18

struct Settings {
    int leafSize;
    int averageBucketSize;
	int rice[MAX_SIZE];
	int splits[MAX_SIZE];
};

struct Settings settings;

void Settings_load(struct Settings* this) {
    this->leafSize = readEliasDelta() - 1;
    this->averageBucketSize = readEliasDelta() - 1;
    uint32_t len = readEliasDelta() - 1;
    printf("leafSize: %d, averageBucketSize: %d, len: %d\n", 
	    this->leafSize,
	    this->averageBucketSize,
	    len);
    for(int i=0; i<len && i < MAX_SIZE; i++) {
        this->splits[i] = unfoldSigned(readEliasDelta() - 1);
        this->rice[i] = readEliasDelta() - 1;
        if (i < 32)
        printf("  %d: rice=%d, splits: %d\n", i, this->rice[i], this->splits[i]);
        
    }
}

uint64_t getUniversalHashIndex(uint64_t index) {
	return index >> SUPPLEMENTAL_HASH_SHIFT;
}


// RecSplitEvaluator

struct RecSplitEvaluator {
	uint64_t size;
	uint32_t bucketCount;
	uint32_t minStartDiff;
	struct MultiStageMonotoneList startList;	
	uint32_t minOffsetDiff;
	struct MultiStageMonotoneList offsetList;	
	uint32_t startBuckets;
	uint32_t endHeader;
	uint32_t endOffsetList;
};

struct RecSplitEvaluator evaluator;

uint32_t getBucketCount(uint64_t size, int averageBucketSize) {
    return (uint32_t) ((size + averageBucketSize - 1) / averageBucketSize);
}

void RecSplitEvaluator_load(struct RecSplitEvaluator* this) {
	this->size = readEliasDelta() - 1;
	printf("hash size %lld\n", this->size);
	
	this->bucketCount = getBucketCount(this->size, settings.averageBucketSize);
	printf("buckets: %d\n", this->bucketCount);
    int alternative = readBit() != 0;
    if (alternative) {
        // not supported
        printf("not supported: alternative\n");
        return;
    }
    this->minOffsetDiff = (uint32_t) (readEliasDelta() - 1);
	// printf("minOffsetDiff: %d\n", this->minOffsetDiff);
    this->minStartDiff = (uint32_t) (readEliasDelta() - 1);
	// printf("minStartDiff: %d\n", this->minStartDiff);
    this->endHeader = pos;
    MultiStageMonotoneList_load(&(this->offsetList));
	this->endOffsetList = pos;
    MultiStageMonotoneList_load(&(this->startList));
	this->startBuckets = pos;
	
	// printf("hash loaded\n");
}

uint32_t getMinBitCount(uint32_t size) {
    // at least 1.375 bits per key (if it is less, fill with zeroes)
    return (size  * 11 + 7) >> 3;
}

uint32_t skip(struct RecSplitEvaluator* this, uint64_t pos, uint32_t size) {
    if (size < 2) {
        return pos;
    }
    pos = skipGolombRice(pos, settings.rice[size]);
    if (size <= settings.leafSize) {
        return pos;
    }
    int split = settings.splits[size];
    int firstPart, otherPart;
    if (split < 0) {
        firstPart = -split;
        otherPart = size - firstPart;
        split = 2;
    } else {
        firstPart = size / split;
        otherPart = firstPart;
    }
    int s = firstPart;
    for (int i = 0; i < split; i++) {
        pos = skip(this, pos, s);
        s = otherPart;
    }
    return pos;
}

uint32_t evaluate2(struct RecSplitEvaluator* this, uint64_t pos, char* obj, uint64_t hashCode,
        uint64_t index, uint32_t add, uint32_t size) {
    while (true) {
        if (size < 2) {
            return add;
        }
        int shift = settings.rice[size];
        uint64_t q = readUntilZero(pos);
        pos += q + 1;
        uint64_t value = (q << shift) | readNumber(pos, shift);
        pos += shift;
        uint64_t oldX = getUniversalHashIndex(index);
        index += value + 1;
        uint64_t x = getUniversalHashIndex(index);
        if (x != oldX) {
            hashCode = universalHash(obj, x);
        }
        if (size <= settings.leafSize) {
            int h = supplementalHash(hashCode, index);
            h = reduce(h, size);
//printf("shift %d q %d value %lld oldX %lld x %lld size %d h %d add %d\n", shift, q, value, oldX, x, size, h, add);
            
            return add + h;
        }
        int split = settings.splits[size];
        int firstPart, otherPart;
        if (split < 0) {
            firstPart = -split;
            otherPart = size - firstPart;
            split = 2;
        } else {
            firstPart = size / split;
            otherPart = firstPart;
        }
        int h = supplementalHash(hashCode, index);
        if (firstPart != otherPart) {
            h = reduce(h, size);
            if (h < firstPart) {
                size = firstPart;
                continue;
            }
            pos = skip(this, pos, firstPart);
            add += firstPart;
            size = otherPart;
            continue;
        }
        h = reduce(h, split);
        for (int i = 0; i < h; i++) {
            pos = skip(this, pos, firstPart);
            add += firstPart;
        }
        size = firstPart;
    }
}

uint64_t evaluate(struct RecSplitEvaluator* this, char* obj) {
	uint64_t hashCode = universalHash(obj, 0);
//printf("  hashCode %s = %lld\n", obj, hashCode); 	
	uint32_t b;
    if (this->bucketCount == 1) {
        b = 0;
    } else {
        b = reduce((uint32_t) hashCode, this->bucketCount);
    }
//printf("  bucket %d\n", b); 	
	uint32_t startPos;
    uint64_t offsetPair = MultiStageMonotoneList_getPair(&this->offsetList, b);
    uint32_t offset = (uint32_t) (offsetPair >> 32) + b * this->minOffsetDiff;
    uint32_t offsetNext = ((uint32_t) offsetPair) + (b + 1) * this->minOffsetDiff;
    if (offsetNext == offset) {
        // entry not found
        return 0;
    }
    uint32_t bucketSize = offsetNext - offset;
    startPos = this->startBuckets +
            getMinBitCount(offset) +
            MultiStageMonotoneList_get(&this->startList, b) + b * this->minStartDiff;
// printf("  startPos %d offset %d bucketSize %d\n", startPos, offset, bucketSize); 	
    return evaluate2(this, startPos, obj, hashCode, 0, offset, bucketSize);
}

// demo

int main(int argc, char** argv) {
    char* settingsFile = 0;
    char* hashFile = 0;
    char* keyFile = 0;
    
    ++argv;
    --argc;
    if (argc > 0) {
        settingsFile = argv[0];
        ++argv;
        --argc;
        if (argc > 0) {
            hashFile = argv[0];
            ++argv;
            --argc;
            if (argc > 0) {
                keyFile = argv[0];
                ++argv;
                --argc;
            }    
        }
    }
    
    
    if (!loadFile(settingsFile, &data)) {
        return 0;
    }
	Settings_load(&settings);
    
    free(data);
    if (!loadFile(hashFile, &data)) {
        return 0;
    }
    
    FILE* input;
    if(!keyFile) {
        input = stdin;
    } else {
        input = fopen(keyFile, "rb");
        if (!input) {
            printf("Could not open file %s\n", keyFile);
            return 0;
        }
    }

    pos = 0;
    RecSplitEvaluator_load(&evaluator);
    
    char line[255];
    
    // used to measure time of i/o
    // (it turned out to be 50 ns / key)
    /*
    for(int i=0; i<5; i++) {
        clock_t start = clock();
        int64_t sum = 0;
        while (fgets(line, 255, input)) {
            int len = strlen(line);
            // trim
            while (len > 0 && line[len - 1] < ' ') {
                line[--len] = 0;
            }
            sum += len;
        }
        clock_t time = (clock() - start);
        printf("sum: %lld sec: %ld %d\n", sum, time, CLOCKS_PER_SEC);
        rewind(input);
    }
    */
    
    clock_t start = clock();
    int64_t sum = 0;
    while (fgets(line, 255, input)) {
        int len = strlen(line);
        // trim
        while (len > 0 && line[len - 1] < ' ') {
            line[--len] = 0;
        }
        uint64_t index = evaluate(&evaluator, line);
        sum += index;
    }
    clock_t time = (clock() - start);
    // with leafSize 8 and averageBucketSize 128 and 10 million keys,
    // that is 1.79 bits/key, average key length 55 bits/key,
    // evaluate takes about 220 ns/key with -O3, and 260 ns/key with -Os
    
    // with leafSize 6 and averageBucketSize 18 and 10 million keys,
    // that is 2.33 bits/key, average key length 55 bits/key,
    // evaluate takes about 140 ns/key with -O3
    printf("sum: %lld sec: %ld %d\n", sum, time, CLOCKS_PER_SEC);
    
    free(data);
    
    return 0;
}

