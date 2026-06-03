%{
#include "pagmo/bfe.hpp"
#include "bfe_callback.h"
%}
%typemap(csclassmodifiers) pagmo::bfe "public partial class"
%typemap(csclassmodifiers) pagmoWrap::bfe_callback "public partial class"

class bfe {
    virtual std::string get_name() const = 0;
};

%feature("director") pagmoWrap::bfe_callback;

class pagmoWrap::bfe_callback {
public:
    virtual ~bfe_callback();
    virtual pagmo::vector_double call(
        const pagmo::problem &prob,
        const pagmo::vector_double &dvs) const;
    virtual std::string get_name() const;
    virtual std::string get_extra_info() const;
    virtual pagmo::thread_safety get_thread_safety() const;
    virtual std::string consume_deferred_exception();
};

class pagmoWrap::managed_bfe {
public:
    managed_bfe();
    explicit managed_bfe(pagmoWrap::bfe_callback *cb);
    pagmo::vector_double operator()(const pagmo::problem &prob, const pagmo::vector_double &dvs) const;
    std::string get_name() const;
    std::string get_extra_info() const;
    pagmo::thread_safety get_thread_safety() const;
};
