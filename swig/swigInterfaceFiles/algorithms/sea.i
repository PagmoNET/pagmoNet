%{
#include "pagmo/algorithm.hpp"
#include "pagmo/algorithms/sea.hpp"
%}

%typemap(csclassmodifiers) pagmo::sea "public partial class"
%ignore sea::get_log() const;
class sea
{
public:
    typedef std::tuple<unsigned, unsigned long long, double, double, vector_double::size_type> log_line_type;
    typedef std::vector<log_line_type> log_type;

	extern sea(unsigned gen = 1u, unsigned seed = pagmo::random_device::next());
    extern population evolve(population) const;
    extern void set_verbosity(unsigned level);
    extern unsigned get_verbosity() const;
    extern void set_seed(unsigned);
    extern unsigned get_seed() const;
    extern std::string get_name() const;
    extern std::string get_extra_info() const;
    extern const log_type& get_log() const;
};

%extend sea {
    int get_log_entry_count() const { return (int)$self->get_log().size(); }
    ::pagmoWrap::SeaLogEntry get_log_entry(int idx) const {
        const auto& line = $self->get_log().at((std::size_t)idx);
        return {std::get<0>(line), std::get<1>(line), std::get<2>(line), std::get<3>(line), static_cast<unsigned long long>(std::get<4>(line))};
    }

    pagmo::algorithm to_algorithm() const
    {
        return pagmo::algorithm(*self);
    }
}

