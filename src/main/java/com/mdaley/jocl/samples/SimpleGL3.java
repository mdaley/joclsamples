package com.mdaley.jocl.samples;

/*
 * JOCL - Java bindings for OpenCL
 *
 * Copyright 2009 Marco Hutter - http://www.jocl.org/
 */

import static com.jogamp.opengl.GL.*;
import static org.jocl.CL.*;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.*;
import java.util.Arrays;

import javax.swing.*;

import com.jogamp.nativewindow.NativeSurface;
import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLCanvas;
import jogamp.opengl.*;
import jogamp.opengl.egl.EGLContext;
import jogamp.opengl.macosx.cgl.MacOSXCGLContext;
import jogamp.opengl.windows.wgl.WindowsWGLContext;
import jogamp.opengl.x11.glx.X11GLXContext;

import org.jocl.*;

import com.jogamp.opengl.util.Animator;

/**
 * A small example demonstrating the JOCL/JOGL interoperability,
 * using the "simpleGL.cl" kernel from the NVIDIA "oclSimpleGL"
 * example. This example is intended to be used with JOGL 2, and
 * uses only the OpenGL 3.2 core profile and GLSL 1.5
 */
public class SimpleGL3 implements GLEventListener
{
    /**
     * Entry point for this sample.
     */
    public static void run()
    {
        GLProfile profile = GLProfile.get(GLProfile.GL3);
        final GLCapabilities capabilities = new GLCapabilities(profile);
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                new SimpleGL3(capabilities);
            }
        });
    }

    /**
     * Compile-time flag which indicates whether the real OpenCL/OpenGL
     * interoperation should be used. If this flag is 'true', then the
     * buffers should be shared between OpenCL and OpenGL. If it is
     * 'false', then the buffer contents will be copied via the host.
     */
    private static final boolean GL_INTEROP = true;

    /**
     * The source code for the vertex shader
     */
    private static String vertexShaderSource =
            "#version 150 core" + "\n" +
                    "in  vec4 inVertex;" + "\n" +
                    "in  vec3 inColor;" + "\n" +
                    "uniform mat4 modelviewMatrix;" + "\n" +
                    "uniform mat4 projectionMatrix;" + "\n" +
                    "void main(void)" + "\n" +
                    "{" + "\n" +
                    "    gl_Position = " + "\n" +
                    "        projectionMatrix * modelviewMatrix * inVertex;" + "\n" +
                    "}";

    /**
     * The source code for the fragment shader
     */
    private static String fragmentShaderSource =
            "#version 150 core" + "\n" +
                    "out vec4 outColor;" + "\n" +
                    "void main(void)" + "\n" +
                    "{" + "\n" +
                    "    outColor = vec4(1.0,0.0,0.0,1.0);" + "\n" +
                    "}";


    /**
     * Whether the initialization method of this GLEventListener has
     * already been called
     */
    private boolean initialized = false;

    /**
     * The width segments of the mesh to be displayed.
     */
    private static final int meshWidth = 8 * 64;

    /**
     * The height segments of the mesh to be displayed
     */
    private static final int meshHeight = 8 * 64;

    /**
     * The current animation state of the mesh
     */
    private float animationState = 0.0f;

    /**
     * The vertex array object (required as of GL3)
     */
    private int vertexArrayObject;

    /**
     * The VBO identifier
     */
    private int vertexBufferObject;

    /**
     * The cl_mem that has the contents of the VBO,
     * namely the vertex positions
     */
    private cl_mem vboMem;

    /**
     * The OpenCL context
     */
    private cl_context context;

    /**
     * The OpenCL command queue
     */
    private cl_command_queue commandQueue;

    /**
     * The OpenCL kernel
     */
    private cl_kernel kernel;

    /**
     * Whether the computation should be performed with JOCL or
     * with Java. May be toggled by pressing the 't' key
     */
    private boolean useJOCL = true;

    /**
     * A flag indicating whether the VBO and the VBO memory object
     * have to be re-initialized due to a switch between Java and
     * JOCL computing mode
     * To do: This should not be necessary. Find out why leaving
     * this out results in an OUT_OF_HOST_MEMORY error.
     */
    private boolean reInitVBOData = true;

    /**
     * The ID of the OpenGL shader program
     */
    private int shaderProgramID;

    /**
     * The translation in X-direction
     */
    private float translationX = 0;

    /**
     * The translation in Y-direction
     */
    private float translationY = 0;

    /**
     * The translation in Z-direction
     */
    private float translationZ = -4;

    /**
     * The rotation about the X-axis, in degrees
     */
    private float rotationX = 40;

    /**
     * The rotation about the Y-axis, in degrees
     */
    private float rotationY = 30;

    /**
     * The current projection matrix
     */
    float projectionMatrix[] = new float[16];

    /**
     * The current projection matrix
     */
    float modelviewMatrix[] = new float[16];

    /**
     * The animator
     */
    private Animator animator;

    /**
     * Step counter for FPS computation
     */
    private int step = 0;

    /**
     * Time stamp for FPS computation
     */
    private long prevTimeNS = -1;

    /**
     * The main frame of the application
     */
    private Frame frame;

    /**
     * Inner class encapsulating the MouseMotionListener and
     * MouseWheelListener for the interaction
     */
    class MouseControl implements MouseMotionListener, MouseWheelListener
    {
        private Point previousMousePosition = new Point();

        public void mouseDragged(MouseEvent e)
        {
            int dx = e.getX() - previousMousePosition.x;
            int dy = e.getY() - previousMousePosition.y;

            // If the left button is held down, move the object
            if ((e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) ==
                    MouseEvent.BUTTON1_DOWN_MASK)
            {
                translationX += dx / 100.0f;
                translationY -= dy / 100.0f;
            }

            // If the right button is held down, rotate the object
            else if ((e.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) ==
                    MouseEvent.BUTTON3_DOWN_MASK)
            {
                rotationX += dy;
                rotationY += dx;
            }
            previousMousePosition = e.getPoint();
            updateModelviewMatrix();
        }

        public void mouseMoved(MouseEvent e)
        {
            previousMousePosition = e.getPoint();
        }

        public void mouseWheelMoved(MouseWheelEvent e)
        {
            // Translate along the Z-axis
            translationZ += e.getWheelRotation() * 0.25f;
            previousMousePosition = e.getPoint();
            updateModelviewMatrix();
        }
    }

    /**
     * Inner class extending a KeyAdapter for the keyboard
     * interaction
     */
    class KeyboardControl extends KeyAdapter
    {
        @Override
        public void keyTyped(KeyEvent e)
        {
            char c = e.getKeyChar();
            if (c == 't')
            {
                useJOCL = !useJOCL;
                reInitVBOData = true;
                System.out.println("useJOCL is now "+useJOCL);
            }
        }
    }


    /**
     * Creates a new JOCLSimpleGL3 sample.
     *
     * @param capabilities The GL capabilities
     */
    public SimpleGL3(GLCapabilities capabilities)
    {
        // Initialize the GL component
        final GLCanvas glComponent = new GLCanvas(capabilities);
        glComponent.setFocusable(true);
        glComponent.addGLEventListener(this);

        // Initialize the mouse and keyboard controls
        MouseControl mouseControl = new MouseControl();
        glComponent.addMouseMotionListener(mouseControl);
        glComponent.addMouseWheelListener(mouseControl);
        KeyboardControl keyboardControl = new KeyboardControl();
        glComponent.addKeyListener(keyboardControl);
        updateModelviewMatrix();

        // Create and start an animator
        animator = new Animator(glComponent);
        animator.start();

        // Create the main frame
        frame = new JFrame("JOCL / JOGL interaction sample");
        frame.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                runExit();
            }
        });
        frame.setLayout(new BorderLayout());
        glComponent.setPreferredSize(new Dimension(800, 800));
        frame.add(glComponent, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);
        glComponent.requestFocus();

    }

    /**
     * Update the modelview matrix depending on the
     * current translation and rotation
     */
    private void updateModelviewMatrix()
    {
        float m0[] = translation(translationX, translationY, translationZ);
        float m1[] = rotationX(rotationX);
        float m2[] = rotationY(rotationY);
        modelviewMatrix = multiply(multiply(m1,m2), m0);
    }


    /**
     * Implementation of GLEventListener: Called to initialize the
     * GLAutoDrawable
     */
    public void init(GLAutoDrawable drawable)
    {
        // Perform the default GL initialization
        GL3 gl = drawable.getGL().getGL3();

        gl.setSwapInterval(0);

        gl.glEnable(GL_DEPTH_TEST);
        gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        // Initialize the shaders
        initShaders(gl);

        // Set up the viewport and projection matrix
        setupView(drawable);

        // Initialize the GL_ARB_vertex_buffer_object extension
        if (!gl.isExtensionAvailable("GL_ARB_vertex_buffer_object"))
        {
            new Thread(new Runnable()
            {
                public void run()
                {
                    JOptionPane.showMessageDialog(null,
                            "GL_ARB_vertex_buffer_object extension not available",
                            "Unavailable extension", JOptionPane.ERROR_MESSAGE);
                    runExit();
                }
            }).start();
        }

        // Initialize OpenCL, creating a context for the given GL object
        initCL(gl);

        // Initialize the OpenGL VBO and the OpenCL VBO memory object
        initVBOData(gl);

        initialized = true;
    }


    /**
     * Initialize OpenCL. This will create the CL context for the GL
     * context of the given GL, as well as the command queue and
     * kernel.
     *
     * @param gl The GL object
     */
    private void initCL(GL3 gl)
    {
        // The platform, device type and device number
        // that will be used
        final int platformIndex = 0;
        final long deviceType = CL_DEVICE_TYPE_GPU;
        final int deviceIndex = 0;

        // Enable exceptions and subsequently omit error checks in this sample
        CL.setExceptionsEnabled(true);

        // Obtain the number of platforms
        int numPlatformsArray[] = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];

        // Obtain a platform ID
        cl_platform_id platforms[] = new cl_platform_id[numPlatforms];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[platformIndex];

        // Initialize the context properties
        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);
        initContextProperties(contextProperties, gl);

        // Obtain the number of devices for the platform
        int numDevicesArray[] = new int[1];
        clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
        int numDevices = numDevicesArray[0];

        // Obtain a device ID
        cl_device_id devices[] = new cl_device_id[numDevices];
        clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
        cl_device_id device = devices[deviceIndex];

        // Create a context for the selected device
        context = clCreateContext(
                contextProperties, 1, new cl_device_id[]{device},
                null, null, null);

        // Create a command-queue for the selected device
        commandQueue =
                clCreateCommandQueue(context, device, 0, null);

        // Read the program source code and create the program
        String source = readFile("/simpleGL.cl");
        cl_program program = clCreateProgramWithSource(context, 1,
                new String[]{ source }, null, null);
        clBuildProgram(program, 0, null, "-cl-mad-enable", null, null);

        // Create the kernel which computes the sine wave pattern
        kernel = clCreateKernel(program, "sine_wave", null);

        // Set the constant kernel arguments
        clSetKernelArg(kernel, 1, Sizeof.cl_uint,
                Pointer.to(new int[]{ meshWidth }));
        clSetKernelArg(kernel, 2, Sizeof.cl_uint,
                Pointer.to(new int[]{ meshHeight }));
    }


    /**
     * Initializes the given context properties so that they may be
     * used to create an OpenCL context for the given GL object.
     *
     * @param contextProperties The context properties
     * @param gl The GL object
     */
    private void initContextProperties(cl_context_properties contextProperties, GL gl)
    {
        // Adapted from http://jogamp.org/jocl/www/

        GLContext glContext = gl.getContext();
        if(!glContext.isCurrent())
        {
            throw new IllegalArgumentException(
                    "OpenGL context is not current. This method should be called " +
                            "from the OpenGL rendering thread, when the context is current.");
        }

        long glContextHandle = glContext.getHandle();
        GLContextImpl glContextImpl = (GLContextImpl)glContext;
        GLDrawableImpl glDrawableImpl = glContextImpl.getDrawableImpl();
        NativeSurface nativeSurface = glDrawableImpl.getNativeSurface();

        if (glContext instanceof X11GLXContext)
        {
            long displayHandle = nativeSurface.getDisplayHandle();
            contextProperties.addProperty(CL_GL_CONTEXT_KHR, glContextHandle);
            contextProperties.addProperty(CL_GLX_DISPLAY_KHR, displayHandle);
        }
        else if (glContext instanceof WindowsWGLContext)
        {
            long surfaceHandle = nativeSurface.getSurfaceHandle();
            contextProperties.addProperty(CL_GL_CONTEXT_KHR, glContextHandle);
            contextProperties.addProperty(CL_WGL_HDC_KHR, surfaceHandle);
        }
        else if (glContext instanceof MacOSXCGLContext)
        {
            contextProperties.addProperty(CL_CGL_SHAREGROUP_KHR, glContextHandle);
        }
        else if (glContext instanceof EGLContext)
        {
            long displayHandle = nativeSurface.getDisplayHandle();
            contextProperties.addProperty(CL_GL_CONTEXT_KHR, glContextHandle);
            contextProperties.addProperty(CL_EGL_DISPLAY_KHR, displayHandle);
        }
        else
        {
            throw new RuntimeException("unsupported GLContext: " + glContext);
        }
    }



    /**
     * Helper function which reads the file with the given name and returns
     * the contents of this file as a String. Will exit the application
     * if the file can not be read.
     *
     * @param fileName The name of the file to read.
     * @return The contents of the file
     */
    private String readFile(String fileName)
    {
        try
        {
            BufferedReader br = new BufferedReader(new InputStreamReader(SimpleGL3.class.getResourceAsStream(fileName)));
            StringBuffer sb = new StringBuffer();
            String line = null;
            while (true)
            {
                line = br.readLine();
                if (line == null)
                {
                    break;
                }
                sb.append(line).append("\n");
            }
            br.close();
            return sb.toString();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            runExit();
            return null;
        }
    }


    /**
     * Initialize the shaders and the shader program
     *
     * @param gl The GL context
     */
    private void initShaders(GL3 gl)
    {
        int vertexShaderID = gl.glCreateShader(GL3.GL_VERTEX_SHADER);
        gl.glShaderSource(vertexShaderID, 1,
                new String[]{vertexShaderSource}, null);
        gl.glCompileShader(vertexShaderID);

        int fragmentShaderID = gl.glCreateShader(GL3.GL_FRAGMENT_SHADER);
        gl.glShaderSource(fragmentShaderID, 1,
                new String[]{fragmentShaderSource}, null);
        gl.glCompileShader(fragmentShaderID);

        shaderProgramID = gl.glCreateProgram();
        gl.glAttachShader(shaderProgramID, vertexShaderID);
        gl.glAttachShader(shaderProgramID, fragmentShaderID);
        gl.glLinkProgram(shaderProgramID);
    }

    /**
     * Initialize the OpenGL VBO and the OpenCL VBO memory object
     *
     * @param gl The current GL object
     */
    private void initVBOData(GL3 gl)
    {
        initVBO(gl);
        initVBOMem(gl);
        reInitVBOData = false;
    }


    /**
     * Create the GL vertex buffer object (VBO) that stores the
     * vertex positions.
     *
     * @param gl The GL context
     */
    private void initVBO(GL3 gl)
    {
        if (vertexBufferObject != 0)
        {
            gl.glDeleteBuffers(1, new int[]{ vertexBufferObject }, 0);
            vertexBufferObject = 0;
        }
        if (vertexArrayObject != 0)
        {
            gl.glDeleteVertexArrays(1, new int[] { vertexArrayObject }, 0);
            vertexArrayObject = 0;
        }
        int tempArray[] = new int[1];

        // Create the vertex array object
        gl.glGenVertexArrays(1, IntBuffer.wrap(tempArray));
        vertexArrayObject = tempArray[0];
        gl.glBindVertexArray(vertexArrayObject);

        // Create the vertex buffer object
        gl.glGenBuffers(1, IntBuffer.wrap(tempArray));
        vertexBufferObject = tempArray[0];

        // Initialize the vertex buffer object
        gl.glBindBuffer(GL_ARRAY_BUFFER, vertexBufferObject);
        int size = meshWidth * meshHeight * 4 * Sizeof.cl_float;
        gl.glBufferData(GL_ARRAY_BUFFER, size, null,
                GL_DYNAMIC_DRAW);

        // Initialize the attribute location of the input
        // vertices for the shader program
        int location = gl.glGetAttribLocation(shaderProgramID, "inVertex");
        gl.glVertexAttribPointer(location, 4, GL3.GL_FLOAT, false, 0, 0);
        gl.glEnableVertexAttribArray(location);

    }

    /**
     * Initialize the OpenCL VBO memory object which corresponds to
     * the OpenGL VBO that stores the vertex positions
     *
     * @param gl The current GL object
     */
    private void initVBOMem(GL3 gl)
    {
        if (vboMem != null)
        {
            clReleaseMemObject(vboMem);
            vboMem = null;
        }
        if (GL_INTEROP)
        {
            // Create an OpenCL buffer for the VBO
            gl.glBindBuffer(GL_ARRAY_BUFFER, vertexBufferObject);
            vboMem = clCreateFromGLBuffer(context, CL_MEM_WRITE_ONLY,
                    vertexBufferObject, null);
        }
        else
        {
            // Create an empty OpenCL buffer
            int size = meshWidth * meshHeight * 4 * Sizeof.cl_float;
            vboMem = clCreateBuffer(context, CL_MEM_WRITE_ONLY, size,
                    null, null);
        }
        clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(vboMem));
    }

    /**
     * Implementation of GLEventListener: Called when the given GLAutoDrawable
     * is to be displayed.
     */
    public void display(GLAutoDrawable drawable)
    {
        if (!initialized)
        {
            return;
        }
        GL3 gl = (GL3)drawable.getGL();

        if (reInitVBOData)
        {
            initVBOData(gl);
        }

        if (useJOCL)
        {
            // Run the JOCL kernel to generate new vertex positions.
            runJOCL(gl);
        }
        else
        {
            // Run the Java method to generate new vertex positions.
            runJava(gl);
        }

        animationState += 0.01f;

        gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Activate the shader program
        gl.glUseProgram(shaderProgramID);

        // Set the current projection matrix
        int projectionMatrixLocation =
                gl.glGetUniformLocation(shaderProgramID, "projectionMatrix");
        gl.glUniformMatrix4fv(
                projectionMatrixLocation, 1, false, projectionMatrix, 0);

        // Set the current modelview matrix
        int modelviewMatrixLocation =
                gl.glGetUniformLocation(shaderProgramID, "modelviewMatrix");
        gl.glUniformMatrix4fv(
                modelviewMatrixLocation, 1, false, modelviewMatrix, 0);

        // Render the VBO
        gl.glBindBuffer(GL_ARRAY_BUFFER, vertexBufferObject);
        gl.glDrawArrays(GL_POINTS, 0, meshWidth * meshHeight);


        // Update FPS information in main frame title
        step++;
        long currentTime = System.nanoTime();
        if (prevTimeNS == -1)
        {
            prevTimeNS = currentTime;
        }
        long diff = currentTime - prevTimeNS;
        if (diff > 1e9)
        {
            double fps = (diff / 1e9) * step;
            String t = "JOCL / JOGL interaction sample - ";
            t += useJOCL?"JOCL":"Java";
            t += " mode: "+String.format("%.2f", fps)+" FPS";
            frame.setTitle(t);
            prevTimeNS = currentTime;
            step = 0;
        }
    }

    /**
     * Run the JOCL computation to create new vertex positions
     * inside the vertexBufferObject.
     *
     * @param gl The current GL
     */
    private void runJOCL(GL3 gl)
    {
        if (GL_INTEROP)
        {
            // Map OpenGL buffer object for writing from OpenCL
            gl.glFinish();
            clEnqueueAcquireGLObjects(commandQueue, 1,
                    new cl_mem[]{ vboMem }, 0, null, null);
        }

        // Set work size and arguments, and execute the kernel
        long globalWorkSize[] = new long[2];
        globalWorkSize[0] = meshWidth;
        globalWorkSize[1] = meshHeight;
        clSetKernelArg(kernel, 3, Sizeof.cl_float,
                Pointer.to(new float[]{ animationState }));
        clEnqueueNDRangeKernel(commandQueue, kernel, 2, null,
                globalWorkSize, null, 0, null, null);

        if (GL_INTEROP)
        {
            // Unmap the buffer object
            clEnqueueReleaseGLObjects(commandQueue, 1,
                    new cl_mem[]{ vboMem }, 0, null, null);
            clFinish(commandQueue);
        }
        else
        {
            // Map the VBO to copy data from the CL buffer to the GL buffer,
            // copy the data from CL to GL, and unmap the buffer
            gl.glBindBuffer(GL_ARRAY_BUFFER, vertexBufferObject);
            ByteBuffer pointer = gl.glMapBuffer(
                    GL_ARRAY_BUFFER, GL_WRITE_ONLY);
            clEnqueueReadBuffer(commandQueue, vboMem, CL_TRUE, 0,
                    Sizeof.cl_float * 4 * meshHeight * meshWidth,
                    Pointer.to(pointer), 0, null, null);
            gl.glUnmapBuffer(GL_ARRAY_BUFFER);
        }
    }

    /**
     * Run the Java computation to create new vertex positions
     * inside the vertexBufferObject.
     *
     * @param gl The current GL.
     */
    private void runJava(GL3 gl)
    {
        float currentAnimationState = animationState;

        gl.glBindBuffer(GL_ARRAY_BUFFER, vertexBufferObject);

        ByteBuffer byteBuffer = gl.glMapBuffer(
                GL_ARRAY_BUFFER, GL_WRITE_ONLY);
        FloatBuffer vertices = byteBuffer.order(
                ByteOrder.nativeOrder()).asFloatBuffer();

        for (int x = 0; x < meshWidth; x++)
        {
            for (int y = 0; y < meshHeight; y++)
            {
                // Calculate u/v coordinates
                float u = x / (float) meshWidth;
                float v = y / (float) meshHeight;

                u = u * 2.0f - 1.0f;
                v = v * 2.0f - 1.0f;

                // Calculate simple sine wave pattern
                float freq = 4.0f;
                float w =
                        (float) Math.sin(u * freq + currentAnimationState) *
                                (float) Math.cos(v * freq + currentAnimationState) * 0.5f;

                // Write output vertex
                int index = 4 * (y * meshWidth + x);
                vertices.put(index + 0, u);
                vertices.put(index + 1, w);
                vertices.put(index + 2, v);
                vertices.put(index + 3, 1);
            }
        }
        gl.glUnmapBuffer(GL_ARRAY_BUFFER);
    }

    /**
     * Implementation of GLEventListener: Called then the
     * GLAutoDrawable was reshaped
     */
    public void reshape(GLAutoDrawable drawable, int x, int y,
                        int width, int height)
    {
        setupView(drawable);
    }

    /**
     * Set up a default view for the given GLAutoDrawable
     *
     * @param drawable The GLAutoDrawable to set the view for
     */
    private void setupView(GLAutoDrawable drawable)
    {
        GL3 gl = (GL3)drawable.getGL();

        gl.glViewport(0, 0,
                drawable.getSurfaceWidth(),
                drawable.getSurfaceHeight());

        float aspect = (float) drawable.getSurfaceWidth() /
                drawable.getSurfaceHeight();
        projectionMatrix = perspective(50, aspect, 0.1f, 100.0f);
    }

    /**
     * Implementation of GLEventListener - not used
     */
    public void dispose(GLAutoDrawable drawable)
    {
    }

    /**
     * Calls System.exit() in a new Thread. It may not be called
     * synchronously inside one of the JOGL callbacks.
     */
    private void runExit()
    {
        new Thread(new Runnable()
        {
            public void run()
            {
                animator.stop();
                System.exit(0);
            }
        }).start();
    }



    //=== Helper functions for matrix operations ==============================

    /**
     * Helper method that creates a perspective matrix
     * @param fovy The fov in y-direction, in degrees
     *
     * @param aspect The aspect ratio
     * @param zNear The near clipping plane
     * @param zFar The far clipping plane
     * @return A perspective matrix
     */
    private static float[] perspective(
            float fovy, float aspect, float zNear, float zFar)
    {
        float radians = (float)Math.toRadians(fovy / 2);
        float deltaZ = zFar - zNear;
        float sine = (float)Math.sin(radians);
        if ((deltaZ == 0) || (sine == 0) || (aspect == 0))
        {
            return identity();
        }
        float cotangent = (float)Math.cos(radians) / sine;
        float m[] = identity();
        m[0*4+0] = cotangent / aspect;
        m[1*4+1] = cotangent;
        m[2*4+2] = -(zFar + zNear) / deltaZ;
        m[2*4+3] = -1;
        m[3*4+2] = -2 * zNear * zFar / deltaZ;
        m[3*4+3] = 0;
        return m;
    }

    /**
     * Creates an identity matrix
     *
     * @return An identity matrix
     */
    private static float[] identity()
    {
        float m[] = new float[16];
        Arrays.fill(m, 0);
        m[0] = m[5] = m[10] = m[15] = 1.0f;
        return m;
    }

    /**
     * Multiplies the given matrices and returns the result
     *
     * @param m0 The first matrix
     * @param m1 The second matrix
     * @return The product m0*m1
     */
    private static float[] multiply(float m0[], float m1[])
    {
        float m[] = new float[16];
        for (int x=0; x < 4; x++)
        {
            for(int y=0; y < 4; y++)
            {
                m[x*4 + y] =
                        m0[x*4+0] * m1[y+ 0] +
                                m0[x*4+1] * m1[y+ 4] +
                                m0[x*4+2] * m1[y+ 8] +
                                m0[x*4+3] * m1[y+12];
            }
        }
        return m;
    }

    /**
     * Creates a translation matrix
     *
     * @param x The x translation
     * @param y The y translation
     * @param z The z translation
     * @return A translation matrix
     */
    private static float[] translation(float x, float y, float z)
    {
        float m[] = identity();
        m[12] = x;
        m[13] = y;
        m[14] = z;
        return m;
    }

    /**
     * Creates a matrix describing a rotation around the x-axis
     *
     * @param angleDeg The rotation angle, in degrees
     * @return The rotation matrix
     */
    private static float[] rotationX(float angleDeg)
    {
        float m[] = identity();
        float angleRad = (float)Math.toRadians(angleDeg);
        float ca = (float)Math.cos(angleRad);
        float sa = (float)Math.sin(angleRad);
        m[ 5] =  ca;
        m[ 6] =  sa;
        m[ 9] = -sa;
        m[10] =  ca;
        return m;
    }

    /**
     * Creates a matrix describing a rotation around the y-axis
     *
     * @param angleDeg The rotation angle, in degrees
     * @return The rotation matrix
     */
    private static float[] rotationY(float angleDeg)
    {
        float m[] = identity();
        float angleRad = (float)Math.toRadians(angleDeg);
        float ca = (float)Math.cos(angleRad);
        float sa = (float)Math.sin(angleRad);
        m[ 0] =  ca;
        m[ 2] = -sa;
        m[ 8] =  sa;
        m[10] =  ca;
        return m;
    }

}
