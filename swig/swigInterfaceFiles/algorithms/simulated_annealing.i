%{
#include "pagmo/algorithm.hpp"
#include "pagmo/algorithms/simulated_annealing.hpp"
%}

%typemap(csclassmodifiers) pagmo::simulated_annealing "public partial class"
%ignore simulated_annealing::get_log() const;

class simulated_annealing {
public:
    typedef std::tuple<unsigned long long, double, double, double, double> log_line_type;
    typedef std::vector<log_line_type> log_type;

    extern simulated_annealing(double Ts = 10., double Tf = .1, unsigned n_T_adj = 10u, unsigned n_range_adj = 1u,
        unsigned bin_size = 20u, double start_range = 1., unsigned seed = pagmo::random_device::next());

    // Algorithm evolve method
    extern population evolve(population) const;
    extern void set_verbosity(unsigned level);
    extern unsigned get_verbosity() const;
    extern void set_seed(unsigned);
    extern unsigned get_seed() const;
    extern std::string get_name() const;
    extern std::string get_extra_info() const;
    extern const log_type& get_log() const;
};

%extend simulated_annealing {
    int get_log_entry_count() const { return (int)$self->get_log().size(); }
    ::pagmoWrap::SimulatedAnnealingLogEntry get_log_entry(int idx) const {
        const auto& line = $self->get_log().at((std::size_t)idx);
        return {std::get<0>(line), std::get<1>(line), std::get<2>(line),
                std::get<3>(line), std::get<4>(line)};
    }

    pagmo::algorithm to_algorithm() const
    {
        return pagmo::algorithm(*self);
    }
}

