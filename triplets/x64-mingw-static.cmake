# MinGW x64 static build for Windows.
# All vcpkg dependencies are linked statically into the shared library.
# Required for IPOPT+MUMPS: MSYS2's MUMPS static archives (.a) can only be
# linked by GCC's ld — MSVC's link.exe cannot read GNU archive format.
# MSYS2 MinGW64 (C:\msys64\mingw64\bin) must be on PATH when building.
set(VCPKG_TARGET_ARCHITECTURE x64)
set(VCPKG_CRT_LINKAGE dynamic)
set(VCPKG_LIBRARY_LINKAGE static)
set(VCPKG_ENV_PASSTHROUGH PATH)
set(VCPKG_CHAINLOAD_TOOLCHAIN_FILE "${VCPKG_ROOT_DIR}/scripts/toolchains/mingw.cmake")
# Skip debug builds: clapack (LAPACK fallback for MinGW) fails to compile in debug mode.
# We only need release libraries since PagmoWrapper.dll is built in Release.
set(VCPKG_BUILD_TYPE release)
