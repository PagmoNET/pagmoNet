# Bridge port: does not build MUMPS from source.
# Creates coinmumps.pc pointing to the system/MSYS2-installed MUMPS so that
# IPOPT's AC_COIN_CHK_PKG([MUMPS],...,[coinmumps]) succeeds via pkg-config.
#
# Prerequisites (installed before vcpkg runs):
#   Linux:   apt-get install libmumps-dev
#   macOS:   brew tap brewsci/num && brew install brewsci-mumps
#   Windows: C:\msys64\usr\bin\pacman.exe -S --noconfirm mingw-w64-x86_64-mumps

set(VCPKG_BUILD_TYPE release)

if(VCPKG_TARGET_IS_OSX)
    # MUMPS installed via: micromamba install -c conda-forge -p ~/mumps-env mumps-seq
    # The MUMPS_PREFIX env var is set by the CI step; fall back to the default path.
    set(_mumps_prefix "$ENV{MUMPS_PREFIX}")
    if(NOT _mumps_prefix OR NOT EXISTS "${_mumps_prefix}")
        set(_mumps_prefix "$ENV{HOME}/mumps-env")
    endif()
    if(NOT EXISTS "${_mumps_prefix}/include/dmumps_c.h")
        message(FATAL_ERROR
            "MUMPS not found at ${_mumps_prefix}. "
            "Run: micromamba install -c conda-forge -p ~/mumps-env mumps-seq")
    endif()
    set(_mumps_inc "${_mumps_prefix}/include")
    # conda-forge mumps-seq on ARM macOS installs libdmumps_seq.dylib, not libdmumps.dylib.
    find_library(_dmumps_mac NAMES dmumps_seq dmumps
        HINTS "${_mumps_prefix}/lib" NO_DEFAULT_PATH)
    if(_dmumps_mac MATCHES "_seq")
        set(_mumps_sfx "_seq")
    else()
        set(_mumps_sfx "")
    endif()
    unset(_dmumps_mac)
    set(_mumps_libs "-L${_mumps_prefix}/lib -ldmumps${_mumps_sfx} -lmumps_common${_mumps_sfx}")

elseif(VCPKG_TARGET_IS_LINUX)
    set(_mumps_inc "/usr/include")
    # Try the sequential package (libmumps-seq-dev) first — its libraries have
    # a _seq suffix and carry their own MPI stubs, so no real MPI is needed at
    # runtime.  Fall back to the plain name (libmumps-dev) if _seq is absent.
    find_library(_dmumps_lib NAMES dmumps_seq dmumps
        HINTS
            /usr/lib/${CMAKE_LIBRARY_ARCHITECTURE}
            /usr/lib/x86_64-linux-gnu
            /usr/lib/aarch64-linux-gnu
            /usr/lib
        NO_DEFAULT_PATH)
    if(NOT _dmumps_lib)
        message(FATAL_ERROR
            "libdmumps(_seq) not found. Install: apt-get install libmumps-seq-dev")
    endif()
    get_filename_component(_mumps_lib_dir "${_dmumps_lib}" DIRECTORY)
    # Derive the library name suffix (_seq or empty) from whichever was found.
    if(_dmumps_lib MATCHES "_seq")
        set(_mumps_sfx "_seq")
    else()
        set(_mumps_sfx "")
    endif()
    # On Linux the system MUMPS is a shared library; transitive deps (openblas,
    # gfortran, scotch, metis) are already encoded in the .so.
    set(_mumps_libs "-L${_mumps_lib_dir} -ldmumps${_mumps_sfx} -lmumps_common${_mumps_sfx}")

elseif(VCPKG_TARGET_IS_WINDOWS)
    # Use pre-built MUMPS from the system MSYS2/MinGW64 installation.
    # Install it before running the build:
    #   C:\msys64\usr\bin\pacman.exe -S --noconfirm mingw-w64-x86_64-mumps
    set(_msys2_root "C:/msys64/mingw64")
    if(NOT EXISTS "${_msys2_root}/include/dmumps_c.h")
        message(FATAL_ERROR
            "MUMPS not found at ${_msys2_root}/include. "
            "Install via: C:\\msys64\\usr\\bin\\pacman.exe -S --noconfirm mingw-w64-x86_64-mumps")
    endif()
    # Use MSYS2 POSIX paths (/c/msys64/...) because coinmumps.pc is consumed
    # inside vcpkg's MSYS2 autotools build environment.
    set(_mumps_inc "/c/msys64/mingw64/include")
    # Windows MUMPS from MSYS2 is static; list all transitive deps.
    set(_mumps_libs "-L/c/msys64/mingw64/lib -ldmumps -lmumps_common -lpord -lgfortran -lopenblas")

else()
    message(FATAL_ERROR "coin-or-mumps bridge port: unsupported platform.")
endif()

set(_pc_content
"Name: coinmumps
Description: MUMPS sparse direct solver (system bridge for COIN-OR IPOPT)
Version: 1.0
Cflags: -I${_mumps_inc}
Libs: ${_mumps_libs}
")

# Write to both release and debug pkgconfig dirs. vcpkg_configure_make adds
# the debug pkgconfig path to PKG_CONFIG_PATH when building the debug variant,
# so without this the debug configure cannot find coinmumps via pkg-config.
file(MAKE_DIRECTORY "${CURRENT_PACKAGES_DIR}/lib/pkgconfig")
file(WRITE "${CURRENT_PACKAGES_DIR}/lib/pkgconfig/coinmumps.pc" "${_pc_content}")
file(MAKE_DIRECTORY "${CURRENT_PACKAGES_DIR}/debug/lib/pkgconfig")
file(WRITE "${CURRENT_PACKAGES_DIR}/debug/lib/pkgconfig/coinmumps.pc" "${_pc_content}")

file(MAKE_DIRECTORY "${CURRENT_PACKAGES_DIR}/include/coin-or")

file(WRITE "${CURRENT_PACKAGES_DIR}/share/${PORT}/copyright"
    "Bridge port — system MUMPS is used. See your system package manager for license details.")
