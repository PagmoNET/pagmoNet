/* dynamic_library.cpp -- MPL-2.0 (original work). See dynamic_library.hpp. */
// dladdr() (used to locate this shared library on Unix) is a GNU extension guarded by __USE_GNU;
// _GNU_SOURCE must be defined before the first system header. Harmless on Windows/macOS.
#ifndef _GNU_SOURCE
#define _GNU_SOURCE 1
#endif
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

// Directory containing THIS shared library (the wrapper). We resolve solver candidates relative to
// it because the OS bare-name search does not include the loading module's own directory on Linux
// (dlopen) the way the app-dir search effectively does on Windows -- so an app-local libipopt that
// the companion package drops next to the wrapper is otherwise invisible.
std::string self_dir()
{
#if defined(_WIN32)
    HMODULE hmod = nullptr;
    if (!::GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
                              reinterpret_cast<LPCSTR>(&os_load), &hmod)) {
        return {};
    }
    char buf[MAX_PATH];
    const DWORD n = ::GetModuleFileNameA(hmod, buf, MAX_PATH);
    if (n == 0 || n >= MAX_PATH) {
        return {};
    }
    std::string path(buf, n);
    const auto slash = path.find_last_of("\\/");
    return slash == std::string::npos ? std::string() : path.substr(0, slash);
#else
    Dl_info info{};
    if (::dladdr(reinterpret_cast<void *>(&os_load), &info) == 0 || info.dli_fname == nullptr) {
        return {};
    }
    std::string path(info.dli_fname);
    const auto slash = path.find_last_of('/');
    return slash == std::string::npos ? std::string() : path.substr(0, slash);
#endif
}

constexpr char path_sep()
{
#if defined(_WIN32)
    return '\\';
#else
    return '/';
#endif
}

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

    // 2) Candidate names co-located with this wrapper library (where the companion package drops
    // the solver). Explicit because dlopen does not search the caller's own directory on Linux.
    {
        const std::string dir = self_dir();
        if (!dir.empty()) {
            for (const char *name : candidate_names) {
                const std::string full = dir + path_sep() + name;
                m_handle = os_load(full.c_str());
                if (m_handle != nullptr) {
                    m_loaded = full;
                    return;
                }
                tried += "\n  - " + full;
            }
        }
    }

    // 3) Candidate names via the OS loader search path (system install / LD_LIBRARY_PATH / RPATH).
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
