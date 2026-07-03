/* deferred_ipopt.hpp
 *
 * The runtime-`dlopen` IPOPT user-defined algorithm (UDA), namespace pagmoNet.
 *
 * LICENSING NOTE: the dlopen/adapter machinery is original work (MPL-2.0). The evolve()
 * body in the .cpp reuses option-defaulting and select/solve/replace scaffolding adapted
 * from pagmo2's ipopt.cpp (LGPL-3.0 / GPL-3.0); those adapted portions carry pagmo's
 * license. See LICENSING.md, "Files derived from pagmo."
 */
#ifndef PAGMONET_DEFERRED_IPOPT_HPP
#define PAGMONET_DEFERRED_IPOPT_HPP

#include <map>
#include <string>
#include <tuple>
#include <vector>

#include <boost/serialization/base_object.hpp>
#include <boost/serialization/map.hpp>
#include <boost/serialization/string.hpp>

#include <pagmo/algorithm.hpp>
#include <pagmo/algorithms/not_population_based.hpp>
#include <pagmo/population.hpp>
#include <pagmo/s11n.hpp>
#include <pagmo/threading.hpp>
#include <pagmo/types.hpp>

namespace pagmoNet
{

// IPOPT via runtime dynamic loading. API-compatible (option setters, logging, return
// code) with pagmo::ipopt, but links no IPOPT: at evolve() time it dlopen's a libipopt
// (companion package, system install, or the PAGMONET_IPOPT_LIBRARY override) and drives
// its C interface. If none is available it throws an actionable error.
class deferred_ipopt : public pagmo::not_population_based
{
public:
    // Log line: (objfun evals, objective value, n violated constraints, violation norm, feasible).
    using log_line_type = std::tuple<unsigned long, double, pagmo::vector_double::size_type, double, bool>;
    using log_type = std::vector<log_line_type>;

    deferred_ipopt() = default;

    pagmo::population evolve(pagmo::population) const;

    // Last IPOPT ApplicationReturnStatus as an int (0 == Solve_Succeeded).
    int get_last_opt_result() const { return m_last_opt_res; }

    std::string get_name() const { return "IPOPT (deferred-load)"; }
    std::string get_extra_info() const;

    void set_verbosity(unsigned n) { m_verbosity = n; }
    const log_type &get_log() const { return m_log; }

    void set_string_option(const std::string &name, const std::string &value) { m_string_opts[name] = value; }
    void set_integer_option(const std::string &name, int value) { m_integer_opts[name] = value; }
    void set_numeric_option(const std::string &name, double value) { m_numeric_opts[name] = value; }
    void set_string_options(const std::map<std::string, std::string> &m);
    void set_integer_options(const std::map<std::string, int> &m);
    void set_numeric_options(const std::map<std::string, double> &m);
    std::map<std::string, std::string> get_string_options() const { return m_string_opts; }
    std::map<std::string, int> get_integer_options() const { return m_integer_opts; }
    std::map<std::string, double> get_numeric_options() const { return m_numeric_opts; }
    void reset_string_options() { m_string_opts.clear(); }
    void reset_integer_options() { m_integer_opts.clear(); }
    void reset_numeric_options() { m_numeric_opts.clear(); }

    pagmo::thread_safety get_thread_safety() const { return pagmo::thread_safety::none; }

    template <typename Archive>
    void serialize(Archive &ar, unsigned)
    {
        ar &boost::serialization::base_object<pagmo::not_population_based>(*this);
        ar &m_string_opts;
        ar &m_integer_opts;
        ar &m_numeric_opts;
        ar &m_last_opt_res;
        ar &m_verbosity;
        ar &m_log;
    }

private:
    std::map<std::string, std::string> m_string_opts;
    std::map<std::string, int> m_integer_opts;
    std::map<std::string, double> m_numeric_opts;
    // 0 == IP_Solve_Succeeded. Mutable because evolve() is const (pagmo UDA convention).
    mutable int m_last_opt_res = 0;
    unsigned m_verbosity = 0;
    mutable log_type m_log;
};

} // namespace pagmoNet

PAGMO_S11N_ALGORITHM_EXPORT_KEY(pagmoNet::deferred_ipopt)

#endif // PAGMONET_DEFERRED_IPOPT_HPP
