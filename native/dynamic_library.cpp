/* dynamic_library.cpp -- MPL-2.0 (original work). See dynamic_library.hpp. */
#include "dynamic_library.hpp"

#include <cstdlib>
#include <stdexcept>
#include <string>

#if defined(_WIN32)
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#else
#include <dlfcn.h>
#endif

namespace pagmoNet
{

namespace
{

#if defined(_WIN32)
void *os_load(const char *name)
{
    return reinterpret_cast<void *>(::LoadLibraryA(name));
}
void *os_symbol(void *handle, const char *name)
{
    return reinterpret_cast<void *>(::GetProcAddress(reinterpret_cast<HMODULE>(handle), name));
}
void os_unload(void *handle)
{
    ::FreeLibrary(reinterpret_cast<HMODULE>(handle));
}
#else
void *os_load(const char *name)
{
    return ::dlopen(name, RTLD_NOW | RTLD_GLOBAL);
}
void *os_symbol(void *handle, const char *name)
{
    return ::dlsym(handle, name);
}
void os_unload(void *handle)
{
    ::dlclose(handle);
}
#endif

std::string read_env(const char *name)
{
#if defined(_WIN32)
    // getenv is deprecated under MSVC; use the safe variant.
    char *buf = nullptr;
    size_t len = 0;
    std::string out;
    if (_dupenv_s(&buf, &len, name) == 0 && buf) {
        out = buf;
        std::free(buf);
    }
    return out;
#else
    const char *v = std::getenv(name);
    return v ? std::string(v) : std::string();
#endif
}

} // namespace

dynamic_library::dynamic_library(const char *override_env_var, std::initializer_list<const char *> candidate_names,
                                 const std::string &human_name, const std::string &install_hint)
{
    std::string tried;

    // 1) Explicit override.
    if (override_env_var != nullptr) {
        const std::string ov = read_env(override_env_var);
        if (!ov.empty()) {
            m_handle = os_load(ov.c_str());
            if (m_handle != nullptr) {
                m_loaded = ov;
                return;
            }
            tried += "\n  - " + ov + "  (from " + override_env_var + ")";
        }
    }

    // 2) Candidate names via the OS loader search path.
    for (const char *name : candidate_names) {
        m_handle = os_load(name);
        if (m_handle != nullptr) {
            m_loaded = name;
            return;
        }
        tried += std::string("\n  - ") + name;
    }

    throw std::runtime_error("Could not load the " + human_name + " runtime library. Tried:" + tried + "\n"
                             + install_hint);
}

dynamic_library::~dynamic_library()
{
    if (m_handle != nullptr) {
        os_unload(m_handle);
    }
}

void *dynamic_library::symbol(const char *name) const
{
    void *s = os_symbol(m_handle, name);
    if (s == nullptr) {
        throw std::runtime_error("Symbol '" + std::string(name) + "' was not found in the loaded library '" + m_loaded
                                 + "'. It may not be a compatible build.");
    }
    return s;
}

} // namespace pagmoNet
