%{
#include "pagmo/algorithm.hpp"
#include "pagmo/population.hpp"
#include "deferred_ipopt.hpp"
// The public managed type stays `pagmo.ipopt` for API continuity, but it is backed by the
// runtime-dlopen implementation pagmoNet::deferred_ipopt (no IPOPT is linked; the library is
// loaded at evolve() time -- see deferred_ipopt.hpp). This alias lets the generated wrapper
// keep referring to `pagmo::ipopt` while the real type does the work.
namespace pagmo { using ipopt = ::pagmoNet::deferred_ipopt; }
%}

%typemap(csclassmodifiers) pagmo::ipopt "public partial class"
%ignore ipopt::get_log() const;
%ignore ipopt::get_last_opt_result() const;
%ignore ipopt::set_string_options(const std::map<std::string, std::string> &);
%ignore ipopt::set_integer_options(const std::map<std::string, int> &);
%ignore ipopt::set_numeric_options(const std::map<std::string, double> &);
%ignore ipopt::get_string_options() const;
%ignore ipopt::get_integer_options() const;
%ignore ipopt::get_numeric_options() const;

class ipopt {
public:
    typedef std::tuple<unsigned long, double, vector_double::size_type, double, bool> log_line_type;
    typedef std::vector<log_line_type> log_type;

    ipopt();

    extern population evolve(population) const;
    extern int get_last_opt_result() const;
    extern std::string get_name() const;
    extern std::string get_extra_info() const;
    extern void set_verbosity(unsigned n);
    extern const log_type &get_log() const;

    extern void set_string_option(const std::string &, const std::string &);
    extern void set_integer_option(const std::string &, int);
    extern void set_numeric_option(const std::string &, double);
    extern void set_string_options(const std::map<std::string, std::string> &);
    extern void set_integer_options(const std::map<std::string, int> &);
    extern void set_numeric_options(const std::map<std::string, double> &);
    extern std::map<std::string, std::string> get_string_options() const;
    extern std::map<std::string, int> get_integer_options() const;
    extern std::map<std::string, double> get_numeric_options() const;
    extern void reset_string_options();
    extern void reset_integer_options();
    extern void reset_numeric_options();

    extern thread_safety get_thread_safety() const;
};

%extend ipopt {
    // IPOPT's return status, exposed as a numeric code (0 == Solve_Succeeded).
    int get_last_opt_result_code() const
    {
        return self->get_last_opt_result();
    }

    void set_integer_option_u64(const std::string &name, unsigned long long value)
    {
        self->set_integer_option(name, static_cast<int>(value));
    }

    int get_log_entry_count() const
    {
        return (int)self->get_log().size();
    }

    ::pagmoWrap::IpoptLogEntry get_log_entry(int idx) const
    {
        const auto &line = self->get_log().at((std::size_t)idx);
        return ::pagmoWrap::IpoptLogEntry{
            (unsigned long long)std::get<0>(line),
            std::get<1>(line),
            static_cast<unsigned long long>(std::get<2>(line)),
            std::get<3>(line),
            std::get<4>(line)
        };
    }

    pagmo::algorithm to_algorithm() const
    {
        return pagmo::algorithm(*self);
    }
}
