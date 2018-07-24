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

int loadFile(char* directory, char* fileName, uint8_t** target) {
    if (!fileName || !directory) {
        printf("No file\n");
        return 0;
    }
    char* fullFileName = malloc(strlen(directory) + strlen(fileName) + 2);
    sprintf(fullFileName, "%s/%s", directory, fileName);
    printf("Loading file %s\n", fullFileName);
    FILE* fp = fopen(fullFileName, "r");
    free(fullFileName);
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
    printf("(%lld bytes)\n", fileSize);
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

// from https://stackoverflow.com/questions/11656241/how-to-print-uint128-t-number-using-gcc/11660651#11660651
typedef unsigned __int128 uint128_t;

// data

uint8_t* data;

uint32_t getArrayLength(uint32_t size) {
    return (uint32_t) (3 + (uint64_t) 123 * size / 100);
}

uint64_t hash64(uint64_t x) {
    x = (x ^ (x >> 30)) * 0xbf58476d1ce4e5b9L;
    x = (x ^ (x >> 27)) * 0x94d049bb133111ebL;
    x = x ^ (x >> 31);
    return x;
}

uint32_t fingerprint(uint64_t hash) {
    return (uint32_t) (hash & ((1 << 8) - 1));
}

// XorFilter8

struct XorFilter8 {
	uint32_t size;
	uint32_t arrayLength;
	uint32_t blockLength;
	uint32_t hashIndex;
	uint8_t* fingerprints;
};

struct XorFilter8 filter;

void XorFilter_load(struct XorFilter8* this) {
    printf("Loading...\n");
    this->size = (data[0] << 24) | (data[1] << 16) | (data[2] << 8) | data[3];
	printf("Size %d\n", this->size);
    this->hashIndex = (data[4] << 24) | (data[5] << 16) | (data[6] << 8) | data[7];
	printf("HashIndex %d\n", this->hashIndex);
    this->arrayLength = getArrayLength(this->size);
    this->blockLength = this->arrayLength / 3;
    this->fingerprints = data + 8;
}

uint32_t XorFilter_mayContain(struct XorFilter8* this, uint64_t key) {
	uint64_t hash = hash64(key + this->hashIndex);
	uint32_t f = fingerprint(hash);
    uint32_t r0 = (uint32_t) hash;
	uint32_t r1 = (uint32_t) (hash >> 16);
    uint32_t r2 = (uint32_t) (hash >> 32);
    uint32_t h0 = reduce(r0, this->blockLength);
    uint32_t h1 = reduce(r1, this->blockLength) + this->blockLength;
    uint32_t h2 = reduce(r2, this->blockLength) + 2 * this->blockLength;
    f ^= this->fingerprints[h0] ^ this->fingerprints[h1] ^ this->fingerprints[h2];
    return (f & 0xff) == 0;
}

// demo

int main(int argc, char** argv) {
    char* directory = ".";
    
    ++argv;
    --argc;
    if (argc > 0) {
        directory = argv[0];
        ++argv;
        --argc;
    }
    printf("Directory %s\n", directory);
    
    char* hashFile = "hash.bin";
    char* keyFile = "keys.txt";
    
    if (!loadFile(directory, hashFile, &data)) {
        return 0;
    }
  
    FILE* input;
    if(!keyFile) {
        input = stdin;
    } else {
        char* fullFileName = malloc(strlen(directory) + strlen(keyFile) + 2);
        sprintf(fullFileName, "%s/%s", directory, keyFile);
        printf("Loading file %s\n", fullFileName);
        input = fopen(fullFileName, "rb");
        free(fullFileName);
        if (!input) {
            printf("Could not open file %s\n", keyFile);
            return 0;
        }
    }    

    XorFilter_load(&filter);
    
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
        uint64_t key = atoll(line);
        uint32_t contain = XorFilter_mayContain(&filter, key);
        sum += contain;
    }
    clock_t time = (clock() - start);
    
    printf("Sum: %lld time: %ld ticks, at %d ticks/second\n", sum, time, CLOCKS_PER_SEC);
    
    free(data);
    
    return 0;
}

