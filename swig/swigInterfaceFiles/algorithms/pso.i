%{
#include "pagmo/algorithm.hpp"
#include "pagmo/algorithms/pso.hpp"
%}

%typemap(csclassmodifiers) pagmo::pso "public partial class"
%ignore pso::get_log() const;

class pso {
public:
    /// Single entry of the log (Gen, Fevals, gbest, Mean Vel., Mean lbest, Avg. Dist.)
    typedef std::tuple<unsigned, unsigned long long, double, double, double, double> log_line_type;
    /// The log
    typedef std::vector<log_line_type> log_type;

    extern pso(unsigned gen = 1u, double omega = 0.7298, double eta1 = 2.05, double eta2 = 2.05, double max_vel = 0.5,
        unsigned variant = 5u, unsigned neighb_type = 2u, unsigned neighb_param = 4u, bool memory = false,
        unsigned seed = pagmo::random_device::next());
    extern population evolve(population) const;
    extern void set_verbosity(unsigned level);
    extern unsigned get_verbosity() const;
    extern void set_seed(unsigned);
    extern unsigned get_seed() const;
    extern std::string get_name() const;
    extern std::string get_extra_info() const;
    extern const log_type& get_log() const;
};

%extend pso {
    int get_log_entry_count() const { return (int)$self->get_log().size(); }
    ::pagmoWrap::PsoLogEntry get_log_entry(int idx) const {
        const auto& line = $self->get_log().at((std::size_t)idx);
        return {std::get<0>(line), std::get<1>(line), std::get<2>(line),
                std::get<3>(line), std::get<4>(line), std::get<5>(line)};
    }

    pagmo::algorithm to_algorithm() const
    {
        return pagmo::algorithm(*self);
    }
}

