#ifndef DSP_CAPABILITIES_UTILS_H
#define DSP_CAPABILITIES_UTILS_H

#include "remote.h"
#include <stdbool.h>

/* NOTE: do not include domain_default.h here - it defines (not just declares)
 * supported_domains[] / is_CDSP() in newer SDKs, causing duplicate symbols at
 * link time when included from multiple translation units. It is included only
 * in dsp_capabilities_utils.c. */

/* Returns pointer to domain struct for given domain_id, or NULL if not found */
domain* get_domain(int domain_id);

/* Returns number of supported domains */
int get_domains_info(int* domain_type_info, int* num_domains, domain** domains_info);

/* Queries the Hexagon arch version (e.g. 0x81 for v81) via
 * remote_handle_control(DSPRPC_GET_DSP_INFO, ARCH_VER). Returns AEE_SUCCESS on
 * success. Implemented locally in dsp_capabilities_utils.c: upstream session.c
 * expects this helper from newer Hexagon SDKs' utils/examples, but SDK 6.6 does
 * not provide it. */
int get_hex_arch_ver(int domain_id, uint32_t* capability);

#endif /* DSP_CAPABILITIES_UTILS_H */
