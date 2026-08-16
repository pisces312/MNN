#include "dsp_capabilities_utils.h"
#include <stddef.h>
#include <stdint.h>
#include <AEEStdErr.h>

/* supported_domains[] is defined (not just declared) by the SDK header; keep
 * the inclusion in this single translation unit to avoid duplicate symbols. */
#include "domain_default.h"

domain* get_domain(int domain_id) {
    int num = (int)(sizeof(supported_domains) / sizeof(supported_domains[0]));
    for (int i = 0; i < num; i++) {
        if (supported_domains[i].id == domain_id) {
            return &supported_domains[i];
        }
    }
    return NULL;
}

int get_domains_info(int* domain_type_info, int* num_domains, domain** domains_info) {
    if (num_domains == NULL || domains_info == NULL) {
        return -1;
    }
    *num_domains = (int)(sizeof(supported_domains) / sizeof(supported_domains[0]));
    *domains_info = supported_domains;
    if (domain_type_info) {
        *domain_type_info = 0;
    }
    return 0;
}

int get_hex_arch_ver(int domain_id, uint32_t* capability) {
    fastrpc_capability cap;
    cap.domain = (uint32_t)domain_id;
    cap.attribute_ID = ARCH_VER;
    cap.capability = 0;
    int err = remote_handle_control(DSPRPC_GET_DSP_INFO, &cap, (uint32_t)sizeof(cap));
    if (err == AEE_SUCCESS && capability != NULL) {
        *capability = cap.capability;
    }
    return err;
}
