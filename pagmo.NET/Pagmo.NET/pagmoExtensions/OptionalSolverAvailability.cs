using System;
using System.IO;
using System.Runtime.InteropServices;

namespace pagmo
{
    /// <summary>
    /// Provides native availability checks for optional solvers that may not be compiled
    /// into every build of the PagmoWrapper native library. Use these before attempting
    /// to construct or call optional solver types to avoid <see cref="EntryPointNotFoundException"/>.
    /// </summary>
    public static class OptionalSolverAvailability
    {
        private static readonly IntPtr _libraryHandle = LoadLibrary();

        private static IntPtr LoadLibrary()
        {
            NativeLibrary.TryLoad("PagmoWrapper", typeof(algorithm).Assembly, null, out var handle);
            return handle;
        }

        /// <summary>
        /// Gets whether NLopt is available in the native PagmoWrapper library.
        /// When <see langword="false"/>, the <c>pagmo.nlopt</c> managed type exists but its
        /// native entry points are absent and cannot be called.
        /// </summary>
        public static bool IsNloptAvailable { get; } = HasExport("CSharp_pagmo_new_nlopt__SWIG_0");

        /// <summary>
        /// Gets whether IPOPT can actually be used at runtime. The <c>pagmo.ipopt</c> algorithm
        /// ships in every build, but it loads its solver library (libipopt) at runtime via
        /// <c>dlopen</c>; this returns <see langword="true"/> only when that library can be
        /// loaded — i.e. the <c>Pagmo.NET.Ipopt</c> companion package is referenced, a system
        /// IPOPT is installed, or <c>PAGMONET_IPOPT_LIBRARY</c> points at one. When
        /// <see langword="false"/>, constructing <c>pagmo.ipopt</c> still succeeds but calling
        /// <c>evolve()</c> throws until a libipopt is provided.
        /// </summary>
        public static bool IsIpoptAvailable { get; } = ProbeIpopt();

        private static bool ProbeIpopt()
        {
            try
            {
                HintCompanionIpoptLocation();
                return pagmonet_ipopt_available();
            }
            catch
            {
                // Native probe unavailable (e.g. PagmoWrapper itself could not be loaded).
                return false;
            }
        }

        /// <summary>
        /// Under a framework-dependent build with no RuntimeIdentifier (e.g. a plain
        /// <c>dotnet run</c>), the <c>Pagmo.NET.Ipopt</c> companion's libipopt is restored to the
        /// NuGet cache but is NOT copied next to the native wrapper — so the wrapper's own loader,
        /// which searches its own directory, cannot find it, and IPOPT silently reports unavailable.
        /// .NET exposes the exact directories it probes for native dependencies (which include the
        /// companion's <c>runtimes/&lt;rid&gt;/native</c> folder regardless of RID) via AppContext;
        /// we scan those and hand the wrapper the full path through <c>PAGMONET_IPOPT_LIBRARY</c>.
        /// An explicit override, or a co-located/system libipopt, still wins: we never overwrite an
        /// existing value, and this only supplies a path the wrapper would otherwise miss.
        /// </summary>
        private static void HintCompanionIpoptLocation()
        {
            if (!string.IsNullOrEmpty(Environment.GetEnvironmentVariable("PAGMONET_IPOPT_LIBRARY")))
            {
                return; // respect an explicit user override
            }
            if (AppContext.GetData("NATIVE_DLL_SEARCH_DIRECTORIES") is not string dirs || dirs.Length == 0)
            {
                return;
            }

            var names =
                RuntimeInformation.IsOSPlatform(OSPlatform.Windows)
                    ? new[] { "ipopt-3.dll", "ipopt.dll", "libipopt-3.dll", "libipopt.dll" }
                : RuntimeInformation.IsOSPlatform(OSPlatform.OSX)
                    ? new[] { "libipopt.dylib", "libipopt.3.dylib" }
                    : new[] { "libipopt.so", "libipopt.so.3", "libipopt.so.1" };

            foreach (var dir in dirs.Split(Path.PathSeparator, StringSplitOptions.RemoveEmptyEntries))
            {
                foreach (var name in names)
                {
                    var candidate = Path.Combine(dir, name);
                    if (File.Exists(candidate))
                    {
                        Environment.SetEnvironmentVariable("PAGMONET_IPOPT_LIBRARY", candidate);
                        return;
                    }
                }
            }
        }

        [DllImport("PagmoWrapper", CallingConvention = CallingConvention.Cdecl)]
        [return: MarshalAs(UnmanagedType.I1)]
        private static extern bool pagmonet_ipopt_available();

        private static bool HasExport(string symbol)
            => _libraryHandle != IntPtr.Zero && NativeLibrary.TryGetExport(_libraryHandle, symbol, out _);
    }
}
