# Static libraries with the STATIC CRT (/MT). Used for the Java JNI (pagmonet4j.dll) so the DLL
# has NO msvcp140/vcruntime140 dependency at all.
#
# Why: some JDK distributions (notably Amazon Corretto) bundle their own, older VC++ runtime
# (vcruntime140/msvcp140) in bin/ and jvm.dll loads it at startup, before the system copy. A
# dynamic-CRT (/MD) native then binds to that stale runtime and access-violates the JVM at the
# first C++ call. A fully static CRT sidesteps the whole problem, so the jar works on every JDK.
# (The C# PagmoWrapper.dll stays /MD via x64-windows-static-md -- .NET always resolves the system
# CRT, so it doesn't have this issue.)
#
# Release-only: pagmo2's Debug (/MTd) config fails to build under a full static CRT on CI runners,
# and we only ship Release. Skipping Debug both fixes that and halves the build.
set(VCPKG_TARGET_ARCHITECTURE x64)
set(VCPKG_CRT_LINKAGE static)
set(VCPKG_LIBRARY_LINKAGE static)
set(VCPKG_BUILD_TYPE release)
# Pin to the VS 2022 (v143) toolset, mirroring x64-windows-static-md: it compiles pagmo2 cleanly on
# runners with VS 2026 installed, and (unlike the unpinned default v145) produces proper static-UCRT
# references instead of dynamic __imp_ imports that fail to link against a /MT wrapper.
set(VCPKG_PLATFORM_TOOLSET v143)
