using System;
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
                return pagmonet_ipopt_available();
            }
            catch
            {
                // Native probe unavailable (e.g. PagmoWrapper itself could not be loaded).
                return false;
            }
        }

        [DllImport("PagmoWrapper", CallingConvention = CallingConvention.Cdecl)]
        [return: MarshalAs(UnmanagedType.I1)]
        private static extern bool pagmonet_ipopt_available();

        private static bool HasExport(string symbol)
            => _libraryHandle != IntPtr.Zero && NativeLibrary.TryGetExport(_libraryHandle, symbol, out _);
    }
}
