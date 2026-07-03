/* nlp_marshaller.hpp
 *
 * LICENSING NOTE: This file is DERIVED from pagmo2's src/algorithms/ipopt.cpp
 * (struct ipopt_nlp). It is therefore a modified work of pagmo and is licensed under
 * the LGPL-3.0-or-later OR GPL-3.0-or-later (pagmo's dual license) -- NOT the MPL-2.0
 * of the surrounding Pagmo.NET / PagmoNet4j project. See LICENSING.md, "Files derived
 * from pagmo." Do not restamp this file with an MPL header.
 *
 * Original copyright 2017-2021 PaGMO development team (LGPL-3.0 / GPL-3.0 dual).
 *
 * What changed vs. the original ipopt_nlp:
 *   - No IPOPT types or headers. Ipopt::Index -> int, Ipopt::Number -> double; the
 *     Ipopt::TNLP virtuals become plain methods writing int/double buffers ("IPOPT
 *     format" is just int/double arrays). This is what lets the marshaller compile into
 *     the base wrapper with zero build-time IPOPT dependency.
 *   - finalize_solution is dropped: the caller (deferred_ipopt) reads IpoptSolve's
 *     output buffers directly instead.
 *   - Otherwise the sparsity setup and eval_* bodies are ported verbatim.
 */
#ifndef PAGMONET_NLP_MARSHALLER_HPP
#define PAGMONET_NLP_MARSHALLER_HPP

#include <exception>
#include <utility>
#include <vector>

#include <pagmo/problem.hpp>
#include <pagmo/types.hpp>

namespace pagmoNet
{

// One verbosity-log line: (objfun evals, objective value, number of violated
// constraints, norm of the violation, feasibility flag). Mirrors pagmo::ipopt's log line.
struct nlp_log_line {
    unsigned long objevals;
    double objval;
    pagmo::vector_double::size_type nviol;
    double viol_norm;
    bool feasible;
};

// IPOPT-free marshaller between a pagmo::problem and IPOPT's C-interface numerical data.
// Every method mirrors an Ipopt::TNLP callback but speaks plain int/double. Contains no
// IPOPT code, so it compiles into the base wrapper with no build-time IPOPT dependency.
//
// Exception handling mirrors upstream: each method traps everything in a try/catch,
// stores the exception in m_eptr, and returns false. IPOPT does not propagate C++
// exceptions across its callback boundary, so the caller checks the bool and rethrows
// m_eptr after the solve.
class nlp_marshaller
{
public:
    // prob must outlive the marshaller. start is the initial guess (size == prob.get_nx()).
    nlp_marshaller(const pagmo::problem &prob, pagmo::vector_double start, unsigned verbosity);

    nlp_marshaller(const nlp_marshaller &) = delete;
    nlp_marshaller(nlp_marshaller &&) = delete;
    nlp_marshaller &operator=(const nlp_marshaller &) = delete;
    nlp_marshaller &operator=(nlp_marshaller &&) = delete;
    ~nlp_marshaller() = default;

    // --- Problem description (called by us before the solve). ---
    // n, m, nnz(jacobian of constraints), nnz(hessian of lagrangian).
    bool nlp_info(int &n, int &m, int &nnz_jac_g, int &nnz_h_lag);
    // Variable box bounds (x_l/x_u) and constraint bounds (g_l/g_u): eq == [0,0], ineq == [-inf,0].
    bool bounds(int n, double *x_l, double *x_u, int m, double *g_l, double *g_u);
    // Initial guess -> x.
    bool starting_point(int n, double *x);

    // --- Numerical callbacks (invoked BY the solver via the C ABI). ---
    bool eval_f(int n, const double *x, double *obj_value);
    bool eval_grad_f(int n, const double *x, double *grad_f);
    bool eval_g(int n, const double *x, int m, double *g);
    // values == nullptr -> emit sparsity structure into iRow/jCol; else emit values.
    bool eval_jac_g(int n, const double *x, int m, int nele_jac, int *iRow, int *jCol, double *values);
    // values == nullptr -> emit sparsity structure; else accumulate obj_factor*H_f + sum lambda_i*H_gi.
    bool eval_h(int n, const double *x, double obj_factor, int m, const double *lambda, int nele_hess, int *iRow,
                int *jCol, double *values);

    // Exception captured inside a callback (null if none); the caller rethrows after the solve.
    std::exception_ptr take_exception() const { return m_eptr; }
    // Verbosity log accumulated during eval_f.
    const std::vector<nlp_log_line> &log() const { return m_log; }

private:
    const pagmo::problem &m_prob;
    const pagmo::vector_double m_start;
    // Temp decision vector reused for fitness/gradient/hessian evaluations.
    pagmo::vector_double m_dv;
    // Objfun gradient sparsity (only populated when the problem provides gradient sparsity).
    pagmo::sparsity_pattern m_obj_g_sp;
    // Per-component hessian sparsity from pagmo (only when the problem provides hessian sparsity).
    std::vector<pagmo::sparsity_pattern> m_h_sp;
    // Constraint-jacobian sparsity in IPOPT COO format (objfun row stripped, indices as int).
    std::vector<std::pair<int, int>> m_jac_sp;
    // Merged lagrangian-hessian sparsity in IPOPT COO format.
    std::vector<std::pair<int, int>> m_lag_sp;
    const unsigned m_verbosity;
    unsigned long m_objfun_counter = 0;
    std::vector<nlp_log_line> m_log;
    std::exception_ptr m_eptr;
};

} // namespace pagmoNet

#endif // PAGMONET_NLP_MARSHALLER_HPP
