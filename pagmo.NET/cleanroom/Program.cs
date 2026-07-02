// Clean-room consumer of the *published* Pagmo.NET (base, IPOPT-free) package.
//
// This program is built and run on a machine with NO dev tools and NO
// DYLD_LIBRARY_PATH/LD_LIBRARY_PATH/PATH hints pointing at a build tree — exactly what
// an end user has after `dotnet add package Pagmo.NET`. The base package is NLopt-only
// and statically linked, so there is no dynamic dependency closure to bundle; what this
// gate proves is narrower but real: the SWIG bindings were regenerated (not a
// binding-less package) and the native wrapper for this RID was actually injected into
// the nupkg and loads. If either regressed, this fails with a DllNotFound/dyld error or
// a missing-type error instead of a green build.
//
// Exit codes: 0 = solved; 1 = ran but did not converge; 2 = NLopt not available.

using System;
using pagmo;

if (!OptionalSolverAvailability.IsNloptAvailable)
{
    Console.Error.WriteLine("FAIL: NLopt reports unavailable in the published base package.");
    return 2;
}

using var prob = new QuadraticProblem();
// slsqp is a gradient-based local optimizer; with the analytic gradient below it drives
// this convex quadratic to the optimum in a handful of iterations — deterministic enough
// for a smoke gate.
using var algo = new nlopt("slsqp");

using var pop = new population(prob, 1u, 42u);
using var evolved = algo.evolve(pop);

double fBest = evolved.champion_f()[0];
Console.WriteLine($"NLopt (slsqp) clean-room solve: f={fBest:E6}");

if (fBest < 1e-6)
{
    Console.WriteLine("PASS: clean-room NLopt solve succeeded.");
    return 0;
}

Console.Error.WriteLine($"FAIL: solve did not converge near f*=0 (f={fBest:E6}).");
return 1;

// Minimal unconstrained differentiable problem: minimise x² + (y-3)², optimum (0,3), f*=0.
sealed class QuadraticProblem : ManagedProblemBase
{
    private readonly DoubleVector _lb = new(new[] { -5.0, -5.0 });
    private readonly DoubleVector _ub = new(new[] {  5.0,  5.0 });

    public override string get_name() => "QuadraticProblem";
    public override PairOfDoubleVectors get_bounds() => new(_lb, _ub);
    public override ThreadSafety get_thread_safety() => ThreadSafety.Constant;

    public override DoubleVector fitness(DoubleVector x)
        => new(new[] { x[0] * x[0] + (x[1] - 3.0) * (x[1] - 3.0) });

    public override bool has_gradient() => true;
    public override DoubleVector gradient(DoubleVector x)
        => new(new[] { 2.0 * x[0], 2.0 * (x[1] - 3.0) });

    public override bool has_gradient_sparsity() => true;
    public override SparsityPattern gradient_sparsity() => Sparsity((0u, 0u), (0u, 1u));
}
