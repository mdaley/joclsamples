# JOCL SAMPLES

A mavenised version of the samples from http://jocl.org/samples/samples.html

Simply `maven clean package` and then run:

```
java -jar target/jocl-samples-1.0-SNAPSHOT.jar {n}
```

where {n} is the number of the sample to run (look in the Core.java file).

## Notes

Reduction - needed to change to use CL_DEVICE_TYPE_GPU otherwise the GPU reduce fails on OSX. Maybe
because it was trying to use the CPU rather than an installed GPU? Don't get any GPU speedup on either
my GTX 770 or with Iris Pro on Macbook.

Sample SVM 2.0 doesn't work with GTX 770 as that only supports openCL 1.2.

HistogramNVIDIA - doesn't work on OSX / Iris Pro. Why? Works with 770 GTX.

SimpleGL3 - GL_ARB_VERTEX_BUFFER_OBJECT not available when the GUI starts on OSX / Iris Pro. Exception
on Linux.

SimpleLWJGL - the version of LWJGL is old; also can't find library to link to even though it is in the
uberjar inside another jar. Probably have to specify the lwjgl64 library separately but can't be bothered!

