using System.Collections.Generic;

namespace pagmo;

/// <summary>
/// Represents pso. Uses pagmo-native semantics. See docs/api-reference.md for upstream links.
/// </summary>
public partial class pso : IAlgorithm
{
    /// <summary>
    /// Represents a typed algorithm log entry projected from pagmo runtime data.
    /// </summary>
    public readonly record struct PsoLogLine(
        uint Generation,
        ulong FunctionEvaluations,
        double BestFitness,
        double Inertia,
        double Cognitive,
        double Social) : IAlgorithmLogLine
    {
        /// <summary>
        /// Uses pagmo-native semantics. See docs/api-reference.md for upstream links.
        /// </summary>
        public string AlgorithmName => "pso";
        /// <summary>
        /// Gets a generic field map for algorithm-agnostic log processing.
        /// </summary>
        public IReadOnlyDictionary<string, object> RawFields => new Dictionary<string, object>
        {
            ["generation"] = Generation,
            ["function_evaluations"] = FunctionEvaluations,
            ["best_fitness"] = BestFitness,
            ["inertia"] = Inertia,
            ["cognitive"] = Cognitive,
            ["social"] = Social
        };
        /// <summary>
        /// Invokes the corresponding pagmo API. See docs/api-reference.md for upstream links.
        /// </summary>
        public string ToDisplayString() => $"gen={Generation}, fevals={FunctionEvaluations}, best={BestFitness}";
    }

    /// <summary>
    /// Invokes the corresponding pagmo API. See docs/api-reference.md for upstream links.
    /// </summary>
    public IReadOnlyList<PsoLogLine> GetTypedLogLines()
    {
        var count = get_log_entry_count();
        var lines = new List<PsoLogLine>(count);
        for (var i = 0; i < count; i++)
        {
            using var entry = get_log_entry(i);
            lines.Add(new PsoLogLine(entry.gen, entry.fevals, entry.best, entry.inertia, entry.cognitive, entry.social));
        }
        return lines;
    }

    /// <summary>
    /// Invokes the corresponding pagmo API. See docs/api-reference.md for upstream links.
    /// </summary>
    public IReadOnlyList<IAlgorithmLogLine> GetLogLines()
    {
        var typedLines = GetTypedLogLines();
        var projected = new List<IAlgorithmLogLine>(typedLines.Count);
        foreach (var line in typedLines) projected.Add(line);
        return projected;
    }
}

