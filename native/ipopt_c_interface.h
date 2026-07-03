/* ipopt_c_interface.h
 *
 * Minimal, hand-written declaration of IPOPT's public C interface (the subset of
 * coin-or/Ipopt's <IpStdCInterface.h> that we call). These are ABI/interface
 * declarations only -- function-pointer typedefs, opaque handles, and stable enum
 * values. There is NO IPOPT code here, so including this file creates no build-time
 * dependency on IPOPT headers or libraries. At runtime we dlopen/LoadLibrary the
 * user's libipopt and resolve these symbols with dlsym/GetProcAddress.
 *
 * This is what keeps the shipped binary free of EPL-2.0 code: we never #include an
 * IPOPT header of substance and never link libipopt.
 *
 * Reference (for reviewers): coin-or/Ipopt, src/Interfaces/IpStdCInterface.h and
 * IpReturnCodes_inc.h. Values below match Ipopt 3.14.x (the conda-forge / vcpkg builds).
 */
#ifndef PAGMONET_IPOPT_C_INTERFACE_H
#define PAGMONET_IPOPT_C_INTERFACE_H

#ifdef __cplusplus
extern "C" {
#endif

/* IPOPT is built with Number == double and Index == int in every distribution we
 * target; the original pagmo wrapper static_asserts the same (ipopt.cpp:128). */
typedef double ipnumber; /* Ipopt::Number */
typedef int ipindex;     /* Ipopt::Index  */
typedef int ipbool;      /* Ipopt::Bool (0/1) */

#define IP_TRUE 1
#define IP_FALSE 0

typedef void *UserDataPtr;
/* Opaque handle -- we never dereference it. */
typedef struct IpoptProblemInfo *IpoptProblem;

/* --- Callback function-pointer types (called BY ipopt, INTO our code) --- */
typedef ipbool (*Eval_F_CB)(ipindex n, ipnumber *x, ipbool new_x, ipnumber *obj_value, UserDataPtr user_data);

typedef ipbool (*Eval_Grad_F_CB)(ipindex n, ipnumber *x, ipbool new_x, ipnumber *grad_f, UserDataPtr user_data);

typedef ipbool (*Eval_G_CB)(ipindex n, ipnumber *x, ipbool new_x, ipindex m, ipnumber *g, UserDataPtr user_data);

typedef ipbool (*Eval_Jac_G_CB)(ipindex n, ipnumber *x, ipbool new_x, ipindex m, ipindex nele_jac, ipindex *iRow,
                                ipindex *jCol, ipnumber *values, UserDataPtr user_data);

typedef ipbool (*Eval_H_CB)(ipindex n, ipnumber *x, ipbool new_x, ipnumber obj_factor, ipindex m, ipnumber *lambda,
                            ipbool new_lambda, ipindex nele_hess, ipindex *iRow, ipindex *jCol, ipnumber *values,
                            UserDataPtr user_data);

/* Optional progress callback -- we do not use it (logging is driven from eval_f,
 * exactly as upstream pagmo does), but the typedef is here for completeness. */
typedef ipbool (*Intermediate_CB)(ipindex alg_mod, ipindex iter_count, ipnumber obj_value, ipnumber inf_pr,
                                  ipnumber inf_du, ipnumber mu, ipnumber d_norm, ipnumber regularization_size,
                                  ipnumber alpha_du, ipnumber alpha_pr, ipindex ls_trials, UserDataPtr user_data);

/* --- Entry points (resolved via dlsym; called BY our code, INTO ipopt) --- */
typedef IpoptProblem (*CreateIpoptProblem_t)(ipindex n, ipnumber *x_L, ipnumber *x_U, ipindex m, ipnumber *g_L,
                                             ipnumber *g_U, ipindex nele_jac, ipindex nele_hess, ipindex index_style,
                                             Eval_F_CB eval_f, Eval_G_CB eval_g, Eval_Grad_F_CB eval_grad_f,
                                             Eval_Jac_G_CB eval_jac_g, Eval_H_CB eval_h);

typedef void (*FreeIpoptProblem_t)(IpoptProblem ipopt_problem);

typedef ipbool (*AddIpoptStrOption_t)(IpoptProblem ipopt_problem, const char *keyword, const char *val);
typedef ipbool (*AddIpoptNumOption_t)(IpoptProblem ipopt_problem, const char *keyword, ipnumber val);
typedef ipbool (*AddIpoptIntOption_t)(IpoptProblem ipopt_problem, const char *keyword, ipindex val);

typedef ipbool (*SetIntermediateCallback_t)(IpoptProblem ipopt_problem, Intermediate_CB intermediate_cb);

typedef ipindex (*IpoptSolve_t)(IpoptProblem ipopt_problem, ipnumber *x, ipnumber *g, ipnumber *obj_val,
                                ipnumber *mult_g, ipnumber *mult_x_L, ipnumber *mult_x_U, UserDataPtr user_data);

/* --- ApplicationReturnStatus values (IpReturnCodes_inc.h) ---
 * IpoptSolve returns one of these as an int. Kept as an anonymous enum so we do not
 * depend on IPOPT's header for the type. */
enum {
    IP_Solve_Succeeded = 0,
    IP_Solved_To_Acceptable_Level = 1,
    IP_Infeasible_Problem_Detected = 2,
    IP_Search_Direction_Becomes_Too_Small = 3,
    IP_Diverging_Iterates = 4,
    IP_User_Requested_Stop = 5,
    IP_Feasible_Point_Found = 6,
    IP_Maximum_Iterations_Exceeded = -1,
    IP_Restoration_Failed = -2,
    IP_Error_In_Step_Computation = -3,
    IP_Maximum_CpuTime_Exceeded = -4,
    IP_Maximum_WallTime_Exceeded = -5,
    IP_Not_Enough_Degrees_Of_Freedom = -10,
    IP_Invalid_Problem_Definition = -11,
    IP_Invalid_Option = -12,
    IP_Invalid_Number_Detected = -13,
    IP_Unrecoverable_Exception = -100,
    IP_NonIpopt_Exception_Thrown = -101,
    IP_Insufficient_Memory = -102,
    IP_Internal_Error = -199
};

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* PAGMONET_IPOPT_C_INTERFACE_H */
