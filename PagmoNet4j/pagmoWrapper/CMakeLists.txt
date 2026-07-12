cmake_minimum_required(VERSION 3.22)
project(pagmonet4j VERSION 1.0.0)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(APPLE)
    # Xcode 16.4+ removed libm.tbd from the SDK. cmake 4.x (CMP0060 NEW, no longer
    # optional) resolves "m" to the full SDK path and adds it as a Makefile
    # prerequisite; if the file is missing, make fails with "No rule to make
    # target libm.tbd". The SDK itself is SIP-protected so we can't drop a stub
    # there - instead create the stub in the build tree and make cmake find it
    # FIRST by prepending it to the implicit link directories and excluding the
    # (missing) SDK path from the search list.
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

# ── JNI headers ───────────────────────────────────────────────────────────────
find_package(JNI REQUIRED)

# ── SWIG (required — wrap file is generated, not committed) ──────────────────
find_package(SWIG 4.0 REQUIRED)
message(STATUS "SWIG ${SWIG_VERSION} — wrap file will be generated from .i sources")

# ── pagmo2 + dependencies via vcpkg ──────────────────────────────────────────
# We never link IPOPT (the ipopt algorithm is loaded at runtime via dlopen -- see
# deferred_ipopt.cpp). If the installed pagmo was built with IPOPT, its export file may
# still reference pagmo::IPOPT::libipopt; pre-create a harmless INTERFACE stub so
# find_package(Pagmo) resolves without pulling in any IPOPT library.
if(NOT TARGET pagmo::IPOPT::libipopt)
    add_library(pagmo::IPOPT::libipopt INTERFACE IMPORTED)
endif()

find_package(Pagmo REQUIRED)
find_package(NLopt CONFIG QUIET)

if(APPLE)
    # Xcode 16.4+ removed libm.tbd from the SDK. Any imported target whose interface
    # link libraries reference libm -- as "m", "-lm", or a path that cmake resolved
    # to the now-missing libm.tbd / libm.dylib -- makes cmake emit that absolute path
    # as a Makefile prerequisite, producing "No rule to make target libm.tbd".
    # Math lives in libSystem.B on macOS (always linked), so strip every such
    # reference from ALL imported targets in this directory before the link line is
    # generated. This is broader than stripping Pagmo::pagmo alone (which is what the
    # C# PagmoWrapper build does) because libpagmonet4j links a wider set of imported
    # targets (NLopt, the pre-created IPOPT target, pagmo's transitive export targets)
    # and the stray libm.tbd path can come in through any of them.
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

# ── Keep IPOPT out of the link ────────────────────────────────────────────────
# We never link IPOPT (the ipopt algorithm reaches libipopt at runtime via dlopen).
# Strip PAGMO_WITH_IPOPT (and PAGMO_WITH_NLOPT, which the wrapper controls itself) from
# Pagmo::pagmo's exported INTERFACE_COMPILE_DEFINITIONS so pagmo's ipopt.hpp / nlopt.hpp
# are not accidentally enabled by how the upstream pagmo happened to be built.
get_target_property(_pagmo_iface_defs Pagmo::pagmo INTERFACE_COMPILE_DEFINITIONS)
if(_pagmo_iface_defs)
    list(REMOVE_ITEM _pagmo_iface_defs PAGMO_WITH_IPOPT PAGMO_WITH_NLOPT)
    set_target_properties(Pagmo::pagmo PROPERTIES
        INTERFACE_COMPILE_DEFINITIONS "${_pagmo_iface_defs}")
endif()
unset(_pagmo_iface_defs)

# Strip any IPOPT library from Pagmo's transitive link deps so ipopt.lib is never pulled in.
get_target_property(_pagmo_iface_libs Pagmo::pagmo INTERFACE_LINK_LIBRARIES)
if(_pagmo_iface_libs)
    list(FILTER _pagmo_iface_libs EXCLUDE REGEX "ipopt|IPOPT")
    set_target_properties(Pagmo::pagmo PROPERTIES
        INTERFACE_LINK_LIBRARIES "${_pagmo_iface_libs}")
endif()
unset(_pagmo_iface_libs)

# ── Locate pagmoNet ───────────────────────────────────────────────────────────
# Probe order: (1) pagmoNet submodule inside PagmoNet4j (standalone),
#              (2) monorepo root two levels up from pagmoWrapper/,
#              (3) pagmoNet cloned alongside PagmoNet4j (legacy standalone).
set(PAGMONET_ROOT "${CMAKE_SOURCE_DIR}/../pagmoNet")
if(NOT EXISTS "${PAGMONET_ROOT}/native/CMakeLists.txt")
    set(PAGMONET_ROOT "${CMAKE_SOURCE_DIR}/../..")
endif()
if(NOT EXISTS "${PAGMONET_ROOT}/native/CMakeLists.txt")
    set(PAGMONET_ROOT "${CMAKE_SOURCE_DIR}/../../pagmoNet")
endif()
if(NOT EXISTS "${PAGMONET_ROOT}/native/CMakeLists.txt")
    message(FATAL_ERROR
        "Cannot locate shared native sources. Expected pagmoNet submodule, monorepo layout, or pagmoNet cloned alongside PagmoNet4j.")
endif()

set(PAGMOWRAPPER_SRC "${PAGMONET_ROOT}/native")
file(GLOB PAGMOWRAPPER_SOURCES "${PAGMOWRAPPER_SRC}/*.cpp")

# ── Generate JNI wrap file from .i sources ───────────────────────────────────
# SWIG Java output goes to the build directory — NOT the source tree.
# The committed Java files in core/src/generated/java/ are the authoritative API
# source (they contain hand-written extensions beyond pure SWIG output).
# To review SWIG's Java output: cmake --build . then inspect swig_java/ in the build dir.
set(SWIG_JAVA_OUTDIR "${CMAKE_CURRENT_BINARY_DIR}/swig_java")
file(MAKE_DIRECTORY "${SWIG_JAVA_OUTDIR}")
set(JNI_WRAP_SRC "${CMAKE_CURRENT_BINARY_DIR}/pagmonet4j_wrap.cxx")

# Collect .i file dependencies so cmake regenerates when they change
file(GLOB_RECURSE SWIG_INTERFACE_FILES "${PAGMONET_ROOT}/swig/*.i")

add_custom_command(
    OUTPUT "${JNI_WRAP_SRC}"
    COMMAND ${SWIG_EXECUTABLE}
        -java -c++
        -package "io.github.pagmonet.pagmonet4j"
        -outdir "${SWIG_JAVA_OUTDIR}"
        -o "${JNI_WRAP_SRC}"
        "-I${PAGMONET_ROOT}/swig"
        "-I${PAGMONET_ROOT}/native"
        "${PAGMONET_ROOT}/swig/Pagmo4jSwigInterface.i"
    DEPENDS ${SWIG_INTERFACE_FILES}
    COMMENT "Generating SWIG JNI wrapper from .i sources (ipopt bindings are unconditional)"
    VERBATIM)

# ── Build the JNI shared library ─────────────────────────────────────────────
add_library(pagmonet4j SHARED
    ${PAGMOWRAPPER_SOURCES}
    "${JNI_WRAP_SRC}"
)

target_include_directories(pagmonet4j PRIVATE
    ${JNI_INCLUDE_DIRS}
    ${PAGMOWRAPPER_SRC}
    "${PAGMONET_ROOT}/swig"
)

target_link_libraries(pagmonet4j PRIVATE
    Pagmo::pagmo
    $<$<TARGET_EXISTS:NLopt::nlopt>:NLopt::nlopt>
    ${CMAKE_DL_LIBS}   # dlopen/dlsym for deferred_ipopt on Unix; no-op on Windows
)

# We never link IPOPT, so PAGMO_WITH_IPOPT stays undefined (pagmo's ipopt.hpp is not used;
# the deferred_ipopt UDA reaches libipopt via dlopen). NLopt is controlled by the wrapper.
if(MSVC)
    target_compile_options(pagmonet4j PRIVATE /UPAGMO_WITH_NLOPT /UPAGMO_WITH_IPOPT /bigobj)
else()
    target_compile_options(pagmonet4j PRIVATE -UPAGMO_WITH_NLOPT -UPAGMO_WITH_IPOPT)
endif()
target_compile_definitions(pagmonet4j PRIVATE PAGMO_STATIC_BUILD)

# (No IPOPT link block: the ipopt algorithm reaches IPOPT at runtime via dlopen. The loader
# lives in deferred_ipopt.cpp / dynamic_library.cpp, both compiled from the shared native dir.)

if(APPLE)
    set_target_properties(pagmonet4j PROPERTIES
        BUILD_WITH_INSTALL_RPATH ON
        INSTALL_RPATH "@loader_path"
        MACOSX_RPATH ON)
elseif(UNIX)
    set_target_properties(pagmonet4j PROPERTIES
        BUILD_WITH_INSTALL_RPATH ON
        INSTALL_RPATH "$ORIGIN")
endif()

if(WIN32)
    set_target_properties(pagmonet4j PROPERTIES PREFIX "")
endif()

# Flatten the output directory so the DLL always lands in CMAKE_BINARY_DIR (e.g. win-build/)
# regardless of generator. The Visual Studio generator adds per-config subdirs (Release/, Debug/)
# by default, which breaks the PAGMO4J_NATIVE_DIR lookup in the test runner.
set_target_properties(pagmonet4j PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY         "${CMAKE_BINARY_DIR}"
    RUNTIME_OUTPUT_DIRECTORY_RELEASE "${CMAKE_BINARY_DIR}"
    RUNTIME_OUTPUT_DIRECTORY_DEBUG   "${CMAKE_BINARY_DIR}"
    LIBRARY_OUTPUT_DIRECTORY         "${CMAKE_BINARY_DIR}"
    LIBRARY_OUTPUT_DIRECTORY_RELEASE "${CMAKE_BINARY_DIR}"
    LIBRARY_OUTPUT_DIRECTORY_DEBUG   "${CMAKE_BINARY_DIR}"
)

install(TARGETS pagmonet4j LIBRARY DESTINATION lib RUNTIME DESTINATION bin)
