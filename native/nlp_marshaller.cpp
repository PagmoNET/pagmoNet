/* nlp_marshaller.cpp
 *
 * LICENSING NOTE: derived from pagmo2's src/algorithms/ipopt.cpp -> LGPL-3.0-or-later /
 * GPL-3.0-or-later, NOT MPL-2.0. See nlp_marshaller.hpp and LICENSING.md.
 */
#include "nlp_marshaller.hpp"

#include <algorithm>
#include <cassert>
#include <exception>
#include <iomanip>
#include <iterator>
#include <limits>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

#include <boost/numeric/conversion/cast.hpp>

#include <pagmo/exceptions.hpp>
#include <pagmo/io.hpp>
#include <pagmo/problem.hpp>
#include <pagmo/types.hpp>
#include <pagmo/utils/constrained.hpp>

namespace pagmoNet
{

using pagmo::sparsity_pattern;
using pagmo::vector_double;

nlp_marshaller::nlp_marshaller(const pagmo::problem &prob, vector_double start, unsigned verbosity)
    : m_prob(prob), m_start(std::move(start)), m_verbosity(verbosity)
{
    assert(m_start.size() == prob.get_nx());

    // Single-objective only.
    if (m_prob.get_nobj() > 1u) {
        pagmo_throw(std::invalid_argument,
                    std::to_string(m_prob.get_nobj()) + " objectives were detected in the input problem named '"
                        + m_prob.get_name()
                        + "', but the ipopt algorithm can solve only single-objective problems");
    }

    // We need the gradient.
    if (!m_prob.has_gradient()) {
        pagmo_throw(std::invalid_argument, "the ipopt algorithm needs the gradient, but the problem named '"
                                               + m_prob.get_name() + "' does not provide it");
    }

    // Prepare the dv used for fitness computation.
    m_dv.resize(m_start.size());

    // Gradient sparsity -> Ipopt jacobian format. Our gradient sparsity includes the objfun's
    // gradient (first index 0); Ipopt's jacobian holds only constraints. So drop the objfun rows
    // and decrement the first index of the remaining pairs by one.
    {
        const auto sp = prob.gradient_sparsity();
        const auto it = std::lower_bound(sp.begin(), sp.end(), sparsity_pattern::value_type(1u, 0u));
        std::transform(it, sp.end(), std::back_inserter(m_jac_sp), [](const sparsity_pattern::value_type &p) {
            return std::make_pair(boost::numeric_cast<int>(p.first - 1u), boost::numeric_cast<int>(p.second));
        });
        if (m_prob.has_gradient_sparsity()) {
            // Store the objfun gradient sparsity, if user-provided (needed by eval_grad_f).
            std::copy(sp.begin(), it, std::back_inserter(m_obj_g_sp));
        }
    }

    // Hessian sparsity -> single merged lagrangian pattern in Ipopt format. pagmo provides a
    // separate pattern per component (objfun + each constraint); Ipopt wants one pattern valid
    // for all of them, so merge via set_union.
    {
        sparsity_pattern merged_sp;
        if (m_prob.has_hessians_sparsity()) {
            m_h_sp = m_prob.hessians_sparsity();
            for (const auto &sp : m_h_sp) {
                // set_union() needs distinct ranges, so copy the accumulator each time.
                const auto old_merged_sp(merged_sp);
                merged_sp.clear();
                std::set_union(old_merged_sp.begin(), old_merged_sp.end(), sp.begin(), sp.end(),
                               std::back_inserter(merged_sp));
            }
        } else {
            // Not user-provided: all patterns are dense and identical; avoid materializing them.
            merged_sp = pagmo::detail::dense_hessian(m_prob.get_nx());
        }
        std::transform(merged_sp.begin(), merged_sp.end(), std::back_inserter(m_lag_sp),
                       [](const sparsity_pattern::value_type &p) {
                           return std::make_pair(boost::numeric_cast<int>(p.first),
                                                 boost::numeric_cast<int>(p.second));
                       });
    }
}

bool nlp_marshaller::nlp_info(int &n, int &m, int &nnz_jac_g, int &nnz_h_lag)
{
    try {
        n = boost::numeric_cast<int>(m_prob.get_nx());
        m = boost::numeric_cast<int>(m_prob.get_nc());
        nnz_jac_g = boost::numeric_cast<int>(m_jac_sp.size());
        nnz_h_lag = boost::numeric_cast<int>(m_lag_sp.size());
        return true;
    } catch (...) {
        m_eptr = std::current_exception();
        return false;
    }
}

bool nlp_marshaller::bounds(int n, double *x_l, double *x_u, int m, double *g_l, double *g_u)
{
    try {
        assert(n == boost::numeric_cast<int>(m_prob.get_nx()));
        assert(m == boost::numeric_cast<int>(m_prob.get_nc()));
        (void)n;
        (void)m;

        const auto bnds = m_prob.get_bounds();
        std::copy(bnds.first.begin(), bnds.first.end(), x_l);
        std::copy(bnds.second.begin(), bnds.second.end(), x_u);

        // Equality constraints: lb == ub == 0.
        std::fill(g_l, g_l + m_prob.get_nec(), 0.);
        std::fill(g_u, g_u + m_prob.get_nec(), 0.);

        // Inequality constraints: lb == -inf, ub == 0.
        std::fill(g_l + m_prob.get_nec(), g_l + m_prob.get_nc(),
                  std::numeric_limits<double>::has_infinity ? -std::numeric_limits<double>::infinity()
                                                            : std::numeric_limits<double>::lowest());
        std::fill(g_u + m_prob.get_nec(), g_u + m_prob.get_nc(), 0.);

        return true;
    } catch (...) {
        m_eptr = std::current_exception();
        return false;
    }
}

bool nlp_marshaller::starting_point(int n, double *x)
{
    try {
        assert(n == boost::numeric_cast<int>(m_prob.get_nx()));
        assert(n == boost::numeric_cast<int>(m_start.size()));
        (void)n;
        std::copy(m_start.begin(), m_start.end(), x);
        return true;
    } catch (...) {
        m_eptr = std::current_exception();
        return false;
    }
}

bool nlp_marshaller::eval_f(int n, const double *x, double *obj_value)
{
    try {
        assert(n == boost::numeric_cast<int>(m_prob.get_nx()));

        std::copy(x, x + n, m_dv.begin());
        const auto fitness = m_prob.fitness(m_dv);
        *obj_value = fitness[0];

        // Update the log if requested.
        if (m_verbosity && !(m_objfun_counter % m_verbosity)) {
            const auto ctol = m_prob.get_c_tol();
            const auto c1eq = pagmo::detail::test_eq_constraints(fitness.data() + 1,
                                                                 fitness.data() + 1 + m_prob.get_nec(), ctol.data());
            const auto c1ineq = pagmo::detail::test_ineq_constraints(
                fitness.data() + 1 + m_prob.get_nec(), fitness.data() + fitness.size(), ctol.data() + m_prob.get_nec());
            // Total number of violated constraints.
            const auto nv = m_prob.get_nc() - c1eq.first - c1ineq.first;
            // Norm of the violation.
            const auto l = c1eq.second + c1ineq.second;
            // Feasibility.
            const auto feas = m_prob.feasibility_f(fitness);

            if (!(m_objfun_counter / m_verbosity % 50u)) {
                pagmo::print("\n", std::setw(10), "objevals:", std::setw(15), "objval:", std::setw(15),
                             "violated:", std::setw(15), "viol. norm:", '\n');
            }
            pagmo::print(std::setw(10), m_objfun_counter + 1u, std::setw(15), *obj_value, std::setw(15), nv,
                         std::setw(15), l, feas ? "" : " i", '\n');
            m_log.push_back(nlp_log_line{m_objfun_counter + 1u, *obj_value, nv, l, feas});
        }

        ++m_objfun_counter;
        return true;
    } catch (...) {
        m_eptr = std::current_exception();
        return false;
    }
}

bool nlp_marshaller::eval_grad_f(int n, const double *x, double *grad_f)
{
    try {
        assert(n == boost::numeric_cast<int>(m_prob.get_nx()));

        std::copy(x, x + n, m_dv.begin());
        const auto gradient = m_prob.gradient(m_dv);

        if (m_prob.has_gradient_sparsity()) {
            // Sparse gradient: zero-fill, then scatter the objfun's nonzeros.
            auto g_it = gradient.begin();
            std::fill(grad_f, grad_f + n, 0.);
            for (auto it = m_obj_g_sp.begin(); it != m_obj_g_sp.end(); ++it, ++g_it) {
                assert(it->first == 0u);
                assert(g_it != gradient.end());
                grad_f[it->second] = *g_it;
            }
        } else {
            // Dense gradient: first nx entries are the objfun gradient.
            std::copy(gradient.data(), gradient.data() + n, grad_f);
        }

        return true;
    } catch (...) {
        m_eptr = std::current_exception();
        return false;
    }
}

bool nlp_marshaller::eval_g(int n, const double *x, int m, double *g)
{
    try {
        assert(n == boost::numeric_cast<int>(m_prob.get_nx()));
        assert(m == boost::numeric_cast<int>(m_prob.get_nc()));
        (void)m;

        std::copy(x, x + n, m_dv.begin());
        const auto fitness = m_prob.fitness(m_dv);

        // Eq. constraints, then ineq. constraints.
        std::copy(fitness.data() + 1, fitness.data() + 1 + m_prob.get_nec(), g);
        std::copy(fitness.data() + 1 + m_prob.get_nec(), fitness.data() + 1 + m_prob.get_nc(), g + m_prob.get_nec());

        return true;
    } catch (...) {
        m_eptr = std::current_exception();
        return false;
    }
}

bool nlp_marshaller::eval_jac_g(int n, const double *x, int m, int nele_jac, int *iRow, int *jCol, double *values)
{
    try {
        assert(n == boost::numeric_cast<int>(m_prob.get_nx()));
        assert(m == boost::numeric_cast<int>(m_prob.get_nc()));
        assert(nele_jac == boost::numeric_cast<int>(m_jac_sp.size()));
        (void)m;
        (void)nele_jac;

        if (values) {
            std::copy(x, x + n, m_dv.begin());
            const auto gradient = m_prob.gradient(m_dv);
            // Discard the objfun gradient (dense: nx entries; sparse: m_obj_g_sp.size() entries),
            // keep the constraints' gradients.
            std::copy(gradient.data() + (m_prob.has_gradient_sparsity() ? m_obj_g_sp.size() : m_prob.get_nx()),
                      gradient.data() + gradient.size(), values);
        } else {
            for (decltype(m_jac_sp.size()) k = 0; k < m_jac_sp.size(); ++k) {
                iRow[k] = m_jac_sp[k].first;
                jCol[k] = m_jac_sp[k].second;
            }
        }

        return true;
    } catch (...) {
        m_eptr = std::current_exception();
        return false;
    }
}

bool nlp_marshaller::eval_h(int n, const double *x, double obj_factor, int m, const double *lambda, int nele_hess,
                            int *iRow, int *jCol, double *values)
{
    try {
        assert(n == boost::numeric_cast<int>(m_prob.get_nx()));
        assert(m == boost::numeric_cast<int>(m_prob.get_nc()));
        assert(nele_hess == boost::numeric_cast<int>(m_lag_sp.size()));
        (void)m;
        (void)nele_hess;

        if (!m_prob.has_hessians()) {
            pagmo_throw(
                std::invalid_argument,
                "the exact evaluation of the Hessian of the Lagrangian was requested, but the problem named '"
                    + m_prob.get_name()
                    + "' does not provide it. Please consider providing the Hessian or, alternatively, "
                      "set the option 'hessian_approximation' to 'limited-memory' in the ipopt algorithm options");
        }

        if (values) {
            std::copy(x, x + n, m_dv.begin());
            const auto hessians = m_prob.hessians(m_dv);
            if (m_prob.has_hessians_sparsity()) {
                // Sparse case. Objfun first: walk our sparsity and the merged pattern together,
                // filling the merged slots we have and zeroing the rest.
                assert(hessians[0].size() <= m_lag_sp.size());
                auto it_h_sp = m_h_sp[0].begin();
                auto it = hessians[0].begin();
                assert(hessians[0].size() == m_h_sp[0].size());
                for (decltype(m_lag_sp.size()) i = 0; i < m_lag_sp.size(); ++i) {
                    if (it_h_sp != m_h_sp[0].end() && static_cast<int>(it_h_sp->first) == m_lag_sp[i].first
                        && static_cast<int>(it_h_sp->second) == m_lag_sp[i].second) {
                        assert(it != hessians[0].end());
                        values[i] = (*it) * obj_factor;
                        ++it;
                        ++it_h_sp;
                    } else {
                        values[i] = 0.;
                    }
                }
                // Constraints (lambda factors refer to constraints only, hence j - 1).
                for (decltype(hessians.size()) j = 1; j < hessians.size(); ++j) {
                    assert(hessians[j].size() <= m_lag_sp.size());
                    it_h_sp = m_h_sp[j].begin();
                    it = hessians[j].begin();
                    assert(hessians[j].size() == m_h_sp[j].size());
                    const auto lam = lambda[j - 1u];
                    for (decltype(m_lag_sp.size()) i = 0; i < m_lag_sp.size(); ++i) {
                        if (it_h_sp != m_h_sp[j].end() && static_cast<int>(it_h_sp->first) == m_lag_sp[i].first
                            && static_cast<int>(it_h_sp->second) == m_lag_sp[i].second) {
                            assert(it != hessians[j].end());
                            values[i] += (*it) * lam;
                            ++it;
                            ++it_h_sp;
                        }
                    }
                }
            } else {
                // Dense case. Objfun first, then add each constraint's hessian scaled by lambda.
                assert(hessians[0].size() == m_lag_sp.size());
                std::transform(hessians[0].begin(), hessians[0].end(), values,
                               [obj_factor](double a) { return obj_factor * a; });
                for (decltype(hessians.size()) i = 1; i < hessians.size(); ++i) {
                    assert(hessians[i].size() == m_lag_sp.size());
                    const auto lam = lambda[i - 1u];
                    std::transform(hessians[i].begin(), hessians[i].end(), values, values,
                                   [lam](double a, double b) { return b + lam * a; });
                }
            }
        } else {
            for (decltype(m_lag_sp.size()) k = 0; k < m_lag_sp.size(); ++k) {
                iRow[k] = m_lag_sp[k].first;
                jCol[k] = m_lag_sp[k].second;
            }
        }

        return true;
    } catch (...) {
        m_eptr = std::current_exception();
        return false;
    }
}

} // namespace pagmoNet
