using NUnit.Framework;
using pagmo;

namespace Tests.Pagmo.NET;

/// <summary>
/// Verifies solver availability in CI builds.
/// Set PAGMONET_BASE_VERIFY=1 in CI to activate these assertions.
/// Note: Pagmo.NET always includes IPOPT in its native library (it is a default
/// feature of the pagmoNet portfile). IsIpoptAvailable is expected to be true.
/// </summary>
[TestFixture]
public class Test_base_build_verification
{
    private static void SkipIfNotBaseVerify()
    {
        if (System.Environment.GetEnvironmentVariable("PAGMONET_BASE_VERIFY") != "1")
            Assert.Pass("Set PAGMONET_BASE_VERIFY=1 to run base build verification.");
    }

    [Test]
    public void BaseBuildIncludesNlopt()
    {
        SkipIfNotBaseVerify();
        Assert.That(OptionalSolverAvailability.IsNloptAvailable, Is.True,
            "Base builds must include NLopt.");
    }
}
