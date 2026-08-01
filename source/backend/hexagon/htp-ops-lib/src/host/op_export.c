#include "host/op_export.h"
#include "host/session.h"
#include "htp_ops.h"

static int htp_ops_rpc_handle_invalid() {
    remote_handle64 handle = get_global_handle();
    return handle == 0 || handle == (remote_handle64)-1;
}

int htp_ops_rpc_init_backend() {
    return htp_ops_init_backend(get_global_handle());
}

int htp_ops_rpc_getInfo(int fd, int offset) {
    if (htp_ops_rpc_handle_invalid()) return -1;
    return htp_ops_getInfo(get_global_handle(), fd, offset);
}

int htp_ops_rpc_getInfoProfile(int fd, int offset) {
    if (htp_ops_rpc_handle_invalid()) return -1;
    return htp_ops_getInfoProfile(get_global_handle(), fd, offset);
}

int htp_ops_rpc_getDiag(int *hvx_units, int *vtcm_size, int *hvx_arch,
                        int *rc_apptype, int *rc_dcvs_v3, int *rc_bus_prot,
                        int *rc_ddr_perf, int *rc_hvx, int *rc_hmx,
                        int *prot_compiled, int *max_workers, int *pool_ok) {
    if (htp_ops_rpc_handle_invalid()) return -1;
    return htp_ops_getDiag(get_global_handle(),
                           hvx_units, vtcm_size, hvx_arch,
                           rc_apptype, rc_dcvs_v3, rc_bus_prot,
                           rc_ddr_perf, rc_hvx, rc_hmx,
                           prot_compiled, max_workers, pool_ok);
}

int htp_rpc_execute_command_group(int groupFd, int groupOffset, int count, int syncGroupFd, int syncGroupOffset, int syncGroupSize) {
    if (htp_ops_rpc_handle_invalid()) return -1;
    return htp_dspqueue_execute_command_group(groupFd, groupOffset, count, syncGroupFd, syncGroupOffset, syncGroupSize);
}

int htp_rpc_execute_command_group_profile(int groupFd, int groupOffset, int count, int syncGroupFd, int syncGroupOffset, int syncGroupSize, int profileFd, int profileOffset, int profileSize) {
    if (htp_ops_rpc_handle_invalid()) return -1;
    return htp_dspqueue_execute_command_group_profile(groupFd, groupOffset, count, syncGroupFd, syncGroupOffset, syncGroupSize, profileFd, profileOffset, profileSize);
}
