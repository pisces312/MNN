#pragma once

#include <stdint.h>
#include <stdlib.h>

#ifdef __cplusplus
extern "C" {
#endif

// see hexagon SDK remote.h
/** Defines the domain IDs for supported DSPs */
#define ADSP_DOMAIN_ID    0
#define MDSP_DOMAIN_ID    1
#define SDSP_DOMAIN_ID    2
#define CDSP_DOMAIN_ID    3
#define CDSP1_DOMAIN_ID   4

// rpcmem
// NOTE: the QNN backend (source/backend/qnn/backend/dsprpc_interface.cc)
// exports the same libcdsprpc wrapper symbols. To avoid duplicate-symbol
// link errors when MNN_HEXAGON and MNN_QNN are both enabled, the hexagon
// backend prefixes its wrappers with mnn_hexagon_. The macro aliases below
// keep call sites unchanged; dlsym() still resolves the real libcdsprpc
// names at runtime.
void   mnn_hexagon_rpcmem_init(void);
void   mnn_hexagon_rpcmem_deinit(void);
void * mnn_hexagon_rpcmem_alloc(int heap_id, uint32_t flags, int size);
void   mnn_hexagon_rpcmem_free(void * p);
int    mnn_hexagon_rpcmem_to_fd(void * p);
int    mnn_hexagon_rpcmem_cache_flush(void * po, int size);
int    mnn_hexagon_rpcmem_cache_invalidate(void * po, int size);

#define rpcmem_init              mnn_hexagon_rpcmem_init
#define rpcmem_deinit            mnn_hexagon_rpcmem_deinit
#define rpcmem_alloc             mnn_hexagon_rpcmem_alloc
#define rpcmem_free              mnn_hexagon_rpcmem_free
#define rpcmem_to_fd             mnn_hexagon_rpcmem_to_fd
#define rpcmem_cache_flush       mnn_hexagon_rpcmem_cache_flush
#define rpcmem_cache_invalidate  mnn_hexagon_rpcmem_cache_invalidate

#define RPCMEM_FLAG_UNCACHED 0
#define RPCMEM_FLAG_CACHED   1  // Allocate memory with the same properties as the ION_FLAG_CACHED flag

enum rpc_heap_ids {
    RPCMEM_HEAP_ID_SECURE = 9,
    RPCMEM_HEAP_ID_CONTIG = 22,
    RPCMEM_HEAP_ID_SYSTEM = 25,
};

// fastrpc mmap & munmap
/**
 * @enum fastrpc_map_flags for fastrpc_mmap and fastrpc_munmap
 * @brief Types of maps with cache maintenance
 */
enum fastrpc_map_flags {
    /**
     * Map memory pages with RW- permission and CACHE WRITEBACK.
     * Driver will clean cache when buffer passed in a FastRPC call.
     * Same remote virtual address will be assigned for subsequent
     * FastRPC calls.
     */
    FASTRPC_MAP_STATIC,

    /** Reserved for compatibility with deprecated flag */
    FASTRPC_MAP_RESERVED,

    /**
     * Map memory pages with RW- permission and CACHE WRITEBACK.
     * Mapping tagged with a file descriptor. User is responsible for
     * maintenance of CPU and DSP caches for the buffer. Get virtual address
     * of buffer on DSP using HAP_mmap_get() and HAP_mmap_put() functions.
     */
    FASTRPC_MAP_FD,

    /**
     * Mapping delayed until user calls HAP_mmap() and HAP_munmap()
     * functions on DSP. User is responsible for maintenance of CPU and DSP
     * caches for the buffer. Delayed mapping is useful for users to map
     * buffer on DSP with other than default permissions and cache modes
     * using HAP_mmap() and HAP_munmap() functions.
     */
    FASTRPC_MAP_FD_DELAYED,

    /** Reserved for compatibility **/
    FASTRPC_MAP_RESERVED_4,
    FASTRPC_MAP_RESERVED_5,
    FASTRPC_MAP_RESERVED_6,
    FASTRPC_MAP_RESERVED_7,
    FASTRPC_MAP_RESERVED_8,
    FASTRPC_MAP_RESERVED_9,
    FASTRPC_MAP_RESERVED_10,
    FASTRPC_MAP_RESERVED_11,
    FASTRPC_MAP_RESERVED_12,
    FASTRPC_MAP_RESERVED_13,
    FASTRPC_MAP_RESERVED_14,
    FASTRPC_MAP_RESERVED_15,

    /**
     * This flag is used to skip CPU mapping,
     * otherwise behaves similar to FASTRPC_MAP_FD_DELAYED flag.
     */
    FASTRPC_MAP_FD_NOMAP,

    /** Update FASTRPC_MAP_MAX when adding new value to this enum **/
};

int mnn_hexagon_fastrpc_mmap(int domain, int fd, void * addr, int offset, size_t length, enum fastrpc_map_flags flags);
int mnn_hexagon_fastrpc_munmap(int domain, int fd, void * addr, size_t length);

#define fastrpc_mmap   mnn_hexagon_fastrpc_mmap
#define fastrpc_munmap mnn_hexagon_fastrpc_munmap

#ifdef __cplusplus
}
#endif
