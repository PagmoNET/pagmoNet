/* dynamic_library.hpp
 *
 * Original work — MPL-2.0 (the surrounding Pagmo.NET / PagmoNet4j license).
 *
 * Cross-platform runtime library loader for the deferred-load solver seam. Built
 * solver-agnostic (candidate-name list + override-env-var name as parameters) so the
 * future SNOPT UDA can reuse it verbatim. Loads via LoadLibrary/GetProcAddress on
 * Windows and dlopen/dlsym elsewhere.
 */
#ifndef PAGMONET_DYNAMIC_LIBRARY_HPP
#define PAGMONET_DYNAMIC_LIBRARY_HPP

#include <initializer_list>
#include <string>

namespace pagmoNet
{

// Loads a shared library at runtime and resolves symbols from it. Resolution order:
//   1) the file named by `override_env_var`, if that environment variable is set;
//   2) each name in `candidate_names`, handed to the OS loader (which itself searches the
//      loading module's own directory first -- where a companion package drops the library --
//      then the system search paths).
// Throws std::runtime_error with an actionable, multi-line message if nothing loads.
class dynamic_library
{
public:
    dynamic_library(const char *override_env_var, std::initializer_list<const char *> candidate_names,
                    const std::string &human_name, const std::string &install_hint);
    ~dynamic_library();

    dynamic_library(const dynamic_library &) = delete;
    dynamic_library &operator=(const dynamic_library &) = delete;

    // Resolve a required symbol; throws std::runtime_error if it is missing.
    void *symbol(const char *name) const;

    template <typename Fn>
    Fn symbol_as(const char *name) const
    {
        return reinterpret_cast<Fn>(symbol(name));
    }

    // Full path/name that actually loaded (for diagnostics).
    const std::string &loaded_name() const { return m_loaded; }

private:
    void *m_handle = nullptr;
    std::string m_loaded;
};

} // namespace pagmoNet

#endif // PAGMONET_DYNAMIC_LIBRARY_HPP
