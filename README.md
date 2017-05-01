# JOCL SAMPLES

A mavenised version of the samples from http://jocl.org/samples/samples.html

## Notes

Reduction - needed to change to use CL_DEVICE_TYPE_GPU otherwise the GPU reduce fails on OSX. Maybe because it was trying to use the CPU rather than an installed GPU?

HistogramNVIDIA - doesn't work on OSX / Iris Pro. Why?

SimpleGL3 - GL_ARB_VERTEX_BUFFER_OBJECT not available when the GUI starts on OSX / Iris Pro.

SimpleLWJGL - the version of LWJGL is old; also can't find library to link to even though it is in the uberjar inside another jar.
