%{
#include "pagmo/algorithm.hpp"
#include "pagmo/algorithms/gwo.hpp"
#include "pagmo/rng.hpp"
%}

%typemap(csclassmodifiers) pagmo::gwo "public partial class"
%ignore gwo::get_log() const;

class gwo {
public:
    typedef std::tuple<unsigned, double, double, double> log_line_type;
    typedef std::vector<log_line_type> log_type;
    
    extern gwo(unsigned gen = 1u, unsigned seed = pagmo::random_device::next());
    extern population evolve(population) const;
    extern void set_seed(unsigned);
    extern unsigned get_seed() const;
    extern void set_verbosity(unsigned level);
    extern unsigned get_verbosity() const;
    extern unsigned get_gen() const;
    extern std::string get_name() const;
    extern std::string get_extra_info() const;
    extern const log_type& get_log() const;
};

%extend gwo {
    int get_log_entry_count() const { return (int)$self->get_log().size(); }
    ::pagmoWrap::GwoLogEntry get_log_entry(int idx) const {
        const auto& line = $self->get_log().at((std::size_t)idx);
        return {std::get<0>(line), std::get<1>(line), std::get<2>(line), std::get<3>(line)};
    }

    pagmo::algorithm to_algorithm() const
    {
        return pagmo::algorithm(*self);
    }
}

