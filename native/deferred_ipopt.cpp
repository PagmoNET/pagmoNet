/* deferred_ipopt.cpp
 *
 * LICENSING NOTE: the dlopen loader wiring and C-ABI trampolines are original work
 * (MPL-2.0). The evolve() option-defaulting and select/solve/replace scaffolding are
 * adapted from pagmo2's ipopt.cpp (LGPL-3.0 / GPL-3.0). See deferred_ipopt.hpp and
 * LICENSING.md.
 */
#include "deferred_ipopt.hpp"

#include <algorithm>
#include <cmath>
#include <exception>
#include <stdexcept>
#include <string>
#include <vector>

#include <pagmo/exceptions.hpp>
#include <pagmo/problem.hpp>
#include <pagmo/types.hpp>
#include <pagmo/utils/constrained.hpp>

#include "dynamic_library.hpp"
#include "ipopt_c_interface.h"
#include "nlp_marshaller.hpp"

namespace pagmoNet
{

namespace
{

// Resolved entry points into whatever libipopt we loaded. Loaded once, lazily, and kept
// resident for the process lifetime (function pointers stay valid).
struct ipopt_api {
    dynamic_library lib;
    CreateIpoptProblem_t CreateIpoptProblem;
    FreeIpoptProblem_t FreeIpoptProblem;
    AddIpoptStrOption_t AddIpoptStrOption;
    AddIpoptNumOption_t AddIpoptNumOption;
    AddIpoptIntOption_t AddIpoptIntOption;
    IpoptSolve_t IpoptSolve;

    ipopt_api()
        : lib("PAGMONET_IPOPT_LIBRARY",
              {
#if defined(_WIN32)
                  "ipopt-3.dll", "ipopt.dll", "libipopt-3.dll", "libipopt.dll",
#elif defined(__APPLE__)
                  "libipopt.dylib", "libipopt.3.dylib",
#else
                  "libipopt.so", "libipopt.so.3", "libipopt.so.1",
#endif
              },
              "IPOPT",
              "Add the companion package (Pagmo.NET.Ipopt / PagmoNet4j.ipopt), or install IPOPT on your system "
              "(conda-forge 'ipopt', apt 'coinor-libipopt-dev', brew 'ipopt'), or set PAGMONET_IPOPT_LIBRARY to "
              "the full path of a libipopt shared library.")
    {
        CreateIpoptProblem = lib.symbol_as<CreateIpoptProblem_t>("CreateIpoptProblem");
        FreeIpoptProblem = lib.symbol_as<FreeIpoptProblem_t>("FreeIpoptProblem");
        AddIpoptStrOption = lib.symbol_as<AddIpoptStrOption_t>("AddIpoptStrOption");
        AddIpoptNumOption = lib.symbol_as<AddIpoptNumOption_t>("AddIpoptNumOption");
        AddIpoptIntOption = lib.symbol_as<AddIpoptIntOption_t>("AddIpoptIntOption");
        IpoptSolve = lib.symbol_as<IpoptSolve_t>("IpoptSolve");
    }
};

const ipopt_api &get_ipopt_api()
{
    static ipopt_api api; // constructed (and libipopt loaded) on first use
    return api;
}

// --- C-ABI trampolines: cast the user-data pointer back to the marshaller and forward. ---
extern "C" {

ipbool eval_f_tramp(ipindex n, ipnumber *x, ipbool, ipnumber *obj_value, UserDataPtr user_data)
{
    auto *nlp = static_cast<nlp_marshaller *>(user_data);
    return nlp->eval_f(n, x, obj_value) ? IP_TRUE : IP_FALSE;
}

ipbool eval_grad_f_tramp(ipindex n, ipnumber *x, ipbool, ipnumber *grad_f, UserDataPtr user_data)
{
    auto *nlp = static_cast<nlp_marshaller *>(user_data);
    return nlp->eval_grad_f(n, x, grad_f) ? IP_TRUE : IP_FALSE;
}

ipbool eval_g_tramp(ipindex n, ipnumber *x, ipbool, ipindex m, ipnumber *g, UserDataPtr user_data)
{
    auto *nlp = static_cast<nlp_marshaller *>(user_data);
    return nlp->eval_g(n, x, m, g) ? IP_TRUE : IP_FALSE;
}

ipbool eval_jac_g_tramp(ipindex n, ipnumber *x, ipbool, ipindex m, ipindex nele_jac, ipindex *iRow, ipindex *jCol,
                        ipnumber *values, UserDataPtr user_data)
{
    auto *nlp = static_cast<nlp_marshaller *>(user_data);
    return nlp->eval_jac_g(n, x, m, nele_jac, iRow, jCol, values) ? IP_TRUE : IP_FALSE;
}

ipbool eval_h_tramp(ipindex n, ipnumber *x, ipbool, ipnumber obj_factor, ipindex m, ipnumber *lambda, ipbool,
                    ipindex nele_hess, ipindex *iRow, ipindex *jCol, ipnumber *values, UserDataPtr user_data)
{
    auto *nlp = static_cast<nlp_marshaller *>(user_data);
    return nlp->eval_h(n, x, obj_factor, m, lambda, nele_hess, iRow, jCol, values) ? IP_TRUE : IP_FALSE;
}

} // extern "C"

// RAII for the IpoptProblem handle.
struct problem_guard {
    const ipopt_api &api;
    IpoptProblem problem;
    ~problem_guard()
    {
        if (problem != nullptr) {
            api.FreeIpoptProblem(problem);
        }
    }
};

} // namespace

void deferred_ipopt::set_string_options(const std::map<std::string, std::string> &m)
{
    for (const auto &p : m) {
        set_string_option(p.first, p.second);
    }
}

void deferred_ipopt::set_integer_options(const std::map<std::string, int> &m)
{
    for (const auto &p : m) {
        set_integer_option(p.first, p.second);
    }
}

void deferred_ipopt::set_numeric_options(const std::map<std::string, double> &m)
{
    for (const auto &p : m) {
        set_numeric_option(p.first, p.second);
    }
}

bool ipopt_available() noexcept
{
    try {
        (void)get_ipopt_api(); // loads libipopt (and caches it) if resolvable
        return true;
    } catch (...) {
        return false;
    }
}

std::string deferred_ipopt::get_extra_info() const
{
    std::string s = "\tLast optimisation return code: " + std::to_string(m_last_opt_res)
                    + "\n\tVerbosity: " + std::to_string(m_verbosity);
    if (!m_string_opts.empty()) {
        s += "\n\tString options: " + std::to_string(m_string_opts.size());
    }
    if (!m_integer_opts.empty()) {
        s += "\n\tInteger options: " + std::to_string(m_integer_opts.size());
    }
    if (!m_numeric_opts.empty()) {
        s += "\n\tNumeric options: " + std::to_string(m_numeric_opts.size());
    }
    return s + "\n";
}

pagmo::population deferred_ipopt::evolve(pagmo::population pop) const
{
    if (!pop.size()) {
        return pop;
    }

    auto &prob = pop.get_problem();

    // Select the individual to optimise; keep its original fitness for the replace-if-better test.
    auto sel_xf = select_individual(pop);
    pagmo::vector_double initial_guess(std::move(sel_xf.first)), old_f(std::move(sel_xf.second));

    // Validate the initial guess (NaN / out of bounds).
    const auto bounds = prob.get_bounds();
    for (decltype(bounds.first.size()) i = 0; i < bounds.first.size(); ++i) {
        if (std::isnan(initial_guess[i])) {
            pagmo_throw(std::invalid_argument,
                        "the value of the initial guess at index " + std::to_string(i) + " is NaN");
        }
        if (initial_guess[i] < bounds.first[i] || initial_guess[i] > bounds.second[i]) {
            pagmo_throw(std::invalid_argument,
                        "the value of the initial guess at index " + std::to_string(i) + " is outside the problem's bounds");
        }
    }

    // Load libipopt (throws an actionable error if none is available).
    const ipopt_api &api = get_ipopt_api();

    // Build the marshaller and query the problem description.
    nlp_marshaller nlp(prob, initial_guess, m_verbosity);
    int n = 0, m = 0, nnz_jac_g = 0, nnz_h_lag = 0;
    if (!nlp.nlp_info(n, m, nnz_jac_g, nnz_h_lag)) {
        std::rethrow_exception(nlp.take_exception());
    }
    std::vector<double> x_l(static_cast<std::size_t>(n)), x_u(static_cast<std::size_t>(n));
    std::vector<double> g_l(static_cast<std::size_t>(m)), g_u(static_cast<std::size_t>(m));
    if (!nlp.bounds(n, x_l.data(), x_u.data(), m, g_l.data(), g_u.data())) {
        std::rethrow_exception(nlp.take_exception());
    }

    // Create the problem. NOTE the C-interface callback order: eval_f, eval_g, eval_grad_f,
    // eval_jac_g, eval_h (eval_g comes BEFORE eval_grad_f).
    IpoptProblem problem = api.CreateIpoptProblem(n, x_l.data(), x_u.data(), m, g_l.data(), g_u.data(), nnz_jac_g,
                                                  nnz_h_lag, 0 /* C-style (0-based) indexing */, &eval_f_tramp,
                                                  &eval_g_tramp, &eval_grad_f_tramp, &eval_jac_g_tramp, &eval_h_tramp);
    if (problem == nullptr) {
        pagmo_throw(std::runtime_error, "IPOPT's CreateIpoptProblem() failed");
    }
    problem_guard guard{api, problem};

    auto set_str = [&](const std::string &k, const std::string &v) {
        if (!api.AddIpoptStrOption(problem, k.c_str(), v.c_str())) {
            pagmo_throw(std::invalid_argument, "failed to set the ipopt string option '" + k + "' to '" + v + "'");
        }
    };
    auto set_num = [&](const std::string &k, double v) {
        if (!api.AddIpoptNumOption(problem, k.c_str(), v)) {
            pagmo_throw(std::invalid_argument, "failed to set the ipopt numeric option '" + k + "'");
        }
    };
    auto set_int = [&](const std::string &k, int v) {
        if (!api.AddIpoptIntOption(problem, k.c_str(), v)) {
            pagmo_throw(std::invalid_argument, "failed to set the ipopt integer option '" + k + "'");
        }
    };

    // constr_viol_tol: default to the smallest positive problem tolerance, unless the user set it.
    if (prob.get_nc() && !m_numeric_opts.count("constr_viol_tol")) {
        const auto c_tol = prob.get_c_tol();
        const double min_tol = *std::min_element(c_tol.begin(), c_tol.end());
        if (min_tol > 0.) {
            set_num("constr_viol_tol", min_tol);
        }
    }
    // No hessians provided and not overridden -> quasi-Newton, so problems work out of the box.
    if (!prob.has_hessians() && !m_string_opts.count("hessian_approximation")) {
        set_str("hessian_approximation", "limited-memory");
    }
    // Default print_level to 0 (quiet) unless the user set it.
    if (!m_integer_opts.count("print_level")) {
        set_int("print_level", 0);
    }
    // User-provided options.
    for (const auto &p : m_string_opts) {
        set_str(p.first, p.second);
    }
    for (const auto &p : m_numeric_opts) {
        set_num(p.first, p.second);
    }
    for (const auto &p : m_integer_opts) {
        set_int(p.first, p.second);
    }

    // Starting point (IpoptSolve reads and overwrites x with the solution) + output buffers.
    std::vector<double> x(static_cast<std::size_t>(n));
    if (!nlp.starting_point(n, x.data())) {
        std::rethrow_exception(nlp.take_exception());
    }
    std::vector<double> g(static_cast<std::size_t>(m));
    double obj_value = 0.;

    // Solve.
    m_last_opt_res = api.IpoptSolve(problem, x.data(), g.empty() ? nullptr : g.data(), &obj_value, nullptr, nullptr,
                                    nullptr, static_cast<UserDataPtr>(&nlp));

    // Rethrow any exception trapped inside a callback during the solve.
    if (auto eptr = nlp.take_exception()) {
        std::rethrow_exception(eptr);
    }

    // Copy the verbosity log out of the marshaller.
    m_log.clear();
    for (const auto &ln : nlp.log()) {
        m_log.emplace_back(ln.objevals, ln.objval, ln.nviol, ln.viol_norm, ln.feasible);
    }

    // Replace the selected individual only if the optimised one is better.
    const auto new_f = prob.fitness(x);
    if (pagmo::compare_fc(new_f, old_f, prob.get_nec(), prob.get_c_tol())) {
        replace_individual(pop, x, new_f);
    }

    return pop;
}

} // namespace pagmoNet

PAGMO_S11N_ALGORITHM_IMPLEMENT(pagmoNet::deferred_ipopt)
