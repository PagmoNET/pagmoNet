%{
#include "pagmo/algorithm.hpp"
#include "pagmo/algorithms/nspso.hpp"
%}

%typemap(csclassmodifiers) pagmo::nspso "public partial class"
%ignore nspso::get_log() const;
class nspso
{
public:

    typedef std::tuple<unsigned, unsigned long long, vector_double> log_line_type;
    typedef std::vector<log_line_type> log_type;

    extern nspso(unsigned gen = 1u, double omega = 0.6, double c1 = 2.0, double c2 = 2.0, double chi = 1.0,
        double v_coeff = 0.5, unsigned leader_selection_range = 60u,
        std::string diversity_mechanism = "crowding distance", bool memory = false,
        unsigned seed = pagmo::random_device::next());

    extern population evolve(population) const;
    extern void set_seed(unsigned);
    extern unsigned get_seed() const;
    extern void set_verbosity(unsigned level);
    extern unsigned get_verbosity() const;
    extern unsigned get_gen() const;
    extern void set_bfe(const bfe& b);
    extern std::string get_name() const;
    extern std::string get_extra_info() const;
    extern const log_type& get_log() const;
};

%extend nspso {
    int get_log_entry_count() const { return (int)$self->get_log().size(); }
    ::pagmoWrap::MoVectorLogEntry get_log_entry(int idx) const {
        const auto& line = $self->get_log().at((std::size_t)idx);
        return {std::get<0>(line), std::get<1>(line), std::get<2>(line)};
    }

    pagmo::algorithm to_algorithm() const
    {
        return pagmo::algorithm(*self);
    }
}

