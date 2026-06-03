#pragma once

#include <memory>
#include <stdexcept>
#include <string>
#include <utility>

#include "pagmo/bfe.hpp"
#include "pagmo/problem.hpp"
#include "pagmo/types.hpp"
#include "pagmo/threading.hpp"

namespace pagmoWrap {

/**
 * Director interface for managed (C#/Java) batch fitness evaluator implementations.
 *
 * Subclass in managed code and override call() to implement a custom BFE
 * (e.g., GPU-accelerated evaluation, custom caching, remote dispatch).
 *
 * Naming convention mirrors algorithm_callback / problem_callback:
 * bfe_callback  = SWIG director interface that managed code subclasses
 * managed_bfe   = copy-safe UDT that pagmo can store by value
 */
class bfe_callback
{
public:
    virtual ~bfe_callback() = default;

    /**
     * Evaluates a batch of decision vectors.
     *
     * @param prob   The pagmo::problem whose fitness function to call.
     * @param dvs    Concatenated decision vectors (length = n_individuals * n_vars).
     * @return       Concatenated fitness vectors (length = n_individuals * n_fitness).
     */
    virtual pagmo::vector_double call(
        const pagmo::problem &prob,
        const pagmo::vector_double &dvs) const
    {
        // Default: delegate to problem's single-call fitness in a serial loop.
        const auto nv = prob.get_nx();
        const auto nf = prob.get_nf();
        const auto n  = dvs.size() / nv;

        pagmo::vector_double out;
        out.reserve(n * nf);

        for (std::size_t i = 0; i < n; ++i) {
            pagmo::vector_double x(dvs.begin() + static_cast<std::ptrdiff_t>(i * nv),
                                   dvs.begin() + static_cast<std::ptrdiff_t>((i + 1) * nv));
            auto f = prob.fitness(x);
            out.insert(out.end(), f.begin(), f.end());
        }
        return out;
    }

    virtual std::string get_name()       const { return "Managed BFE"; }
    virtual std::string get_extra_info() const { return ""; }
    virtual pagmo::thread_safety get_thread_safety() const { return pagmo::thread_safety::basic; }

    /** Boundary-safe exception channel — same pattern as algorithm_callback. */
    virtual std::string consume_deferred_exception() { return ""; }
};

/**
 * Copy-safe UDT that pagmo can store by value.
 * Holds a shared_ptr to the director callback so copies are safe
 * across pagmo's internal type-erasure.
 */
class managed_bfe
{
public:
    managed_bfe() : m_cb(std::make_shared<bfe_callback>()) {}

    explicit managed_bfe(bfe_callback *cb)
        : managed_bfe(std::shared_ptr<bfe_callback>(cb))
    {
        if (!cb) throw std::invalid_argument("managed_bfe: callback must not be null");
    }

    explicit managed_bfe(std::shared_ptr<bfe_callback> cb)
        : m_cb(std::move(cb))
    {
        if (!m_cb) throw std::invalid_argument("managed_bfe: callback must not be null");
    }

    pagmo::vector_double operator()(const pagmo::problem &prob, const pagmo::vector_double &dvs) const
    {
        auto result = m_cb->call(prob, dvs);
        throw_if_deferred("managed_bfe::call");
        return result;
    }

    std::string get_name()       const { return m_cb->get_name(); }
    std::string get_extra_info() const { return m_cb->get_extra_info(); }
    pagmo::thread_safety get_thread_safety() const { return m_cb->get_thread_safety(); }

private:
    void throw_if_deferred(const char *context) const
    {
        auto msg = m_cb->consume_deferred_exception();
        if (!msg.empty()) throw std::runtime_error(std::string(context) + ": " + msg);
    }

    std::shared_ptr<bfe_callback> m_cb;
};

} // namespace pagmoWrap
