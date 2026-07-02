// Clean-room consumer of the *published* Pagmo.NET.Ipopt package.
//
// This program is built and run on a machine with NO dev tools, NO conda, and NO
// DYLD_LIBRARY_PATH/LD_LIBRARY_PATH/PATH hints pointing at a build tree — exactly
// what an end user has after `dotnet add package Pagmo.NET.Ipopt`. If the package
// is self-contained (wrapper + its IPOPT/MUMPS/BLAS/gfortran dependency closure all
// resolve from runtimes/<rid>/native), this solve runs to completion. If anything is
// missing, the process fails with a DllNotFound/dyld error instead of a green build —
// which is the entire point of this gate.
//
// Exit codes: 0 = solved; 1 = ran but did not converge; 2 = IPOPT not available.

using System;
using pagmo;

if (!OptionalSolverAvailability.IsIpoptAvailable)
{
    Console.Error.WriteLine("FAIL: IPOPT reports unavailable in the published package.");
    return 2;
}

using var prob = new QuadraticProblem();
using var algo = new ipopt();
algo.set_integer_option("print_level", 0);
algo.set_string_option("linear_solver", "mumps"); // exercises the bundled MUMPS closure

using var pop = new population(prob, 1u, 42u);
using var evolved = algo.evolve(pop);

int code = algo.GetLastOptimizationResultCode();
double fBest = evolved.champion_f()[0];
Console.WriteLine($"IPOPT result code={code}, f={fBest:E6}");

// 0 = Solve_Succeeded, 1 = Solved_To_Acceptable_Level.
if ((code == 0 || code == 1) && fBest < 1e-6)
{
    Console.WriteLine("PASS: clean-room IPOPT solve succeeded.");
    return 0;
}

Console.Error.WriteLine($"FAIL: solve did not converge (code={code}, f={fBest:E6}).");
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

    // IPOPT needs explicit sparsity to set up the NLP.
    public override bool has_gradient_sparsity() => true;
    public override SparsityPattern gradient_sparsity() => Sparsity((0u, 0u), (0u, 1u));
}
