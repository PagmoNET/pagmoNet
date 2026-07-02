using System.Collections.Generic;

namespace pagmo;

/// <summary>
/// Represents compass_search. Uses pagmo-native semantics. See docs/api-reference.md for upstream links.
/// </summary>
public partial class compass_search : IAlgorithm
{
    /// <summary>
    /// Represents a typed algorithm log entry projected from pagmo runtime data.
    /// </summary>
    public readonly record struct CompassSearchLogLine(
        ulong FunctionEvaluations,
        double BestFitness,
        ulong ViolatedConstraints,
        double ViolationNorm,
        double Range) : IAlgorithmLogLine
    {
        /// <summary>
        /// Uses pagmo-native semantics. See docs/api-reference.md for upstream links.
        /// </summary>
        public string AlgorithmName => "compass_search";
        /// <summary>
        /// Gets a generic field map for algorithm-agnostic log processing.
        /// </summary>
        public IReadOnlyDictionary<string, object> RawFields => new Dictionary<string, object>
        {
            ["function_evaluations"] = FunctionEvaluations,
            ["best_fitness"] = BestFitness,
            ["violated_constraints"] = ViolatedConstraints,
            ["violation_norm"] = ViolationNorm,
            ["range"] = Range
        };
        /// <summary>
        /// Invokes the corresponding pagmo API. See docs/api-reference.md for upstream links.
        /// </summary>
        public string ToDisplayString() => $"fevals={FunctionEvaluations}, best={BestFitness}, range={Range}";
    }

    /// <summary>
    /// Invokes the corresponding pagmo API. See docs/api-reference.md for upstream links.
    /// </summary>
    public IReadOnlyList<CompassSearchLogLine> GetTypedLogLines()
    {
        var count = get_log_entry_count();
        var lines = new List<CompassSearchLogLine>(count);
        for (var i = 0; i < count; i++)
        {
            using var entry = get_log_entry(i);
            lines.Add(new CompassSearchLogLine(entry.fevals, entry.best, entry.violated, entry.violation_norm, entry.range));
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

