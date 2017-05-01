package com.mdaley.jocl;

import com.mdaley.jocl.samples.*;
import sun.java2d.pipe.SpanShapeRenderer;

public class Core {

    public static void main(String [] args) {
        System.out.println("Running JOCL Sample...");

        if (args != null && args.length > 0 && args[0] != null && args[0].length() > 0) {
            int id = Integer.parseInt(args[0]);

            switch(id) {
                case 1:
                    Sample.run();
                    break;
                case 2:
                    Sample_1_1.run();
                    break;
                case 3:
                    Sample_1_2_KernelArgs.run();
                    break;
                case 4:
                    Sample_2_0_SVM.run();
                    break;
                case 5:
                    DeviceQuery.run();
                    break;
                case 6:
                    Reduction.run();
                    break;
                case 7:
                    HistogramAMD.run();
                    break;
                case 8:
                    HistogramNVIDIA.run();
                    break;
                case 9:
                    EventSample.run();
                    break;
                case 10:
                    MultiDeviceSample.run();
                    break;
                case 11:
                    SimpleGL3.run();
                    break;
                case 12:
                    SimpleLWJGL.run();
                    break;
                case 13:
                    SimpleMandelbrot.run();
                    break;
                case 14:
                    Mandelbrot.run();
                    break;
                case 15:
                    SimpleConvolution.run();
                    break;
                case 16:
                    SimpleImage.run();
                    break;
                default:
                    System.out.println("Nothing to run!");
            }
        }
    }
}
