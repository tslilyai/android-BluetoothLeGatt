APP_ABI := armeabi-v7a
APP_CFLAGS += -O3 -ftree-vectorize -fopenmp
APP_CPPFLAGS += -fexceptions -std=gnu++11 -O3 -Wno-literal-range -ftree-vectorize -fopenmp
APP_GNUSTL_FORCE_CPP_FEATURES := exceptions rtti
APP_STL := gnustl_shared

COMMON_SOURCE_FILES += source/Address.cpp
COMMON_SOURCE_FILES += source/Base64.cpp
COMMON_SOURCE_FILES += source/BinaryToUTF8.cpp
COMMON_SOURCE_FILES += source/BitMap.cpp
COMMON_SOURCE_FILES += source/EbNDevice.cpp
COMMON_SOURCE_FILES += source/EbNHystPolicy.cpp
COMMON_SOURCE_FILES += source/ECDH.cpp
COMMON_SOURCE_FILES += source/Logger.cpp
COMMON_SOURCE_FILES += source/RSErasureDecoder.cpp
COMMON_SOURCE_FILES += source/RSMatrix.cpp
COMMON_SOURCE_FILES += source/RSErasureEncoder.cpp
COMMON_SOURCE_FILES += source/SharedArray.cpp
COMMON_SOURCE_FILES += source/SipHash.cpp
COMMON_SOURCE_FILES += source/jerasure/galois.c
COMMON_SOURCE_FILES += source/jerasure/jerasure.c
COMMON_SOURCE_FILES += source/jerasure/reed_sol.c
COMMON_SOURCE_FILES += source/BloomFilter.cpp
COMMON_SOURCE_FILES += source/SegmentedBloomFilter.cpp
COMMON_SOURCE_FILES += source/SDDRRadio.cpp
EXECUTABLE_SOURCE_FILES += c_SDDRRadio.cpp
EXECUTABLE_SOURCE_FILES += source/sddr.pb.cc
