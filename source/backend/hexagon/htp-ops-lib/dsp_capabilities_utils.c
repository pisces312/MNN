#include "dsp_capabilities_utils.h"
#include <stddef.h>

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
