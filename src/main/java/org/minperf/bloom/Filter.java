package org.minperf.bloom;

public interface Filter {

    boolean mayContain(long key);
    long getBitCount();

    public enum Type {
        XOR8 {
            @Override
            public Filter construct(long[] keys, int setting) {
                return XorFilter_8bit.construct(keys);
            }
        },
        BLOOM {
            @Override
            public Filter construct(long[] keys, int setting) {
                return BloomFilter.construct(keys, setting);
            }
        },
        CUCKOO16_4 {
            @Override
            public Filter construct(long[] keys, int setting) {
                return CuckooFilter_16bit_4entries.construct(keys);
            }
        },
        CUCKOO8_4 {
            @Override
            public Filter construct(long[] keys, int setting) {
                return CuckooFilter_8bit_4entries.construct(keys);
            }
        },
        XOR16 {
            @Override
            public Filter construct(long[] keys, int setting) {
                return XorFilter_16bit.construct(keys);
            }
        },
        XOR {
            @Override
            public Filter construct(long[] keys, int bitsPerKey) {
                return XorFilter.construct(keys, bitsPerKey);
            }
        },
        CUCKOO {
            @Override
            public Filter construct(long[] keys, int setting) {
                return CuckooFilter.construct(keys, setting);
            }
        },
//        MPHF {
//            @Override
//            Filter construct(long[] keys, int setting) {
//                return MPHFilter.construct(keys, setting);
//            }
//        };
        ;

        /**
         * Construct the filter with the given keys and the setting.
         *
         * @param keys the keys
         * @param setting the setting (roughly bits per fingerprint)
         * @return the constructed filter
         */
        public abstract Filter construct(long[] keys, int setting);

    }

}
