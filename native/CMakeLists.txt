cmake_minimum_required(VERSION 3.20)
project(PagmoWrapper VERSION 1.0.0)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(APPLE)
    # Xcode 16.4 removed libm.tbd from the SDK. cmake 4.x (CMP0060 NEW) resolves
    # -lm to a full path and adds that path as a Makefile file prerequisite; if the
    # file is missing make fails with "No rule to make target libm.tbd".
    # Strategy: create a minimal valid TBD stub in the build tree and prepend its
    # directory to CMAKE_*_IMPLICIT_LINK_DIRECTORIES so cmake finds our stub FIRST.
    # The stub exists -> make's prereq check passes -> linker resolves to /usr/lib/libm.dylib.
    file(MAKE_DIRECTORY "${CMAKE_BINARY_DIR}/_libm_stub")
    file(WRITE "${CMAKE_BINARY_DIR}/_libm_stub/libm.tbd"
        "--- !tapi-tbd\ntbd-version: 4\ntargets: [ arm64-apple-macos14.0, x86_64-apple-macos14.0 ]\ninstall-name: /usr/lib/libm.dylib\n...\n")
    foreach(_lang IN ITEMS C CXX)
        list(INSERT CMAKE_${_lang}_IMPLICIT_LINK_DIRECTORIES 0 "${CMAKE_BINARY_DIR}/_libm_stub")
        list(FILTER CMAKE_${_lang}_IMPLICIT_LINK_DIRECTORIES
            EXCLUDE REGEX "[Mm]ac[Oo][Ss].*\\.sdk/usr/lib$")
        set(CMAKE_${_lang}_IMPLICIT_LINK_DIRECTORIES
            "${CMAKE_${_lang}_IMPLICIT_LINK_DIRECTORIES}" CACHE INTERNAL "" FORCE)
        list(REMOVE_ITEM CMAKE_${_lang}_IMPLICIT_LINK_LIBRARIES "m")
        set(CMAKE_${_lang}_IMPLICIT_LINK_LIBRARIES
            "${CMAKE_${_lang}_IMPLICIT_LINK_LIBRARIES}" CACHE INTERNAL "" FORCE)
        string(REGEX REPLACE "(^| )-lm( |$)" " " CMAKE_${_lang}_STANDARD_LIBRARIES
            "${CMAKE_${_lang}_STANDARD_LIBRARIES}")
        string(STRIP "${CMAKE_${_lang}_STANDARD_LIBRARIES}" CMAKE_${_lang}_STANDARD_LIBRARIES)
        set(CMAKE_${_lang}_STANDARD_LIBRARIES
            "${CMAKE_${_lang}_STANDARD_LIBRARIES}" CACHE INTERNAL "" FORCE)
    endforeach()
    unset(_lang)
    if(CMAKE_OSX_SYSROOT)
        list(APPEND CMAKE_IGNORE_PATH "${CMAKE_OSX_SYSROOT}/usr/lib")
    endif()
    list(FILTER CMAKE_SYSTEM_LIBRARY_PATH
        EXCLUDE REGEX "[Mm]ac[Oo][Ss].*\\.sdk/usr/lib")
endif()

# Hide all symbols by default; only PAGMONET_EXPORT-decorated functions are visible.
# This matches the Windows __declspec(dllexport) model — no unintended ABI leakage.
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

# When building with a static vcpkg triplet (x64-windows-static-md on Windows,
# x64-linux-static-pic on Linux), pagmo2, Boost.Serialization, and TBB are all
# static libraries. Linking them into PagmoWrapper produces a self-contained
# shared library with no pagmo/boost/tbb/nlopt/ipopt runtime dependencies —
# only the standard C++ runtime is required at runtime.
# We never link IPOPT (the ipopt algorithm is loaded at runtime via dlopen -- see
# deferred_ipopt.cpp). If the installed pagmo happened to be built with IPOPT, its
# export file may still reference pagmo::IPOPT::libipopt; pre-create a harmless INTERFACE
# stub so find_package(Pagmo) resolves without pulling in any IPOPT library.
if(NOT TARGET pagmo::IPOPT::libipopt)
    add_library(pagmo::IPOPT::libipopt INTERFACE IMPORTED)
endif()

find_package(Pagmo REQUIRED)
find_package(Boost REQUIRED COMPONENTS serialization)

# NOTE: the macOS libm.tbd strip for Pagmo::pagmo / NLopt::nlopt / Ipopt::Ipopt is a
# single generic sweep at the end of this file (after the optional-solver blocks import
# those targets). See the "Xcode 16.4+ libm.tbd" block below.

# nloptGenerated.cxx is always compiled; it is empty when NLopt is not available
# (the SWIG interface guards it with #if defined(PAGMO_WITH_NLOPT)).
#
# The IPOPT algorithm is always compiled in but links no IPOPT: nlp_marshaller.cpp is the
# IPOPT-free marshalling core, deferred_ipopt.cpp is the UDA that dlopen's a libipopt at
# runtime, and dynamic_library.cpp is the cross-platform loader. See deferred_ipopt.hpp.
add_library(PagmoWrapper SHARED
    GeneratedWrappers.cxx
    managed_bridge.cpp
    nloptGenerated.cxx
    nlp_marshaller.cpp
    dynamic_library.cpp
    deferred_ipopt.cpp
)

target_include_directories(PagmoWrapper PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/../swig/pagmo
)

target_link_libraries(PagmoWrapper PRIVATE
    Pagmo::pagmo
    Boost::serialization
    ${CMAKE_DL_LIBS}   # dlopen/dlsym for deferred_ipopt on Unix; no-op on Windows
)

# Pagmo::pagmo exports PAGMO_WITH_NLOPT/IPOPT as INTERFACE compile definitions
# reflecting how upstream pagmo was built, not what we have available. Explicitly
# undefine them so our wrapper only enables optional-solver code when we have
# verified the solver is present (PAGMONET_WITH_* options below).
if(MSVC)
    target_compile_options(PagmoWrapper PRIVATE /UPAGMO_WITH_NLOPT /UPAGMO_WITH_IPOPT /bigobj)
else()
    target_compile_options(PagmoWrapper PRIVATE -UPAGMO_WITH_NLOPT -UPAGMO_WITH_IPOPT)
endif()

# Platform-specific library properties.
if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
    # Set SONAME so consumers can find the library by its short name.
    set_target_properties(PagmoWrapper PROPERTIES
        VERSION   ${PROJECT_VERSION}
        SOVERSION 1
    )
elseif(APPLE)
    # Use @loader_path so the dylib resolves sibling libraries at runtime
    # regardless of install location (NuGet runtimes/ layout).
    set_target_properties(PagmoWrapper PROPERTIES
        BUILD_WITH_INSTALL_RPATH ON
        INSTALL_RPATH "@loader_path"
    )
endif()

# Optional solver support. Use PAGMONET_WITH_* names to avoid collision with
# pagmo's own cmake-exported PAGMO_WITH_* variables (which reflect how the
# upstream pagmo library was built, not how we want to build our wrapper).
# IPOPT is no longer a compile-time option: the ipopt algorithm is always built in and
# loads libipopt at runtime via dlopen (deferred_ipopt.cpp), so no IPOPT link/toolchain is
# needed to build the base wrapper. NLopt remains an opt-in compile-time link; SNOPT7 is a
# post-1.0 placeholder (it will also become deferred-load, never a compile-time link).
option(PAGMONET_WITH_NLOPT  "Build PagmoWrapper with NLopt support"  OFF)
option(PAGMONET_WITH_SNOPT7 "Build PagmoWrapper with SNOPT7 support" OFF)

if(PAGMONET_WITH_NLOPT)
    find_package(NLopt CONFIG REQUIRED)
    target_compile_definitions(PagmoWrapper PRIVATE PAGMO_WITH_NLOPT)
    target_link_libraries(PagmoWrapper PRIVATE NLopt::nlopt)
endif()

# (No IPOPT link block: the ipopt algorithm reaches IPOPT at runtime via dlopen, so there
# is nothing to find_package/link here. deferred_ipopt.cpp + dynamic_library.cpp handle it.)

if(PAGMONET_WITH_SNOPT7)
    target_compile_definitions(PagmoWrapper PRIVATE PAGMO_WITH_SNOPT7)
endif()

if(APPLE)
    # Xcode 16.4+ removed libm.tbd from the SDK. Any imported target whose interface
    # link libraries reference libm -- as "m", "-lm", or a path cmake resolved to the
    # now-missing libm.tbd / libm.dylib -- makes cmake emit that absolute path as a
    # Makefile file prerequisite: "No rule to make target libm.tbd". Math lives in
    # libSystem.B on macOS (always linked), so strip every such reference from ALL
    # imported targets in this directory.
    #
    # One generic sweep is deliberately used instead of per-target strips: the latter
    # silently miss a target the moment a new one is linked (exactly how NLopt::nlopt
    # slipped through and broke the base macOS build). This mirrors the PagmoNet4j
    # `.ipopt` superset's pagmoWrapper/CMakeLists.txt technique so both native builds
    # stay in parity.
    #
    # Gated on APPLE because on Linux "-lm"/"m" is a real, required separate library;
    # stripping it there would break the IPOPT link. Placed after the optional-solver
    # blocks so NLopt::nlopt / Ipopt::Ipopt are already imported -- the mutated property
    # is read at generate time, after the target_link_libraries calls above.
    get_property(_imported_tgts DIRECTORY PROPERTY IMPORTED_TARGETS)
    foreach(_tgt IN LISTS _imported_tgts)
        get_target_property(_iface_libs ${_tgt} INTERFACE_LINK_LIBRARIES)
        if(_iface_libs)
            list(FILTER _iface_libs EXCLUDE REGEX "libm\\.tbd$|libm\\.dylib$|^-lm$|^m$|/usr/lib/libm")
            set_target_properties(${_tgt} PROPERTIES INTERFACE_LINK_LIBRARIES "${_iface_libs}")
        endif()
    endforeach()
    unset(_iface_libs)
    unset(_imported_tgts)
    unset(_tgt)
endif()
