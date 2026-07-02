package io.github.samthegliderpilot.pagmonet4j;

/**
 * Test utility for SWIG-generated types that cannot implement AutoCloseable.
 *
 * <p>SWIG's std_vector.i sets javainterfaces via an unexpandable macro, preventing
 * AutoCloseable from being added to vector types. Use {@link #close} as a drop-in
 * for try-with-resources on {@link DoubleVector}, {@link ULongLongVector}, etc.
 *
 * <p>{@link NativeHandle} overrides {@code close()} without {@code throws}, so
 * test methods don't need to declare {@code throws Exception}.
 */
final class SWIGTestUtils {

    private SWIGTestUtils() {}

    @FunctionalInterface
    interface NativeHandle extends AutoCloseable {
        @Override void close(); // no checked exception
    }

    public static NativeHandle close(DoubleVector v) { return v::delete; }
    public static NativeHandle close(ULongLongVector v) { return v::delete; }
    public static NativeHandle close(SizeTVector v) { return v::delete; }
    public static NativeHandle close(VectorOfVectorOfDoubles v) { return v::delete; }
}
