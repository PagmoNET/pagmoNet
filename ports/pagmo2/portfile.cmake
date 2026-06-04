vcpkg_from_github(
    OUT_SOURCE_PATH SOURCE_PATH
    REPO esa/pagmo2
    REF "v${VERSION}"
    SHA512 9ebe7f63b907607ea5762e56a884be62630efaca3f45d9ba9ad85ca1818d60d09864422bd075c2653aea1a14609fe9ad6520297aee5a00e07fa88df45872cef9
    HEAD_REF master
    PATCHES
        0001-doxygen.patch
        0002-find-tbb.patch
        0003-disable-werror.patch
        0004-support-eigen3-5.patch
)

vcpkg_check_features(OUT_FEATURE_OPTIONS FEATURE_OPTIONS
    FEATURES
        nlopt PAGMO_WITH_NLOPT
        ipopt PAGMO_WITH_IPOPT
)

# When IPOPT is not a requested feature, disable find_package(IPOPT) entirely so
# that pagmo2's cmake cannot auto-detect a system IPOPT installation and bake
# PAGMO_WITH_IPOPT into config.hpp.  Without this guard, system IPOPT (common on
# Linux/macOS runners) causes config.hpp to declare PAGMO_WITH_IPOPT while the
# actual IPOPT symbols are absent from the static pagmo library.
if(NOT "ipopt" IN_LIST FEATURES)
    list(APPEND FEATURE_OPTIONS -DCMAKE_DISABLE_FIND_PACKAGE_IPOPT=ON)
endif()

string(COMPARE EQUAL "${VCPKG_LIBRARY_LINKAGE}" "static" PAGMO_BUILD_STATIC_LIBRARY)
vcpkg_cmake_configure(
    SOURCE_PATH "${SOURCE_PATH}"
    OPTIONS
        ${FEATURE_OPTIONS}
        -DPAGMO_BUILD_TESTS=OFF
        -DPAGMO_BUILD_BENCHMARKS=OFF
        -DPAGMO_BUILD_TUTORIALS=OFF
        -DPAGMO_WITH_EIGEN3=ON
        -DPAGMO_BUILD_STATIC_LIBRARY=${PAGMO_BUILD_STATIC_LIBRARY}
)

vcpkg_cmake_install()

vcpkg_copy_pdbs()
vcpkg_cmake_config_fixup(CONFIG_PATH "lib/cmake/pagmo")

file(REMOVE_RECURSE "${CURRENT_PACKAGES_DIR}/debug/share")
file(REMOVE_RECURSE "${CURRENT_PACKAGES_DIR}/debug/include")

vcpkg_install_copyright(FILE_LIST "${SOURCE_PATH}/COPYING.lgpl3" "${SOURCE_PATH}/COPYING.gpl3")
file(INSTALL "${CMAKE_CURRENT_LIST_DIR}/usage" DESTINATION "${CURRENT_PACKAGES_DIR}/share/${PORT}")
