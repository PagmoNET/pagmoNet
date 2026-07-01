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
# When pagmo2 is built with IPOPT support, pagmo_export.cmake references
# pagmo::IPOPT::libipopt as a transitive link dependency. Pagmo's own config
# only pre-creates the 'header' component target; we must pre-create
# 'libipopt' so the export file can resolve it during find_package(Pagmo).
if(NOT TARGET pagmo::IPOPT::libipopt)
    if(PAGMONET_WITH_IPOPT)
        find_library(_pagmo_ipopt_lib NAMES ipopt ipopt-3)
        if(_pagmo_ipopt_lib)
            add_library(pagmo::IPOPT::libipopt UNKNOWN IMPORTED)
            set_target_properties(pagmo::IPOPT::libipopt PROPERTIES
                IMPORTED_LOCATION "${_pagmo_ipopt_lib}")
        else()
            add_library(pagmo::IPOPT::libipopt INTERFACE IMPORTED)
        endif()
    else()
        add_library(pagmo::IPOPT::libipopt INTERFACE IMPORTED)
    endif()
    unset(_pagmo_ipopt_lib)
endif()

find_package(Pagmo REQUIRED)
find_package(Boost REQUIRED COMPONENTS serialization)

if(APPLE)
    # pagmo2's cmake config records -lm from its build environment. On macOS Xcode 16+,
    # libm.tbd no longer exists as a separate SDK stub — math is in libSystem.B.
    # Strip the resolved libm.tbd path from Pagmo::pagmo's interface before cmake
    # generates Makefile rules that depend on the missing file.
    get_target_property(_pagmo_iface_libs Pagmo::pagmo INTERFACE_LINK_LIBRARIES)
    if(_pagmo_iface_libs)
        list(FILTER _pagmo_iface_libs EXCLUDE REGEX "libm\\.tbd$|libm\\.dylib$|-lm$|^m$")
        set_target_properties(Pagmo::pagmo PROPERTIES
            INTERFACE_LINK_LIBRARIES "${_pagmo_iface_libs}")
    endif()
    unset(_pagmo_iface_libs)
endif()

# nloptGenerated.cxx is always compiled; it is empty when NLopt is not available
# (the SWIG interface guards it with #if defined(PAGMO_WITH_NLOPT)).
add_library(PagmoWrapper SHARED
    GeneratedWrappers.cxx
    managed_bridge.cpp
    nloptGenerated.cxx
)

target_include_directories(PagmoWrapper PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/../swig/pagmo
)

target_link_libraries(PagmoWrapper PRIVATE
    Pagmo::pagmo
    Boost::serialization
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
option(PAGMONET_WITH_NLOPT  "Build PagmoWrapper with NLopt support"  OFF)
option(PAGMONET_WITH_IPOPT  "Build PagmoWrapper with IPOPT support"  OFF)
option(PAGMONET_WITH_SNOPT7 "Build PagmoWrapper with SNOPT7 support" OFF)

if(PAGMONET_WITH_NLOPT)
    find_package(NLopt CONFIG REQUIRED)
    if(APPLE)
        # NLopt is a math library; its cmake config records -lm the same way pagmo2
        # does. On Xcode 16.4+ that resolves to the removed libm.tbd SDK stub, which
        # cmake then emits as a Makefile file prerequisite -> "No rule to make target
        # .../MacOSX.sdk/usr/lib/libm.tbd". Strip it here for the same reason as the
        # Pagmo::pagmo strip above: math lives in libSystem, so the link still succeeds.
        get_target_property(_nlopt_iface_libs NLopt::nlopt INTERFACE_LINK_LIBRARIES)
        if(_nlopt_iface_libs)
            list(FILTER _nlopt_iface_libs EXCLUDE REGEX "libm\\.tbd$|libm\\.dylib$|-lm$|^m$")
            set_target_properties(NLopt::nlopt PROPERTIES
                INTERFACE_LINK_LIBRARIES "${_nlopt_iface_libs}")
        endif()
        unset(_nlopt_iface_libs)
    endif()
    target_compile_definitions(PagmoWrapper PRIVATE PAGMO_WITH_NLOPT)
    target_link_libraries(PagmoWrapper PRIVATE NLopt::nlopt)
endif()

if(PAGMONET_WITH_IPOPT)
    # Three cases in order of preference:
    # 1. cmake config package (future vcpkg versions or other setups)
    # 2. Windows/vcpkg: pagmo::IPOPT::libipopt was pre-created above via find_library;
    #    Pagmo::pagmo links it transitively. Only the compile definition and include
    #    path are needed here — no explicit target_link_libraries call required.
    # 3. Linux/macOS: pkg-config (apt pagmo or source build).
    find_package(Ipopt CONFIG QUIET)
    if(TARGET Ipopt::Ipopt)
        target_compile_definitions(PagmoWrapper PRIVATE PAGMO_WITH_IPOPT)
        if(APPLE)
            # Pagmo::pagmo already links libipopt transitively through the
            # pre-created pagmo::IPOPT::libipopt target above, and ld records it
            # with the dylib's real install name (@rpath/libipopt.3.dylib). The
            # overlay port's macOS ipopt-config.cmake points Ipopt::Ipopt at the
            # dylib-copied-as-a-.a, so linking Ipopt::Ipopt as well adds a SECOND
            # libipopt load command under a different name (@rpath/libipopt.a).
            # ld dedupes load commands by install name, but these two names differ
            # so both survive -- and the @rpath/libipopt.a one is unresolvable at
            # runtime, breaking dlopen of the wrapper. So on macOS take only
            # Ipopt::Ipopt's include dirs and let the single transitive link
            # provide the library.
            get_target_property(_ipopt_inc Ipopt::Ipopt INTERFACE_INCLUDE_DIRECTORIES)
            if(_ipopt_inc)
                target_include_directories(PagmoWrapper PRIVATE ${_ipopt_inc})
            endif()
            unset(_ipopt_inc)
        else()
            target_link_libraries(PagmoWrapper PRIVATE Ipopt::Ipopt)
        endif()
    elseif(WIN32)
        # conda-forge IPOPT is a pre-built DLL that bundles its own BLAS (OpenBLAS).
        # No MKL or separate BLAS linking needed — ipopt.dll handles it internally.
        find_path(IPOPT_INCLUDE_DIR NAMES IpIpoptApplication.hpp PATH_SUFFIXES coin-or)
        target_compile_definitions(PagmoWrapper PRIVATE PAGMO_WITH_IPOPT)
        if(IPOPT_INCLUDE_DIR)
            target_include_directories(PagmoWrapper PRIVATE "${IPOPT_INCLUDE_DIR}")
        endif()
    else()
        find_package(PkgConfig REQUIRED)
        pkg_check_modules(IPOPT REQUIRED ipopt)
        target_compile_definitions(PagmoWrapper PRIVATE PAGMO_WITH_IPOPT)
        if(APPLE)
            # On macOS Xcode 16+, cmake resolves -lm (from ipopt.pc) to the full
            # path of libm.tbd in the Xcode SDK. cmake's Makefile generator adds
            # libm.tbd as a file dependency; when the file doesn't build, make
            # fails. Use IPOPT_LDFLAGS (raw pkg-config flags) via target_link_options
            # so -lm goes straight to the linker without cmake creating a file dep.
            separate_arguments(_ipopt_ldflags UNIX_COMMAND "${IPOPT_LDFLAGS}")
            list(FILTER _ipopt_ldflags EXCLUDE REGEX "^-lm$")
            target_link_options(PagmoWrapper PRIVATE ${_ipopt_ldflags})
            target_include_directories(PagmoWrapper PRIVATE ${IPOPT_INCLUDE_DIRS})
            unset(_ipopt_ldflags)
        else()
            target_link_libraries(PagmoWrapper PRIVATE ${IPOPT_LINK_LIBRARIES})
            target_include_directories(PagmoWrapper PRIVATE ${IPOPT_INCLUDE_DIRS})
            target_link_directories(PagmoWrapper PRIVATE ${IPOPT_LIBRARY_DIRS})
        endif()
    endif()
endif()

if(PAGMONET_WITH_SNOPT7)
    target_compile_definitions(PagmoWrapper PRIVATE PAGMO_WITH_SNOPT7)
endif()
