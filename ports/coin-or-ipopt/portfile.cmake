# Bridge port for macOS and Windows: use pre-built conda-forge IPOPT instead of
# building from source. Linux continues to build from source (autotools) below.
if(VCPKG_TARGET_IS_OSX OR VCPKG_TARGET_IS_WINDOWS OR VCPKG_TARGET_IS_MINGW)
    set(VCPKG_BUILD_TYPE release)

    # Locate the conda-forge IPOPT prefix (set by CI via IPOPT_PREFIX env var).
    set(_ipopt_prefix "$ENV{IPOPT_PREFIX}")
    if(NOT _ipopt_prefix OR NOT EXISTS "${_ipopt_prefix}")
        if(VCPKG_TARGET_IS_OSX)
            set(_ipopt_prefix "$ENV{HOME}/mumps-env")
        else()
            set(_ipopt_prefix "C:/ipopt-env/Library")
        endif()
    endif()
    if(NOT EXISTS "${_ipopt_prefix}")
        message(FATAL_ERROR
            "coin-or-ipopt bridge port: IPOPT_PREFIX ('${_ipopt_prefix}') not found. "
            "Install IPOPT via 'micromamba create -c conda-forge -p <env> ipopt' and set IPOPT_PREFIX.")
    endif()

    # Install ALL headers (including IpoptConfig.h which is .h not .hpp)
    file(MAKE_DIRECTORY "${CURRENT_PACKAGES_DIR}/include/coin-or")
    cmake_path(GET _ipopt_prefix PARENT_PATH _ipopt_env_root_hdr)
    file(GLOB _ipopt_hdrs LIST_DIRECTORIES false
        "${_ipopt_prefix}/include/coin-or/*.hpp"
        "${_ipopt_prefix}/include/coin-or/*.h"
        "${_ipopt_env_root_hdr}/include/coin-or/*.hpp"
        "${_ipopt_env_root_hdr}/include/coin-or/*.h")
    unset(_ipopt_env_root_hdr)
    if(NOT _ipopt_hdrs)
        message(FATAL_ERROR "coin-or-ipopt: no IPOPT headers found under ${_ipopt_prefix}/include/coin-or/")
    endif()
    file(COPY ${_ipopt_hdrs} DESTINATION "${CURRENT_PACKAGES_DIR}/include/coin-or/")

    # Install library and record the path for the cmake config
    if(VCPKG_TARGET_IS_OSX)
        file(GLOB _ipopt_lib "${_ipopt_prefix}/lib/libipopt*.dylib")
        if(NOT _ipopt_lib)
            message(FATAL_ERROR "coin-or-ipopt: no libipopt*.dylib found under ${_ipopt_prefix}/lib/")
        endif()
        file(COPY ${_ipopt_lib} DESTINATION "${CURRENT_PACKAGES_DIR}/lib/")
        file(GLOB _lib_installed "${CURRENT_PACKAGES_DIR}/lib/libipopt*.dylib")
        list(GET _lib_installed 0 _lib_installed)
        # vcpkg's static triplet sets CMAKE_FIND_LIBRARY_SUFFIXES=".a" so find_library
        # won't find .dylib. Copy the dylib as .a too — macOS ld reads Mach-O magic
        # bytes (not the file extension) and will correctly link it as a shared library.
        get_filename_component(_dylib_stem "${_lib_installed}" NAME_WE)
        configure_file("${_lib_installed}" "${CURRENT_PACKAGES_DIR}/lib/${_dylib_stem}.a" COPYONLY)
    else()
        # Windows: import library (.lib) for link-time, DLL for runtime.
        # conda-forge may place DLLs in Library/bin or directly in bin at the env root;
        # search both locations with wildcard patterns to handle version suffixes.
        cmake_path(GET _ipopt_prefix PARENT_PATH _ipopt_env_root)
        file(MAKE_DIRECTORY "${CURRENT_PACKAGES_DIR}/bin")
        file(GLOB _ipopt_implib
            "${_ipopt_prefix}/lib/ipopt*.lib"
            "${_ipopt_prefix}/lib/libipopt*.lib"
            "${_ipopt_env_root}/lib/ipopt*.lib"
            "${_ipopt_env_root}/lib/libipopt*.lib")
        file(GLOB _ipopt_dll
            "${_ipopt_prefix}/bin/ipopt*.dll"
            "${_ipopt_prefix}/bin/libipopt*.dll"
            "${_ipopt_env_root}/bin/ipopt*.dll"
            "${_ipopt_env_root}/bin/libipopt*.dll")
        if(NOT _ipopt_implib)
            message(FATAL_ERROR "coin-or-ipopt: no ipopt.lib found under ${_ipopt_prefix}/lib/ or ${_ipopt_env_root}/lib/")
        endif()
        if(NOT _ipopt_dll)
            message(FATAL_ERROR "coin-or-ipopt: no ipopt.dll found under ${_ipopt_prefix}/bin/ or ${_ipopt_env_root}/bin/")
        endif()
        list(GET _ipopt_implib 0 _ipopt_implib)
        list(GET _ipopt_dll    0 _ipopt_dll)
        get_filename_component(_implib_name "${_ipopt_implib}" NAME)
        get_filename_component(_dll_name    "${_ipopt_dll}"    NAME)
        file(COPY "${_ipopt_implib}" DESTINATION "${CURRENT_PACKAGES_DIR}/lib/")
        file(COPY "${_ipopt_dll}"    DESTINATION "${CURRENT_PACKAGES_DIR}/bin/")
        set(_lib_installed "${CURRENT_PACKAGES_DIR}/lib/${_implib_name}")
        unset(_ipopt_env_root)
    endif()

    # Write pkg-config file so pkg_check_modules(ipopt) in CMakeLists.txt works
    file(MAKE_DIRECTORY "${CURRENT_PACKAGES_DIR}/lib/pkgconfig")
    file(WRITE "${CURRENT_PACKAGES_DIR}/lib/pkgconfig/ipopt.pc"
"Name: ipopt
Version: 3.14
Description: IPOPT NLP solver (conda-forge bridge)
Libs: -L${_ipopt_prefix}/lib -lipopt
Cflags: -I${_ipopt_prefix}/include/coin-or
")

    # Write cmake config so find_package(Ipopt) works (used by vcpkg/pagmo2 build).
    # Must use CURRENT_INSTALLED_DIR (not CURRENT_PACKAGES_DIR): vcpkg removes the
    # packages/ staging dir after install, so any path baked into the config that
    # references packages/ breaks when the vcpkg cache is restored on a later run.
    file(MAKE_DIRECTORY "${CURRENT_PACKAGES_DIR}/share/ipopt")
    if(VCPKG_TARGET_IS_OSX)
        file(WRITE "${CURRENT_PACKAGES_DIR}/share/ipopt/ipopt-config.cmake"
"add_library(Ipopt::Ipopt SHARED IMPORTED GLOBAL)
set_target_properties(Ipopt::Ipopt PROPERTIES
    IMPORTED_LOCATION \"${CURRENT_INSTALLED_DIR}/lib/${_dylib_stem}.a\"
    INTERFACE_INCLUDE_DIRECTORIES \"${CURRENT_INSTALLED_DIR}/include/coin-or\")
")
    else()
        file(WRITE "${CURRENT_PACKAGES_DIR}/share/ipopt/ipopt-config.cmake"
"add_library(Ipopt::Ipopt SHARED IMPORTED GLOBAL)
set_target_properties(Ipopt::Ipopt PROPERTIES
    IMPORTED_LOCATION \"${CURRENT_INSTALLED_DIR}/bin/${_dll_name}\"
    IMPORTED_IMPLIB   \"${CURRENT_INSTALLED_DIR}/lib/${_implib_name}\"
    INTERFACE_INCLUDE_DIRECTORIES \"${CURRENT_INSTALLED_DIR}/include/coin-or\")
")
    endif()

    set(VCPKG_POLICY_EMPTY_PACKAGE enabled)
    set(VCPKG_POLICY_ALLOW_EMPTY_FOLDERS enabled)
    return()
endif()

# ── Linux: build IPOPT from source via autotools (unchanged) ─────────────────
# Skip the debug build: we only need release IPOPT for our native library.
set(VCPKG_BUILD_TYPE release)

vcpkg_from_github(
    OUT_SOURCE_PATH SOURCE_PATH
    REPO coin-or/Ipopt
    REF ec43e37a06054246764fb116e50e3e30c9ada089
    SHA512 f5b30e81b4a1a178e9a0e2b51b4832f07441b2c3e9a2aa61a6f07807f94185998e985fcf3c34d96fbfde78f07b69f2e0a0675e1e478a4e668da6da60521e0fd6
    HEAD_REF master
)

file(COPY "${CURRENT_INSTALLED_DIR}/share/coin-or-buildtools/" DESTINATION "${SOURCE_PATH}")

# IpMumpsSolverInterface.cpp includes "mpi.h" even against sequential MUMPS.
# Write a minimal type-only stub so the configure link test still fails
# (keeping IPOPT sequential) while compilation succeeds.
file(WRITE "${SOURCE_PATH}/src/mpi.h"
"/* Sequential MUMPS MPI type stub */
#ifndef IPOPT_MUMPS_SEQ_MPI_STUB_H
#define IPOPT_MUMPS_SEQ_MPI_STUB_H
typedef int MPI_Comm;
typedef int MPI_Datatype;
typedef int MPI_Op;
typedef int MPI_Status;
typedef int MPI_Request;
typedef int MPI_Info;
typedef int MPI_Group;
typedef int MPI_Errhandler;
typedef long MPI_Aint;
typedef long MPI_Offset;
#define MPI_COMM_WORLD   ((MPI_Comm)0)
#define MPI_COMM_SELF    ((MPI_Comm)1)
#define MPI_SUCCESS      0
#define MPI_UNDEFINED    (-1)
#define MPI_DOUBLE       ((MPI_Datatype)0)
#define MPI_INT          ((MPI_Datatype)0)
#define MPI_SUM          ((MPI_Op)0)
#define MPI_STATUS_IGNORE ((MPI_Status*)0)
#endif
")

set(ENV{ACLOCAL} "aclocal -I \"${SOURCE_PATH}/BuildTools\"")

# Linux: system libmumps-seq-dev
find_library(_dmumps_lib NAMES dmumps_seq dmumps
    HINTS
        /usr/lib/${CMAKE_LIBRARY_ARCHITECTURE}
        /usr/lib/x86_64-linux-gnu
        /usr/lib/aarch64-linux-gnu
        /usr/lib
    NO_DEFAULT_PATH)
if(_dmumps_lib)
    get_filename_component(_mumps_lib_dir "${_dmumps_lib}" DIRECTORY)
else()
    set(_mumps_lib_dir "/usr/lib/x86_64-linux-gnu")
endif()
if(_dmumps_lib MATCHES "_seq")
    set(_mumps_sfx "_seq")
else()
    set(_mumps_sfx "")
endif()
set(_mumps_cflags "-I/usr/include")
set(_mumps_libs "-L${_mumps_lib_dir} -ldmumps${_mumps_sfx} -lmumps_common${_mumps_sfx}")
set(LAPACK_OPTION "--with-lapack=-L${CURRENT_INSTALLED_DIR}/lib -llapack -lopenblas -lgfortran -lm")

set(ENV{MUMPS_CFLAGS} "${_mumps_cflags}")
set(ENV{MUMPS_LIBS} "${_mumps_libs}")
set(ENV{COINMUMPS_CFLAGS} "${_mumps_cflags}")
set(ENV{COINMUMPS_LIBS} "${_mumps_libs}")

vcpkg_configure_make(
    SOURCE_PATH "${SOURCE_PATH}"
    AUTOCONFIG
    OPTIONS
      --without-spral
      --without-hsl
      --without-asl
      ${LAPACK_OPTION}
      --with-mumps
      --enable-relocatable
      --disable-f77
      --disable-java
)

vcpkg_install_make()
vcpkg_copy_pdbs()
vcpkg_fixup_pkgconfig()

file(REMOVE_RECURSE "${CURRENT_PACKAGES_DIR}/debug/include")
file(REMOVE_RECURSE "${CURRENT_PACKAGES_DIR}/debug/share")

file(INSTALL "${SOURCE_PATH}/LICENSE" DESTINATION "${CURRENT_PACKAGES_DIR}/share/${PORT}" RENAME copyright)
