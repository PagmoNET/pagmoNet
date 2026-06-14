%{
#include "pagmo/algorithm.hpp"
#include "pagmo/algorithms/de1220.hpp"
%}

%typemap(csclassmodifiers) pagmo::de1220 "public partial class"
%ignore de1220::get_log() const;

class de1220 {
public:
	typedef std::tuple<unsigned, unsigned long long, double, double, double, unsigned, double, double> log_line_type;
	typedef std::vector<log_line_type> log_type;
	typedef pop_size_t size_type;
	extern de1220(unsigned gen = 1u, std::vector<unsigned> allowed_variants = de1220_statics<void>::allowed_variants,
		unsigned variant_adptv = 1u, double ftol = 1e-6, double xtol = 1e-6, bool memory = false,
		unsigned seed = pagmo::random_device::next());


	extern population evolve(population) const;
	extern std::string get_name() const;
	extern void set_seed(unsigned);
	extern unsigned get_gen() const;
	extern unsigned get_seed() const;
	extern unsigned get_verbosity() const;
	extern void set_verbosity(unsigned);

	extern std::string get_extra_info() const;
	extern const log_type& get_log() const;
};

%extend de1220 {
    int get_log_entry_count() const { return (int)$self->get_log().size(); }
    ::pagmoWrap::De1220LogEntry get_log_entry(int idx) const {
        const auto& line = $self->get_log().at((std::size_t)idx);
        return {std::get<0>(line), std::get<1>(line), std::get<2>(line), std::get<3>(line), std::get<4>(line), std::get<5>(line), std::get<6>(line), std::get<7>(line)};
    }

    pagmo::algorithm to_algorithm() const
    {
        return pagmo::algorithm(*self);
    }
}

