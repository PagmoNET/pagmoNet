using NUnit.Framework;
using pagmo;
using Tests.Pagmo.NET.TestProblems;

namespace Tests.Pagmo.NET.Algorithms;

[TestFixture]
public class Test_sga : TestAlgorithmBase
{
    public override IAlgorithm CreateAlgorithm()
    {
        return new pagmo.sga(10u, 0.5);
    }

    [Test]
    public override void TestNameIsCorrect()
    {
        using (var problem = new TwoDimensionalSingleObjectiveProblemWrapper())
        using (var algorithm = CreateAlgorithm(problem))
        {
            Assert.AreEqual("SGA: Genetic Algorithm", algorithm.get_name());
        }
    }

    public override bool SupportsGeneration => false;

    /// <inheritdoc />
    public override bool Constrained => false;

    /// <inheritdoc />
    public override bool Unconstrained => true;

    /// <inheritdoc />
    public override bool SingleObjective => true;

    /// <inheritdoc />
    public override bool MultiObjective => false;

    /// <inheritdoc />
    public override bool IntegerProgramming => true;

    /// <inheritdoc />
    public override bool Stochastic => true;

    [Test]
    public void TypedAndGenericLogsAreExposed()
    {
        using var problem = new TwoDimensionalSingleObjectiveProblemWrapper();
        using var algorithm = new pagmo.sga(10u, 0.5);
        using var population = new population(problem, 48u, 2u);

        algorithm.set_seed(2u);
        // verbosity=1 only logs when improvement>0 (non-deterministic); verbosity=2 logs every 2nd gen unconditionally
        algorithm.set_verbosity(2u);

        using var _ = algorithm.evolve(population);

        var typedLines = algorithm.GetTypedLogLines();
        Assert.That(typedLines.Count, Is.GreaterThan(0), "sga verbosity should produce at least one log line");

        IAlgorithm algorithmInterface = algorithm;
        var genericLines = algorithmInterface.GetLogLines();
        Assert.That(genericLines.Count, Is.EqualTo(typedLines.Count));

        var raw = genericLines[0].RawFields;
        Assert.That(genericLines[0].AlgorithmName, Is.EqualTo("sga"));
        Assert.That(raw.ContainsKey("generation"), Is.True);
        Assert.That(raw.ContainsKey("function_evaluations"), Is.True);
        Assert.That(raw.ContainsKey("best_fitness"), Is.True);
        Assert.That(raw.ContainsKey("improvement"), Is.True);
        Assert.That(genericLines[0].ToDisplayString(), Does.Contain("gen="));

        Assert.That((uint)raw["generation"], Is.EqualTo(typedLines[0].Generation));
        Assert.That((ulong)raw["function_evaluations"], Is.EqualTo(typedLines[0].FunctionEvaluations));
        Assert.That((double)raw["best_fitness"], Is.EqualTo(typedLines[0].BestFitness));
    }
}
