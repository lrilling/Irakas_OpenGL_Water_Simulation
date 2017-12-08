package water;


import com.jogamp.newt.event.*;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.*;
import com.jogamp.opengl.math.FloatUtil;
import com.jogamp.opengl.math.VectorUtil;
import com.jogamp.opengl.util.Animator;
import com.jogamp.opengl.util.GLBuffers;
import com.jogamp.opengl.util.glsl.ShaderCode;
import com.jogamp.opengl.util.glsl.ShaderProgram;
import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.TextureIO;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import objParser.ObjModelParser;
import objParser.ParserModel;
import mousePicker.MousePicker;

import static com.jogamp.opengl.GL.*;
import static com.jogamp.opengl.GL2ES2.GL_DEBUG_SEVERITY_HIGH;
import static com.jogamp.opengl.GL2ES2.GL_DEBUG_SEVERITY_MEDIUM;
import static com.jogamp.opengl.GL2ES2.GL_DEPTH_COMPONENT;
import static com.jogamp.opengl.GL2ES3.*;
import static com.jogamp.opengl.GL2ES3.GL_UNIFORM_BUFFER;
import static com.jogamp.opengl.GL4.GL_MAP_COHERENT_BIT;
import static com.jogamp.opengl.GL4.GL_MAP_PERSISTENT_BIT;

public class WaterSimulation implements GLEventListener, KeyListener, MouseListener {

    // OpenGL window reference
    private static GLWindow window;

    // The animator is responsible for continuous operation
    private static Animator animator;

    // The program entry point
    public static void main(String[] args) {
        new WaterSimulation().setup();
    }

    private interface Buffer{
    	int SCENE_V = 0;
    	int SCENE_E = 1;

    	int WATER_V = 2;
    	int WATER_E = 3;

    	int WATER_DOWN_V = 4;
    	int WATER_DOWN_E = 5;

    	int GLOBAL_MATRICES = 6;

    	int MODEL_MATRIX_SCENE = 7;
    	int MODEL_MATRIX_WATER = 8;
    	int MODEL_MATRIX_WATER_DOWN = 9;

    	int LIGHT_PROPERTIES = 10;
    	int SCENE_MATERIAL_PROPERTIES = 11;
    	int WATER_MATERIAL_PROPERTIES = 12;
    	int CAMERA_PROPERTIES = 13;

		int DROP_DATA = 14;
		int DROP_COUNT = 15;

		int TIME = 16;
		int NOISE_TIME = 17;

    	int CLIP_PLANE = 18;

    	int MAX = 19;
    }

    private interface VertexArray{
    	int SCENE = 0;
    	int WATER = 1;
    	int WATER_DOWN = 2;
    	int MAX = 3;
    }

    private interface FrameBuffers{
    	int REFLECTION_FB = 0;
    	int REFRACTION_FB = 1;
    	int REFLECTION_FB_DOWN = 2;
    	int REFRACTION_FB_DOWN = 3;
    	int MAX = 4;
    }

    private interface Textures{
    	int REFLECTION_COLOR_T = 0;
    	int REFRACTION_COLOR_T = 2;
    	int REFRACTION_DEPTH_T = 3;

    	int REFLECTION_COLOR_T_DOWN = 4;
    	int REFRACTION_COLOR_T_DOWN = 5;
    	int REFRACTION_DEPTH_T_DOWN = 6;

    	int MAX = 7;
    }

    private interface DepthBuffer{
    	int REFLECTION_DEPTH_B = 0;
    	int REFLECTION_DEPTH_B_DOWN = 1;
    	int MAX = 2;
    }

    private float[] sceneVertexData;
    private short[] sceneElementData;

    private float[] waterVertexData = {
    	 7f, 0f,  7f, 		0f, 0f ,1f, 	0f, 1f, 0f,
    	-7f, 0f,  7f, 		0f, 0f ,1f, 	0f, 1f, 0f,
    	-7f, 0f, -7f, 		0f, 0f ,1f, 	0f, 1f, 0f,
    	 7f, 0f, -7f, 		0f, 0f ,1f, 	0f, 1f, 0f,
    };

    private short[] waterElementData = {
    		0, 1, 2,
    		2, 3, 0
    };

    private float[] waterDownVertexData = {
          7f, -0.0005f,  7f,		0f, 0f ,1f,		0f, -1f, 0f,
         -7f, -0.0005f,  7f,		0f, 0f ,1f,		0f, -1f, 0f,
         -7f, -0.0005f, -7f,		0f, 0f ,1f,		0f, -1f, 0f,
          7f, -0.0005f, -7f,		0f, 0f ,1f, 	0f, -1f, 0f
    };

    // Window triangles
    private short[] waterDownElementData = {
    		0, 1, 2,
    		2, 3, 0
    };

	private int maxNumDrops = 40;
	private int numDrops = 0;
	private float[][] dropPositions = new float[maxNumDrops][2];
	private long[] dropStartTime = new long[maxNumDrops];

	private float[] dropData = new float[3 * maxNumDrops];

    // Create buffers for the names
 	private IntBuffer bufferNames = GLBuffers.newDirectIntBuffer(Buffer.MAX);
 	private IntBuffer vertexArrayName = GLBuffers.newDirectIntBuffer(VertexArray.MAX);
 	private IntBuffer textureNames = GLBuffers.newDirectIntBuffer(Textures.MAX);
 	private IntBuffer frameBufferNames = GLBuffers.newDirectIntBuffer(FrameBuffers.MAX);
 	private IntBuffer depthBufferName = GLBuffers.newDirectIntBuffer(DepthBuffer.MAX);

 	private IntBuffer testTextureNames = GLBuffers.newDirectIntBuffer(1);

	private FloatBuffer clearColor = GLBuffers.newDirectFloatBuffer(new float[] { 0.008f, 0.616f, 0.825f, 0 });
	private FloatBuffer clearDepth = GLBuffers.newDirectFloatBuffer(new float[] { 1 });

	private ByteBuffer globalMatricesPointer, sceneModelMatrixPointer, waterModelMatrixPointer, waterDownModelMatrixPointer, timePointer, noiseTimePointer, clipPlanePointer;

	// Create a direct buffer for dropPositions and dropStartTime:
	private ByteBuffer dropBuffer, dropCountBuffer;

	private ByteBuffer cameraBuffer;

	 // Light properties (4 valued vectors due to std140 see OpenGL 4.5 reference)
    private float[] lightProperties = {
        // Position
        0f, 2f, 0f, 0f,
        // Ambient Color
        1f, 1f, 1f, 0f,
        // Diffuse Color
        1f, 1f, 1f, 0f,
        // Specular Color
        1f, 1f, 1f, 0f
    };

    private float[] sceneMaterialProperties = {
        // Shininess
        1000.0f
    };

    private float[] waterMaterialProperties = {
        // Shininess
        100.0f
    };

    // Camera properties
    private float[] cameraProperties = {
        1f, 3f, 12f
    };

    private float waterHeight = 0f;

    // The OpenGL profile
    private GLProfile glProfile;

    // Program instance reference
    private Program program, waterProgram, waterDownProgram, windowProgram;

    //Bug 1287:
    private boolean bug1287 = true;

    //Timer:
    private long start;
	private long noise_start;

    //Variables for controls:
	private boolean noAutoRepeat = false;	//set true if auto repeat keys are not activated on your system
    private boolean shift = false;
    private float angleX = 0.0f;
    private float angleY = 0.0f;
    private Set<Short> pressed = new HashSet<Short>();

    private int width = 1920;
    private int height = 1080;

    private int reflectionWidth = 1080;
    private int reflectionHeight = 720;

    private int refractionWidth = 1080;
    private int refractionHeight = 720;

    //number of subdivisions for the water mesh:
    private int detail = 6;

    //booleans to switch between rendering the scene as a wireframe or rendering with filled faces: 
    private boolean wireframe = false;
    private boolean wireframeWater = false;

    //mouse picker for calculation of mouse ray:
    private MousePicker mousePicker;
    
    //variables to store mouse position while the mouse is dragged:
    private int mouseXOld = -20;
    private int mouseYOld = -20;

    // Application setup function
    private void setup() {
        // Get a OpenGL 4.x profile (x >= 0)
        glProfile = GLProfile.get(GLProfile.GL4);

        // Get a structure for definining the OpenGL capabilities with default values
        GLCapabilities glCapabilities = new GLCapabilities(glProfile);

        // Create the window with default capabilities
        window = GLWindow.create(glCapabilities);

        // Set the title of the window
        window.setTitle("waterSimulation JOGL");

        // Set the size of the window
        window.setSize(width, height);

        // Set debug context (must be set before the window is set to visible)
        window.setContextCreationFlags(GLContext.CTX_OPTION_DEBUG);

        // Make the window visible
        window.setVisible(true);

        // Add OpenGL and keyboard event listeners
        window.addGLEventListener(this);
        window.addKeyListener(this);
		window.addMouseListener(this);

        // Create and start the animator
        animator = new Animator(window);
        animator.start();

        // Add window event listener
        window.addWindowListener(new WindowAdapter() {
            // Window has been destroyed
            @Override
            public void windowDestroyed(WindowEvent e) {
                // Stop animator and exit
                animator.stop();
                System.exit(1);
            }
        });
    }


    // GLEventListener.init implementation
    @Override
    public void init(GLAutoDrawable drawable) {

        // Get OpenGL 4 reference
        GL4 gl = drawable.getGL().getGL4();

        //Initialize debugging
        initDebug(gl);

        //Parse the obj-Data to get Vertices and element data:
        parseData();

        //Initialize buffers
        initBuffers(gl);

        //Initialize vertex array
        initVertexArray(gl);

        //Initialize frame buffers
        initFrameBuffers(gl);

        // Set up the programs
        program = new Program(gl, "water", "waterSimulation", "waterSimulation");
        waterProgram = new Program(gl, "water", "water", "texture");
        waterDownProgram = new Program(gl, "water", "waterDown", "textureDown");

		//Set up the mousePicker
		mousePicker = new MousePicker(null, null, window.getWidth(), window.getHeight());

        // Enable Opengl depth buffer testing
        gl.glEnable(GL_DEPTH_TEST);


        //Store the start time of the application for the time diff:
        start = System.currentTimeMillis();
				noise_start = start;

				for (int i = 0; i < dropStartTime.length; i++) {
					dropStartTime[i] = 0;
				}
		System.out.println("init done!");
    }

    // GLEventListener.display implementation
    @Override
    public void display(GLAutoDrawable drawable) {

        // Get OpenGL 4 reference
        GL4 gl = drawable.getGL().getGL4();
        
        //if auto repeat keys are deactivated on system interpret the keys here:
        if(noAutoRepeat)
        	applyKeys();

        // Enable the index 0 of the Clip Distance array
        gl.glEnable(gl.GL_CLIP_DISTANCE0);

        // =================== RENDER TO THE REFLECTION FRAMEBUFFER =======================

        gl.glBindTexture(GL_TEXTURE_2D, 0); // Ensure that there is no texture bound

        // Bind the reflection framebuffer
        gl.glBindFramebuffer(GL_FRAMEBUFFER, frameBufferNames.get(FrameBuffers.REFLECTION_FB));

        // Change the viewport for matching the framebuffer
        gl.glViewport(0, 0, reflectionWidth, reflectionHeight);

        // Create and bind the clippingPlane for the reflection texture
        float[] clippingPlane = new float[]{0f, 1f, 0f, -waterHeight};
        clipPlanePointer.asFloatBuffer().put(clippingPlane);

        gl.glBindBufferBase(
        		GL_UNIFORM_BUFFER,
        		Semantic.Uniform.CLIP_PLANE,
        		bufferNames.get(Buffer.CLIP_PLANE));

        gl.glFinish();

        // Render the scene moving the camera to the reflection position
        renderScene(gl, true);

        gl.glFinish();

        // =================== RENDER TO THE REFLECTION FRAMEBUFFER =======================

	    gl.glBindTexture(GL_TEXTURE_2D, 0); // Ensure that there is no texture bound

	    // Bind the refraction framebuffer
	    gl.glBindFramebuffer(GL_FRAMEBUFFER, frameBufferNames.get(FrameBuffers.REFRACTION_FB));

	    // Change the viewport for matching the framebuffer
	    gl.glViewport(0, 0, refractionWidth, refractionHeight);

	    // Change and bind the clipping plane for the refraction texture
	    clippingPlane = new float[]{0f, -1f, 0f, waterHeight};
	    clipPlanePointer.asFloatBuffer().put(clippingPlane);

	    gl.glBindBufferBase(
	     	GL_UNIFORM_BUFFER,
	     	Semantic.Uniform.CLIP_PLANE,
	     	bufferNames.get(Buffer.CLIP_PLANE));

        gl.glFinish();

        // Render the scene without moving the camera
	    renderScene(gl, false);

	    gl.glFinish();

	    // =========== RENDER TO THE REFLECTION FRAMEBUFFER  OF THE DOWN SIDE ============

        gl.glBindTexture(GL_TEXTURE_2D, 0);

        // Bind the reflection framebuffer for the down side
        gl.glBindFramebuffer(GL_FRAMEBUFFER, frameBufferNames.get(FrameBuffers.REFLECTION_FB_DOWN));

        // Change the viewport
        gl.glViewport(0, 0, reflectionWidth, reflectionHeight);

        // Change and bind the clipping plane
        clippingPlane = new float[]{0f, -1f, 0f, waterHeight};
        clipPlanePointer.asFloatBuffer().put(clippingPlane);

        gl.glBindBufferBase(
        		GL_UNIFORM_BUFFER,
        		Semantic.Uniform.CLIP_PLANE,
        		bufferNames.get(Buffer.CLIP_PLANE));

        gl.glFinish();

        // Render with the camera in the reflection position
        renderScene(gl, true);

        gl.glFinish();

        // =========== RENDER TO THE REFRACTION FRAMEBUFFER  OF THE DOWN SIDE ============

	    gl.glBindTexture(GL_TEXTURE_2D, 0);

	    // Bind the refraction framebuffer of the down side
	    gl.glBindFramebuffer(GL_FRAMEBUFFER, frameBufferNames.get(FrameBuffers.REFRACTION_FB_DOWN));

	    // Change the viewport
	    gl.glViewport(0, 0, refractionWidth, refractionHeight);

	    // Change and bind the clipping plane
	    clippingPlane = new float[]{0f, 1f, 0f, -waterHeight};
	    clipPlanePointer.asFloatBuffer().put(clippingPlane);

	    gl.glBindBufferBase(
	     	GL_UNIFORM_BUFFER,
	     	Semantic.Uniform.CLIP_PLANE,
	     	bufferNames.get(Buffer.CLIP_PLANE));

        gl.glFinish();

        // Render the scene without moving the camera
	    renderScene(gl, false);

	    gl.glFinish();

	    // ==================== RENDER TO THE DEFAULT FRAMEBUFFER  =======================

	    // Unbind the framebuffer to set the default framebuffer
	    gl.glBindFramebuffer(GL_FRAMEBUFFER, 0);

	    // Change the viewport to its original size
	    gl.glViewport(0, 0, width, height);

	    // Disable the Clip Distance so that our scene is not clipped anymore
        gl.glDisable(gl.GL_CLIP_DISTANCE0);

	    // Render the scene without camera moving
	    renderScene(gl, false);

	    // ------------- RENDER THE WATER SURFACE ------------

	    // Change to the water program
	    gl.glUseProgram(waterProgram.name);

	    // Find the locations of the texture sampler variables in the shader
	    int reflLoc = gl.glGetUniformLocation(waterProgram.name, "reflectionTextSampler");
	    int refrLoc = gl.glGetUniformLocation(waterProgram.name, "refractionTextSampler");
	    int depthLoc = gl.glGetUniformLocation(waterProgram.name, "depthMap");

	    // Assign each location to a uniform texture index
	    gl.glUniform1i(reflLoc, 0);
	    gl.glUniform1i(refrLoc, 1);
	    gl.glUniform1i(depthLoc, 2);

	    // Bind the Reflection Color Texture
	    gl.glActiveTexture(GL_TEXTURE0 + 0);
	    gl.glBindTexture(GL_TEXTURE_2D, textureNames.get(Textures.REFLECTION_COLOR_T));

	    // Bind the Refraction Color Texture
	    gl.glActiveTexture(GL_TEXTURE0 + 1);
	    gl.glBindTexture(GL_TEXTURE_2D, textureNames.get(Textures.REFRACTION_COLOR_T));

	    // Bind the Refraction Depth Texture
	    gl.glActiveTexture(GL_TEXTURE0 + 2);
	    gl.glBindTexture(GL_TEXTURE_2D, textureNames.get(Textures.REFRACTION_DEPTH_T));

	    // Enable OpenGL blending function and assign an operation to it
	    gl.glEnable(gl.GL_BLEND);
	    gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);

	    // Bind buffers for the water waves
	    gl.glBindBufferBase(GL_UNIFORM_BUFFER, Semantic.Uniform.NOISE_TIME, bufferNames.get(Buffer.NOISE_TIME));
	    gl.glBindBufferBase(GL_UNIFORM_BUFFER, Semantic.Uniform.DROP_COUNT, bufferNames.get(Buffer.DROP_COUNT));
	    gl.glBindBufferBase(GL_UNIFORM_BUFFER, Semantic.Uniform.DROP_DATA, bufferNames.get(Buffer.DROP_DATA));

	    // Bind and draw the water quad
	    gl.glBindBufferBase(GL_UNIFORM_BUFFER, Semantic.Uniform.TRANSFORM1, bufferNames.get(Buffer.MODEL_MATRIX_WATER));
	    gl.glBindBufferBase(GL_UNIFORM_BUFFER, Semantic.Uniform.MATERIAL, bufferNames.get(Buffer.WATER_MATERIAL_PROPERTIES));
	    gl.glBindVertexArray(vertexArrayName.get(VertexArray.WATER));

	    if(wireframeWater)
	    	gl.glPolygonMode(GL_FRONT_AND_BACK, gl.GL_LINE);

	    gl.glDrawElements(GL_TRIANGLES, waterElementData.length, GL_UNSIGNED_SHORT, 0);

	    // ----------- RENDER THE DOWN SIDE OF THE WATER ----------

	    // Change to the water down program
	    gl.glUseProgram(waterDownProgram.name);

	    // Find the locations of the texture sampler variables in the shader
	    int reflLoc2 = gl.glGetUniformLocation(waterDownProgram.name, "reflectionTextSampler");
	    int refrLoc2 = gl.glGetUniformLocation(waterDownProgram.name, "refractionTextSampler");
	    int depthLoc2 = gl.glGetUniformLocation(waterDownProgram.name, "depthMap");

	    // Assign each location to a uniform texture index
	    gl.glUniform1i(reflLoc2, 0);
	    gl.glUniform1i(refrLoc2, 1);
	    gl.glUniform1i(depthLoc2, 2);

	    // Bind textures
	    gl.glActiveTexture(GL_TEXTURE0 + 0);
	    gl.glBindTexture(GL_TEXTURE_2D, textureNames.get(Textures.REFLECTION_COLOR_T_DOWN));

	    gl.glActiveTexture(GL_TEXTURE0 + 1);
	    gl.glBindTexture(GL_TEXTURE_2D, textureNames.get(Textures.REFRACTION_COLOR_T_DOWN));

	    gl.glActiveTexture(GL_TEXTURE0 + 2);
	    gl.glBindTexture(GL_TEXTURE_2D, textureNames.get(Textures.REFRACTION_DEPTH_T));

	    // Bind and draw the quad of the down side of the water surface
	    gl.glBindBufferBase(GL_UNIFORM_BUFFER, Semantic.Uniform.TRANSFORM1, bufferNames.get(Buffer.MODEL_MATRIX_WATER_DOWN));
	    gl.glBindVertexArray(vertexArrayName.get(VertexArray.WATER_DOWN));
	    gl.glDrawElements(GL_TRIANGLES, waterDownElementData.length, GL_UNSIGNED_SHORT, 0);

	    // ============ FINISH DISPLAY METHOD ============

	    // Unbind everything
	    gl.glUseProgram(0);
	    gl.glBindVertexArray(0);
	    gl.glDisable(gl.GL_BLEND);

	    gl.glPolygonMode(GL_FRONT_AND_BACK, gl.GL_FILL);

    }

    private void renderScene(GL4 gl, boolean reflCamera){

        gl.glClearBufferfv(GL_COLOR, 0, clearColor);
        gl.glClearBufferfv(GL_DEPTH, 0, clearDepth);

    	//Copy the view matrix to the server
        {
        	float[] rotationYView = FloatUtil.makeRotationAxis(new float[16], 0, angleY, 0f, 1f, 0f, new float[3]);

			float[] view;

			if (reflCamera){
				float[] translateReflView = FloatUtil.makeTranslation(new float[16], false, -cameraProperties[0], cameraProperties[1], -cameraProperties[2]);
				float[] rotationXReflView = FloatUtil.makeRotationAxis(new float[16], 0, -angleX, 1f, 0f, 0f, new float[3]);
				view = FloatUtil.multMatrix(FloatUtil.multMatrix(rotationXReflView, rotationYView), translateReflView);
			}
			else{
				float [] translateView = FloatUtil.makeTranslation(new float[16], false, -cameraProperties[0], -cameraProperties[1], -cameraProperties[2]);
				float[] rotationXView = FloatUtil.makeRotationAxis(new float[16], 0, angleX, 1f, 0f, 0f, new float[3]);
				view = FloatUtil.multMatrix(FloatUtil.multMatrix(rotationXView, rotationYView), translateView);
			}

        	for(int i=0; i<16; i++)
        		globalMatricesPointer.putFloat(16*4 + i * 4, view[i]);

			mousePicker.updateView(view);

			cameraBuffer.asFloatBuffer().put(cameraProperties);

        }

        //Copy the model matrices to the server
        {
        	//store current time to calculate time difference for the wave functions and the noise
			long now = System.currentTimeMillis();

			float noiseDiff = (float) (now - noise_start) / 100;
			noiseTimePointer.asFloatBuffer().put(noiseDiff);

			//ArrayList to store all waves that are currently active
			ArrayList<Float> dropList = new ArrayList<Float>();
			//iterate through the drop data arrays and transfering the data to the ArrayList
			for (int i = 0; i < dropStartTime.length; i++) {
				//if the time is equal to zero the elements in the array are currently not used or needed
				if (dropStartTime[i] != 0f) {
					dropList.add((float) (now - dropStartTime[i]) / 1000);
					dropList.add(dropPositions[i][0]);
					dropList.add(dropPositions[i][1]);
				}
			}
			
			//transfer ArrayList to fixed sized array
			for(int i = 0; i < dropList.size(); i++) {
				dropData[i] = dropList.get(i);
			}

			//upload array and drop number to server:
			dropBuffer.asFloatBuffer().put(dropData);
			dropCountBuffer.asFloatBuffer().put(numDrops);

        	float[] rotateZ = FloatUtil.makeRotationEuler(new float[16], 0, 0, 3*FloatUtil.PI/2f, 0);

        	sceneModelMatrixPointer.asFloatBuffer().put(rotateZ);

    	    float[] scale = FloatUtil.makeScale(new float[16], false, 1f, 1f, 1f);
    	    waterModelMatrixPointer.asFloatBuffer().put(scale);
    	    waterDownModelMatrixPointer.asFloatBuffer().put(scale);
        }

        gl.glUseProgram(program.name);
        gl.glBindVertexArray(vertexArrayName.get(VertexArray.SCENE));

        //Bind the global matrices buffer:
        gl.glBindBufferBase(GL_UNIFORM_BUFFER, Semantic.Uniform.TRANSFORM0, bufferNames.get(Buffer.GLOBAL_MATRICES));

        //Bind the model matrices buffer:
        gl.glBindBufferBase(GL_UNIFORM_BUFFER, Semantic.Uniform.TRANSFORM1, bufferNames.get(Buffer.MODEL_MATRIX_SCENE));

        // Bind the light properties buffer to a specified uniform index
        gl.glBindBufferBase(
                GL_UNIFORM_BUFFER,
                Semantic.Uniform.LIGHT0,
                bufferNames.get(Buffer.LIGHT_PROPERTIES));

        // Bind the material properties buffer to a specified uniform index
        gl.glBindBufferBase(
                GL_UNIFORM_BUFFER,
                Semantic.Uniform.MATERIAL,
                bufferNames.get(Buffer.SCENE_MATERIAL_PROPERTIES));

        // Bind the camera properties buffer to a specified uniform index
        gl.glBindBufferBase(
                GL_UNIFORM_BUFFER,
                Semantic.Uniform.CAMERA,
                bufferNames.get(Buffer.CAMERA_PROPERTIES));
        if(wireframe)
        	gl.glPolygonMode(GL_FRONT_AND_BACK, gl.GL_LINE);
        //Draw the triangle
        gl.glDrawElements(GL_TRIANGLES, sceneElementData.length, GL_UNSIGNED_SHORT, 0);

        gl.glPolygonMode(GL_FRONT_AND_BACK, gl.GL_FILL);

    }

    // GLEventListener.reshape implementation
    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {

        // Get OpenGL 4 reference
        GL4 gl = drawable.getGL().getGL4();

        this.width = width;
        this.height = height;

        float[] frustum = FloatUtil.makeFrustum(new float[16], 0, false, -1.6f, 1.6f, -0.9f, 0.9f, 1f, 100f);
        float[] zoomFrustum = FloatUtil.makeFrustum(new float[16], 0, false, -1f, 1f, -0.5625f, 0.5625f, 1f, 100f);

        globalMatricesPointer.asFloatBuffer().put(zoomFrustum);

        //update the mouse picker with the new window information:
		mousePicker.updateProjection(zoomFrustum);
		mousePicker.updateWindow(width, height);

        gl.glViewport(x, y, width, height);
    }

    // GLEventListener.dispose implementation
    @Override
    public void dispose(GLAutoDrawable drawable) {

        // Get OpenGL 4 reference
        GL4 gl = drawable.getGL().getGL4();

        // Unmap all named buffers
        gl.glUnmapNamedBuffer(bufferNames.get(Buffer.GLOBAL_MATRICES));
        gl.glUnmapNamedBuffer(bufferNames.get(Buffer.MODEL_MATRIX_SCENE));
        gl.glUnmapNamedBuffer(bufferNames.get(Buffer.MODEL_MATRIX_WATER));
        gl.glUnmapNamedBuffer(bufferNames.get(Buffer.MODEL_MATRIX_WATER_DOWN));
        gl.glUnmapNamedBuffer(bufferNames.get(Buffer.CLIP_PLANE));
        gl.glUnmapNamedBuffer(bufferNames.get(Buffer.TIME));

        // Delete the program
        gl.glDeleteProgram(program.name);
        gl.glDeleteProgram(waterProgram.name);
        gl.glDeleteProgram(windowProgram.name);

        //Delete the vertex array
        gl.glDeleteVertexArrays(VertexArray.MAX, vertexArrayName);

        //Delete the frame buffers
        gl.glDeleteFramebuffers(FrameBuffers.MAX, frameBufferNames);

        //Delete the buffers
        gl.glDeleteBuffers(Buffer.MAX, bufferNames);
//        gl.glDeleteBuffers(DepthBuffer.MAX, depthBufferName);

        //Delete textures
        gl.glDeleteTextures(Textures.MAX, textureNames);

    }

	// KeyListener.keyPressed implementation
	//		Arrow-key pressed: rotation
	//		Shift + Arrow-key pressed: translation
	@Override
	public void keyPressed(KeyEvent e) {
		//vector to store the movement relative to the camera angle
		float[] movement = {0f, 0f, 0f, 0f};
		if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
			shift = true;
		}
		
		if(!noAutoRepeat) {
			//pressing A or D always leads to a movement to the left or the right
			//--> no difference by pressing shift
			if(e.getKeyCode() == KeyEvent.VK_A) {
				new Thread(()-> {
					for(int i=0; i<10; i++) {
						movement[0] -= 0.01;
						applyMovementToCamera(movement);
						try {
							TimeUnit.MILLISECONDS.sleep(20);
						} catch (InterruptedException e1) {
	
							e1.printStackTrace();
						}
					}
				}).start();
			}
			else if(e.getKeyCode() == KeyEvent.VK_D) {
				new Thread(()-> {
					for(int i=0; i<10; i++) {
						movement[0] += 0.01;
						applyMovementToCamera(movement);
						try {
							TimeUnit.MILLISECONDS.sleep(20);
						} catch (InterruptedException e1) {
	
							e1.printStackTrace();
						}
					}
				}).start();
			}
			// Destroy the window if the escape key is pressed
			else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
				new Thread(() -> {
					window.destroy();
				}).start();
			}
	
			if (shift) {
				// change the movement vector if shift and an arrow key are pressed
				if (e.getKeyCode() == KeyEvent.VK_UP) {
					//movement forwards
					new Thread(()-> {
						for(int i=0; i<10; i++) {
							movement[2] -= 0.01;
							applyMovementToCamera(movement);
							try {
								TimeUnit.MILLISECONDS.sleep(20);
							} catch (InterruptedException e1) {
	
								e1.printStackTrace();
							}
						}
					}).start();
				} else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
					//movement backwards
					new Thread(()-> {
						for(int i=0; i<10; i++) {
							movement[2] += 0.01;
							applyMovementToCamera(movement);
							try {
								TimeUnit.MILLISECONDS.sleep(20);
							} catch (InterruptedException e1) {
	
								e1.printStackTrace();
							}
						}
					}).start();
				} else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
					//movement to the right
					new Thread(()-> {
						for(int i=0; i<10; i++) {
							movement[0] += 0.01;
							applyMovementToCamera(movement);
							try {
								TimeUnit.MILLISECONDS.sleep(20);
							} catch (InterruptedException e1) {
	
								e1.printStackTrace();
							}
						}
					}).start();
				} else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
					//movement to the left
					new Thread(()-> {
						for(int i=0; i<10; i++) {
							movement[0] -= 0.01;
							applyMovementToCamera(movement);
							try {
								TimeUnit.MILLISECONDS.sleep(20);
							} catch (InterruptedException e1) {
	
								e1.printStackTrace();
							}
						}
					}).start();
				}
				else if (e.getKeyCode() == KeyEvent.VK_W) {
					new Thread(()-> {
						for(int i=0; i<10; i++) {
							movement[1] += 0.01;
							applyMovementToCamera(movement);
							try {
								TimeUnit.MILLISECONDS.sleep(20);
							} catch (InterruptedException e1) {
	
								e1.printStackTrace();
							}
						}
					}).start();
				}
				else if (e.getKeyCode() == KeyEvent.VK_S) {
					new Thread(()-> {
						for(int i=0; i<10; i++) {
							movement[1] -= 0.01;
							applyMovementToCamera(movement);
							try {
								TimeUnit.MILLISECONDS.sleep(20);
							} catch (InterruptedException e1) {
	
								e1.printStackTrace();
							}
						}
					}).start();
				}
				else if(e.getKeyCode() == KeyEvent.VK_L)
					wireframeWater = !wireframeWater;
			}
			else {
				// change the rotation for the view matrix if an arrow key is pressed without shift
				if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
					new Thread(() -> {
						for(int i=0; i<8; i++) {
							angleY += 0.01;
							try {
								TimeUnit.MILLISECONDS.sleep(20);
							} catch (InterruptedException e1) {
	
								e1.printStackTrace();
							}
						}
						//angleY += 0.08f;
					}).start();
				}
				if (e.getKeyCode() == KeyEvent.VK_LEFT) {
					new Thread(() -> {
						for(int i=0; i<8; i++) {
							angleY -= 0.01;
							try {
								TimeUnit.MILLISECONDS.sleep(20);
							} catch (InterruptedException e1) {
	
								e1.printStackTrace();
							}
						}
					}).start();
				}
				if (e.getKeyCode() == KeyEvent.VK_UP) {
					new Thread(() -> {
						for(int i=0; i<8; i++) {
							angleX -= 0.01;
							try {
								TimeUnit.MILLISECONDS.sleep(20);
							} catch (InterruptedException e1) {
	
								e1.printStackTrace();
							}
						}
					}).start();
				}
				if (e.getKeyCode() == KeyEvent.VK_DOWN) {
					new Thread(() -> {
						for(int i=0; i<8; i++) {
							angleX += 0.01;
							try {
								TimeUnit.MILLISECONDS.sleep(20);
							} catch (InterruptedException e1) {
	
								e1.printStackTrace();
							}
						}
					}).start();
				}
				if(e.getKeyCode() == KeyEvent.VK_W) {
					new Thread(()-> {
						for(int i=0; i<10; i++) {
							movement[2] -= 0.01;
							applyMovementToCamera(movement);
							try {
								TimeUnit.MILLISECONDS.sleep(20);
							} catch (InterruptedException e1) {
	
								e1.printStackTrace();
							}
						}
					}).start();
					//movement[1] = 1f;
				}
				else if(e.getKeyCode() == KeyEvent.VK_S) {
					new Thread(()-> {
						for(int i=0; i<10; i++) {
							movement[2] += 0.01;
							applyMovementToCamera(movement);
							try {
								TimeUnit.MILLISECONDS.sleep(20);
							} catch (InterruptedException e1) {
	
								e1.printStackTrace();
							}
						}
					}).start();
				}
				if (e.getKeyCode() == KeyEvent.VK_N) {
					new Thread(() -> {
						int tmp = maxNumDrops;
						for (int i = 0; i < dropStartTime.length; i++) {
							if (dropStartTime[i] == 0f) {
								tmp = i;
								break;
							}
						}
						if (tmp != maxNumDrops) {
							numDrops++;
							float random_x = 7f * (float) Math.random();
							float random_z = 7f * (float) Math.random();
							dropStartTime[tmp] = System.currentTimeMillis();
							dropPositions[tmp][0] = random_x;
							dropPositions[tmp][1] = random_z;
							try {
								TimeUnit.SECONDS.sleep(7);
								numDrops--;
								dropStartTime[tmp] = 0;
							} catch (InterruptedException e1) {
	
								e1.printStackTrace();
							}
						}
	
					}).start();
				}
				if(e.getKeyCode() == KeyEvent.VK_L) {
					wireframe = !wireframe;
				}
			}
		}
		else {
			pressed.add(e.getKeyCode());
		}
	}

	private void applyMovementToCamera(float[] movement) {
		//Create rotation matrix to make the movement relative to the camera angle
		float[] rotateY = FloatUtil.makeRotationEuler(new float[16], 0, 0f, -angleY, 0f);
		float[] rotateX = FloatUtil.makeRotationEuler(new float[16], 0, -angleX, 0, 0f);
		float[] roation = FloatUtil.multMatrix(rotateY, rotateX);

		//apply rotation to movement vector to turn it in the direction of the camera
		movement = FloatUtil.multMatrixVec(roation, movement, new float[4]);

		//add the changed movement vector to the camera position
		cameraProperties[0] = cameraProperties[0] + movement[0];
		cameraProperties[1] = cameraProperties[1] + movement[1];
		cameraProperties[2] = cameraProperties[2] + movement[2];
	}
	
	//In case auto repeat keys are deactivated on the system this method is triggered
	//in every execution of display(). 
	//This allows multiple keys to be pressed and interpreted at once and therefore 
	//it allows moving into multiple directions at once.
	private void applyKeys() {
		//Iterator to have access to the pressed keys
		Iterator<Short> it = pressed.iterator();
		
		//variable to store the keyCode of the current key for all the comparisons
		short tmp;
		
		//define how many units the camera moves per change
		float step = 0.2f;
		
		//movement vector to store the movment relative to the camera angle
		float[] movement = { 0f, 0f, 0f, 0f };

		while (it.hasNext()) {
			tmp = (short) it.next();
			// pressing A or D always leads to a movement to the left or the right
			// --> no difference by pressing shift
			if (tmp == KeyEvent.VK_A) {
				movement[0] -= step;
				applyMovementToCamera(movement);
			} else if (tmp == KeyEvent.VK_D) {
				movement[0] += step;
				applyMovementToCamera(movement);
			}
			// Destroy the window if the escape key is pressed
			else if (tmp == KeyEvent.VK_ESCAPE) {
				new Thread(() -> {
					window.destroy();
				}).start();
			}

			if (shift) {
				// change the movement vector if shift and an arrow key are pressed
				if (tmp == KeyEvent.VK_UP) {
					// movement forwards
					movement[2] -= step;
					applyMovementToCamera(movement);
				} else if (tmp == KeyEvent.VK_DOWN) {
					movement[2] += step;
					applyMovementToCamera(movement);
				} else if (tmp == KeyEvent.VK_RIGHT) {
					movement[0] += step;
					applyMovementToCamera(movement);
				} else if (tmp == KeyEvent.VK_LEFT) {
					movement[0] -= step;
					applyMovementToCamera(movement);
				} else if (tmp == KeyEvent.VK_W) {
					movement[1] += step;
					applyMovementToCamera(movement);
				} else if (tmp == KeyEvent.VK_S) {
					movement[1] -= step;
					applyMovementToCamera(movement);
				} else if (tmp == KeyEvent.VK_L)
					wireframeWater = !wireframeWater;
			} else {
				// change the rotation for the view matrix if an arrow key is pressed without
				// shift
				if (tmp == KeyEvent.VK_RIGHT) {
					angleY += 0.03f;
				}
				if (tmp == KeyEvent.VK_LEFT) {

					angleY -= 0.03f;

				}
				if (tmp == KeyEvent.VK_UP) {

					angleX -= 0.02f;

				}
				if (tmp == KeyEvent.VK_DOWN) {

					angleX += 0.02f;

				}
				if (tmp == KeyEvent.VK_W) {

					movement[2] -= step;
					applyMovementToCamera(movement);
				} else if (tmp == KeyEvent.VK_S) {

					movement[2] += step;
					applyMovementToCamera(movement);
				}
				if (tmp == KeyEvent.VK_N) {
					//pressing the key "N" triggers a wave with a random center
					new Thread(() -> {
						//search for a free spot in the array (time != 0f)
						int temp = maxNumDrops;
						for (int i = 0; i < dropStartTime.length; i++) {
							if (dropStartTime[i] == 0f) {
								temp = i;
								break;
							}
						}
						//define a new drop:
						if (temp != maxNumDrops) {
							numDrops++;
							float random_x = 7f * (float) Math.random();
							float random_z = 7f * (float) Math.random();
							dropStartTime[temp] = System.currentTimeMillis();
							dropPositions[temp][0] = random_x;
							dropPositions[temp][1] = random_z;
							try {
								//wait seven seconds until drop is deleted and drop counter is decreased
								TimeUnit.SECONDS.sleep(7);
								numDrops--;
								dropStartTime[temp] = 0;
							} catch (InterruptedException e1) {
								e1.printStackTrace();
							}
						}
					}).start();
				}
				if (tmp == KeyEvent.VK_L) {
					//pressing "L" changes the way OpenGL renders the triangles 
					//of the scene model (either as lines or as faces)
					wireframe = !wireframe;
				}
			}
		}
	}

	// KeyListener.keyPressed implementation
	@Override
	public void keyReleased(KeyEvent e) {
		if(e.getKeyCode() == KeyEvent.VK_SHIFT) {
			shift = false;
		}
		
		if(noAutoRepeat)
			pressed.remove(e.getKeyCode());
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		//trigger mousePicker to calculate the mouse ray
		float[] mouseRay = mousePicker.getMouseRay(e.getX(), e.getY());

		//search for the intersection of the mouse ray with the xz-plane
		float[] dropXZ = computeIntersection(mouseRay);
		System.out.println("dropX: " + dropXZ[0] + " dropZ: " + dropXZ[1]);
		
		//check if intersection is on water mesh
		if(FloatUtil.abs(dropXZ[0]) <= 7f && FloatUtil.abs(dropXZ[1]) <= 7f)
			addDropAt(dropXZ[0], dropXZ[1]);
	}

	@Override
	public void mouseEntered(MouseEvent e) {

	}

	@Override
	public void mouseExited(MouseEvent e) {

	}

	@Override
	public void mousePressed(MouseEvent e) {

	}

	@Override
	public void mouseReleased(MouseEvent e) {
		//reset
		mouseXOld = -20;
		mouseYOld = -20;
	}

	@Override
	public void mouseMoved(MouseEvent e) {

	}

	@Override
	public void mouseDragged(MouseEvent e) {
		//check if mouse position is more than 20 pixels away from the last wave's center
		if(FloatUtil.sqrt((e.getX()-mouseXOld)*(e.getX()-mouseXOld) + (e.getY()-mouseYOld)*(e.getY()-mouseYOld)) >= 20) {
			//trigger calculation of mouse ray using the mousePicker
			float[] mouseRay = mousePicker.getMouseRay(e.getX(), e.getY());
			//search for intersection of mouse ray with xz-plane
			float[] dropXZ = computeIntersection(mouseRay);

			//check if intersection is on the water mesh
			if(FloatUtil.abs(dropXZ[0]) <= 7f && FloatUtil.abs(dropXZ[1]) <= 7f)
				addDropAt(dropXZ[0], dropXZ[1]);

			//restore the old position
			mouseXOld = e.getX();
			mouseYOld = e.getY();

		}
	}

	@Override
	public void mouseWheelMoved(MouseEvent e) {
		// TODO Auto-generated method stub

	}

    public void initDebug(GL4 gl) {

        // Register a new debug listener
        window.getContext().addGLDebugListener(new GLDebugListener() {
            // Output any messages to standard out
            @Override
            public void messageSent(GLDebugMessage event) {
                //System.out.println(event);
            }
        });

        // Ignore all messages
        gl.glDebugMessageControl(
                GL_DONT_CARE,
                GL_DONT_CARE,
                GL_DONT_CARE,
                0,
                null,
                false);

        // Enable messages of high severity
        gl.glDebugMessageControl(
                GL_DONT_CARE,
                GL_DONT_CARE,
                GL_DEBUG_SEVERITY_HIGH,
                0,
                null,
                true);

        // Enable messages of medium severity
        gl.glDebugMessageControl(
                GL_DONT_CARE,
                GL_DONT_CARE,
                GL_DEBUG_SEVERITY_MEDIUM,
                0,
                null,
                true);

    }

    public void parseData() {
    	try {
			ObjModelParser parser = new ObjModelParser("models/tree3_new");

			ParserModel sceneModel = new ParserModel();
			sceneModel = parser.parseModel();

			sceneVertexData = sceneModel.getVertexData();
			sceneElementData = sceneModel.getElementData();
		} catch (FileNotFoundException e) {

			e.printStackTrace();
		}
    	catch(IOException e) {
    		e.printStackTrace();
    	}

    }

    public void initBuffers(GL4 gl) {
    	for(int i = 0; i<detail; i++) {
    		waterVertexData = subdivideMesh(waterVertexData, waterElementData);
    		waterElementData = createMeshElementData(waterVertexData);
    	}

    	for(int i = 0; i<detail; i++) {
    		waterDownVertexData = subdivideMesh(waterDownVertexData, waterDownElementData);
    		waterDownElementData = createMeshElementData(waterDownVertexData);
    	}

    	FloatBuffer sceneVertexBuffer = GLBuffers.newDirectFloatBuffer(sceneVertexData);
    	ShortBuffer sceneElementBuffer = GLBuffers.newDirectShortBuffer(sceneElementData);

    	FloatBuffer waterVertexBuffer = GLBuffers.newDirectFloatBuffer(waterVertexData);
    	ShortBuffer waterElementBuffer = GLBuffers.newDirectShortBuffer(waterElementData);

    	FloatBuffer waterDownVertexBuffer = GLBuffers.newDirectFloatBuffer(waterDownVertexData);
    	ShortBuffer waterDownElementBuffer = GLBuffers.newDirectShortBuffer(waterDownElementData);

    	 // Create a direct buffer for the light properties
        FloatBuffer lightBuffer = GLBuffers.newDirectFloatBuffer(lightProperties);

        // Create a direct buffer for the material properties
        FloatBuffer sceneMaterialBuffer = GLBuffers.newDirectFloatBuffer(sceneMaterialProperties);
        FloatBuffer waterMaterialBuffer = GLBuffers.newDirectFloatBuffer(waterMaterialProperties);

    	gl.glCreateBuffers(Buffer.MAX, bufferNames);

    	if(!bug1287) {
    		//Buffer storage for vertex data:
    		gl.glNamedBufferStorage(bufferNames.get(Buffer.SCENE_V), sceneVertexBuffer.capacity() * Float.BYTES, sceneVertexBuffer, GL_STATIC_DRAW);
    		gl.glNamedBufferStorage(bufferNames.get(Buffer.WATER_V), waterVertexBuffer.capacity() * Float.BYTES, waterVertexBuffer, GL_STATIC_DRAW);
    		gl.glNamedBufferStorage(bufferNames.get(Buffer.WATER_DOWN_V), waterDownVertexBuffer.capacity() * Float.BYTES, waterDownVertexBuffer, GL_STATIC_DRAW);

    		//Buffer storage for element data:
    		gl.glNamedBufferStorage(bufferNames.get(Buffer.SCENE_E), sceneElementBuffer.capacity() * Short.BYTES, sceneElementBuffer, GL_STATIC_DRAW);
    		gl.glNamedBufferStorage(bufferNames.get(Buffer.WATER_E), waterElementBuffer.capacity()*Short.BYTES, waterElementBuffer, GL_STATIC_DRAW);
    		gl.glNamedBufferStorage(bufferNames.get(Buffer.WATER_DOWN_E), waterDownElementBuffer.capacity()*Short.BYTES, waterDownElementBuffer, GL_STATIC_DRAW);

    		//Buffer for global Matrix:
    		gl.glNamedBufferStorage(bufferNames.get(Buffer.GLOBAL_MATRICES), 16*4*2, null, GL_MAP_WRITE_BIT);

    		//Buffer for model Matrix:
    		gl.glNamedBufferStorage(bufferNames.get(Buffer.MODEL_MATRIX_SCENE), 16*4, null, GL_MAP_WRITE_BIT);
    		gl.glNamedBufferStorage(bufferNames.get(Buffer.MODEL_MATRIX_WATER), 16*4, null, GL_MAP_WRITE_BIT);
    		gl.glNamedBufferStorage(bufferNames.get(Buffer.MODEL_MATRIX_WATER_DOWN), 16*4, null, GL_MAP_WRITE_BIT);

    		// Create and initialize a named buffer storage for the light properties
            gl.glNamedBufferStorage(bufferNames.get(Buffer.LIGHT_PROPERTIES), 16 * Float.BYTES, lightBuffer, 0);

            // Create and initialize a named buffer storage for the material properties
            gl.glNamedBufferStorage(bufferNames.get(Buffer.SCENE_MATERIAL_PROPERTIES), 1 * Float.BYTES, sceneMaterialBuffer, 0);
            gl.glNamedBufferStorage(bufferNames.get(Buffer.WATER_MATERIAL_PROPERTIES), 1 * Float.BYTES, waterMaterialBuffer, 0);

            // Create and initialize a named buffer storage for the camera properties
            gl.glNamedBufferStorage(bufferNames.get(Buffer.CAMERA_PROPERTIES), 3 * Float.BYTES, null, GL_MAP_WRITE_BIT);

			// Create and initialize a named buffer storage for the time property
			gl.glNamedBufferStorage(bufferNames.get(Buffer.NOISE_TIME), 1 * Float.BYTES, null, GL_MAP_WRITE_BIT);
			gl.glNamedBufferStorage(bufferNames.get(Buffer.DROP_DATA), maxNumDrops * 3 * Float.BYTES, null, GL_MAP_WRITE_BIT);
			gl.glNamedBufferStorage(bufferNames.get(Buffer.DROP_COUNT), 1 * Float.BYTES, null, GL_MAP_WRITE_BIT);

            //Create and initialize a named buffer storage for the time property
            gl.glNamedBufferStorage(bufferNames.get(Buffer.CLIP_PLANE), 4 * Float.BYTES, null, GL_MAP_WRITE_BIT);

    	}

    	else {
    		//Buffer for Vertex Data:
    		 gl.glBindBuffer(GL_ARRAY_BUFFER, bufferNames.get(Buffer.SCENE_V));
             gl.glBufferStorage(GL_ARRAY_BUFFER, sceneVertexBuffer.capacity() * Float.BYTES, sceneVertexBuffer, 0);
             gl.glBindBuffer(GL_ARRAY_BUFFER, 0);

             gl.glBindBuffer(GL_ARRAY_BUFFER, bufferNames.get(Buffer.WATER_V));
             gl.glBufferStorage(GL_ARRAY_BUFFER, waterVertexBuffer.capacity() * Float.BYTES, waterVertexBuffer, 0);
             gl.glBindBuffer(GL_ARRAY_BUFFER, 0);

             gl.glBindBuffer(GL_ARRAY_BUFFER, bufferNames.get(Buffer.WATER_DOWN_V));
             gl.glBufferStorage(GL_ARRAY_BUFFER, waterDownVertexBuffer.capacity() * Float.BYTES, waterDownVertexBuffer, 0);
             gl.glBindBuffer(GL_ARRAY_BUFFER, 0);

             //Buffer for Element Data:
             gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufferNames.get(Buffer.SCENE_E));
             gl.glBufferStorage(GL_ELEMENT_ARRAY_BUFFER, sceneElementBuffer.capacity() * Short.BYTES, sceneElementBuffer, 0);
             gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

             gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufferNames.get(Buffer.WATER_E));
             gl.glBufferStorage(GL_ELEMENT_ARRAY_BUFFER, waterElementBuffer.capacity() * Short.BYTES, waterElementBuffer, 0);
             gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

             gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufferNames.get(Buffer.WATER_DOWN_E));
             gl.glBufferStorage(GL_ELEMENT_ARRAY_BUFFER, waterDownElementBuffer.capacity() * Short.BYTES, waterDownElementBuffer, 0);
             gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

             //Retrieve the uniform buffer offset alignment minimum
             IntBuffer uniformBufferOffset = GLBuffers.newDirectIntBuffer(1);
             gl.glGetIntegerv(GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT, uniformBufferOffset);

             //Set the required bytes for the matrices in accordance to the uniform buffer offset alignment:
             int globalBlockSize = Math.max(16 * 4 * 2, uniformBufferOffset.get(0));
             int modelBlockSize = Math.max(16 * 4, uniformBufferOffset.get(0));
             int lightBlockSize = Math.max(12 * Float.BYTES, uniformBufferOffset.get(0));
             int materialBlockSize = Math.max(3 * Float.BYTES, uniformBufferOffset.get(0));
             int cameraBlockSize = Math.max(3 * Float.BYTES, uniformBufferOffset.get(0));
             int clipPlaneBlockSize = Math.max(4 * Float.BYTES, uniformBufferOffset.get(0));
             int noiseTimeBlockSize = Math.max(Float.BYTES, uniformBufferOffset.get(0));
			 int dropPosBlockSize = Math.max(maxNumDrops * 3 * Float.BYTES, uniformBufferOffset.get(0));

             // Create and initialize a named storage for the global matrices
             gl.glBindBuffer(GL_UNIFORM_BUFFER, bufferNames.get(Buffer.GLOBAL_MATRICES));
             gl.glBufferStorage(GL_UNIFORM_BUFFER, globalBlockSize, null, GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT);
             gl.glBindBuffer(GL_UNIFORM_BUFFER, 0);

             // Create and initialize a named storage for the model matrix
             gl.glBindBuffer(GL_UNIFORM_BUFFER, bufferNames.get(Buffer.MODEL_MATRIX_SCENE));
             gl.glBufferStorage(GL_UNIFORM_BUFFER, modelBlockSize, null, GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT);
             gl.glBindBuffer(GL_UNIFORM_BUFFER, 0);

             gl.glBindBuffer(GL_UNIFORM_BUFFER, bufferNames.get(Buffer.MODEL_MATRIX_WATER));
             gl.glBufferStorage(GL_UNIFORM_BUFFER, modelBlockSize, null, GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT);
             gl.glBindBuffer(GL_UNIFORM_BUFFER, 0);

             gl.glBindBuffer(GL_UNIFORM_BUFFER, bufferNames.get(Buffer.MODEL_MATRIX_WATER_DOWN));
             gl.glBufferStorage(GL_UNIFORM_BUFFER, modelBlockSize, null, GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT);
             gl.glBindBuffer(GL_UNIFORM_BUFFER, 0);

             // Create and initialize a named buffer storage for the light properties
             gl.glBindBuffer(GL_UNIFORM_BUFFER, bufferNames.get(Buffer.LIGHT_PROPERTIES));
             gl.glBufferStorage(GL_UNIFORM_BUFFER, lightBlockSize, lightBuffer, 0);
             gl.glBindBuffer(GL_UNIFORM_BUFFER, 0);

             gl.glBindBuffer(GL_UNIFORM_BUFFER, bufferNames.get(Buffer.SCENE_MATERIAL_PROPERTIES));
             gl.glBufferStorage(GL_UNIFORM_BUFFER, materialBlockSize, sceneMaterialBuffer, 0);
             gl.glBindBuffer(GL_UNIFORM_BUFFER, 0);

             gl.glBindBuffer(GL_UNIFORM_BUFFER, bufferNames.get(Buffer.WATER_MATERIAL_PROPERTIES));
             gl.glBufferStorage(GL_UNIFORM_BUFFER, materialBlockSize, waterMaterialBuffer, 0);
             gl.glBindBuffer(GL_UNIFORM_BUFFER, 0);

			 // Create and initialize a named buffer storage for the camera properties
			 gl.glBindBuffer(GL_UNIFORM_BUFFER, bufferNames.get(Buffer.CAMERA_PROPERTIES));
			 gl.glBufferStorage(GL_UNIFORM_BUFFER, cameraBlockSize, null, GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT);
			 gl.glBindBuffer(GL_UNIFORM_BUFFER, 0);

             //Create and initialize a named buffer storage for the clipping plane property
             gl.glBindBuffer(GL_UNIFORM_BUFFER, bufferNames.get(Buffer.CLIP_PLANE));
             gl.glBufferStorage(GL_UNIFORM_BUFFER, clipPlaneBlockSize, null, GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT);
             gl.glBindBuffer(GL_UNIFORM_BUFFER, 0);

			 gl.glBindBuffer(GL_UNIFORM_BUFFER, bufferNames.get(Buffer.NOISE_TIME));
			 gl.glBufferStorage(GL_UNIFORM_BUFFER, noiseTimeBlockSize, null, GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT);
			 gl.glBindBuffer(GL_UNIFORM_BUFFER, 0);

			 // Create and initialize a named buffer storage for the drop properties:
			 gl.glBindBuffer(GL_UNIFORM_BUFFER, bufferNames.get(Buffer.DROP_DATA));
			 gl.glBufferStorage(GL_UNIFORM_BUFFER, dropPosBlockSize, null, GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT);
			 gl.glBindBuffer(GL_UNIFORM_BUFFER, 0);

			 gl.glBindBuffer(GL_UNIFORM_BUFFER, bufferNames.get(Buffer.DROP_COUNT));
			 gl.glBufferStorage(GL_UNIFORM_BUFFER, noiseTimeBlockSize, null, GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT);
			 gl.glBindBuffer(GL_UNIFORM_BUFFER, 0);
    	}

             globalMatricesPointer = gl.glMapNamedBufferRange(
            		 bufferNames.get(Buffer.GLOBAL_MATRICES),
            		 0, 16*4*2,
            		 GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT | GL_MAP_INVALIDATE_BUFFER_BIT);

             sceneModelMatrixPointer = gl.glMapNamedBufferRange(
                     bufferNames.get(Buffer.MODEL_MATRIX_SCENE),
                     0, 16 * 4,
                     GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT | GL_MAP_INVALIDATE_BUFFER_BIT);

             waterModelMatrixPointer = gl.glMapNamedBufferRange(
            		 bufferNames.get(Buffer.MODEL_MATRIX_WATER),
            		 0, 16*4,
            		 GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT | GL_MAP_INVALIDATE_BUFFER_BIT);

             waterDownModelMatrixPointer = gl.glMapNamedBufferRange(
            		 bufferNames.get(Buffer.MODEL_MATRIX_WATER_DOWN),
            		 0, 16*4,
            		 GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT | GL_MAP_INVALIDATE_BUFFER_BIT);

             clipPlanePointer = gl.glMapNamedBufferRange(
            		 bufferNames.get(Buffer.CLIP_PLANE),
            		 0, 4*Float.BYTES,
            		 GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT | GL_MAP_INVALIDATE_BUFFER_BIT);

			noiseTimePointer = gl.glMapNamedBufferRange(
					bufferNames.get(Buffer.NOISE_TIME),
					0, 1 * Float.BYTES,
					GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT | GL_MAP_INVALIDATE_BUFFER_BIT);

			dropBuffer = gl.glMapNamedBufferRange(
					bufferNames.get(Buffer.DROP_DATA),
					0, maxNumDrops * 3 * Float.BYTES,
					GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT | GL_MAP_INVALIDATE_BUFFER_BIT);

			dropCountBuffer = gl.glMapNamedBufferRange(
					bufferNames.get(Buffer.DROP_COUNT),
					0, Float.BYTES,
					GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT | GL_MAP_INVALIDATE_BUFFER_BIT);

			cameraBuffer = gl.glMapNamedBufferRange(
					bufferNames.get(Buffer.CAMERA_PROPERTIES),
					0, 3*Float.BYTES,
					GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT | GL_MAP_INVALIDATE_BUFFER_BIT);
    }

    private void initVertexArray(GL4 gl) {
    	//Create vertex array object
    	gl.glCreateVertexArrays(VertexArray.MAX, vertexArrayName);

    	//Associate the vertex attributes in the vertex array object with the vertex Buffer
    	gl.glVertexArrayAttribBinding(vertexArrayName.get(VertexArray.SCENE), Semantic.Attr.POSITION, Semantic.Stream.A);
    	gl.glVertexArrayAttribBinding(vertexArrayName.get(VertexArray.SCENE), Semantic.Attr.COLOR, Semantic.Stream.A);
    	gl.glVertexArrayAttribBinding(vertexArrayName.get(VertexArray.SCENE), Semantic.Attr.NORMAL, Semantic.Stream.A);

    	gl.glVertexArrayAttribBinding(vertexArrayName.get(VertexArray.WATER), Semantic.Attr.POSITION, Semantic.Stream.A);
    	gl.glVertexArrayAttribBinding(vertexArrayName.get(VertexArray.WATER), Semantic.Attr.COLOR, Semantic.Stream.A);
    	gl.glVertexArrayAttribBinding(vertexArrayName.get(VertexArray.WATER), Semantic.Attr.NORMAL, Semantic.Stream.A);

    	gl.glVertexArrayAttribBinding(vertexArrayName.get(VertexArray.WATER_DOWN), Semantic.Attr.POSITION, Semantic.Stream.A);
    	gl.glVertexArrayAttribBinding(vertexArrayName.get(VertexArray.WATER_DOWN), Semantic.Attr.COLOR, Semantic.Stream.A);
    	gl.glVertexArrayAttribBinding(vertexArrayName.get(VertexArray.WATER_DOWN), Semantic.Attr.NORMAL, Semantic.Stream.A);

    	 // Set the format of the vertex attributes in the vertex array object
        gl.glVertexArrayAttribFormat(vertexArrayName.get(VertexArray.SCENE), Semantic.Attr.POSITION, 3, GL_FLOAT, false, 0);
        gl.glVertexArrayAttribFormat(vertexArrayName.get(VertexArray.SCENE), Semantic.Attr.COLOR, 3, GL_FLOAT, false, 3 * 4);
        gl.glVertexArrayAttribFormat(vertexArrayName.get(VertexArray.SCENE), Semantic.Attr.NORMAL, 3, GL_FLOAT, false, 6 * 4);

        gl.glVertexArrayAttribFormat(vertexArrayName.get(VertexArray.WATER), Semantic.Attr.POSITION, 3, GL_FLOAT, false, 0);
        gl.glVertexArrayAttribFormat(vertexArrayName.get(VertexArray.WATER), Semantic.Attr.COLOR, 3, GL_FLOAT, false, 3 * 4);
        gl.glVertexArrayAttribFormat(vertexArrayName.get(VertexArray.WATER), Semantic.Attr.NORMAL, 3, GL_FLOAT, false, 6 * 4);

        gl.glVertexArrayAttribFormat(vertexArrayName.get(VertexArray.WATER_DOWN), Semantic.Attr.POSITION, 3, GL_FLOAT, false, 0);
        gl.glVertexArrayAttribFormat(vertexArrayName.get(VertexArray.WATER_DOWN), Semantic.Attr.COLOR, 3, GL_FLOAT, false, 3 * 4);
        gl.glVertexArrayAttribFormat(vertexArrayName.get(VertexArray.WATER_DOWN), Semantic.Attr.NORMAL, 3, GL_FLOAT, false, 5 * 4);

        // Enable the vertex attributes in the vertex object
        gl.glEnableVertexArrayAttrib(vertexArrayName.get(VertexArray.SCENE), Semantic.Attr.POSITION);
        gl.glEnableVertexArrayAttrib(vertexArrayName.get(VertexArray.SCENE), Semantic.Attr.COLOR);
        gl.glEnableVertexArrayAttrib(vertexArrayName.get(VertexArray.SCENE), Semantic.Attr.NORMAL);

        gl.glEnableVertexArrayAttrib(vertexArrayName.get(VertexArray.WATER), Semantic.Attr.POSITION);
        gl.glEnableVertexArrayAttrib(vertexArrayName.get(VertexArray.WATER), Semantic.Attr.COLOR);
        gl.glEnableVertexArrayAttrib(vertexArrayName.get(VertexArray.WATER), Semantic.Attr.NORMAL);

        gl.glEnableVertexArrayAttrib(vertexArrayName.get(VertexArray.WATER_DOWN), Semantic.Attr.POSITION);
        gl.glEnableVertexArrayAttrib(vertexArrayName.get(VertexArray.WATER_DOWN), Semantic.Attr.COLOR);
        gl.glEnableVertexArrayAttrib(vertexArrayName.get(VertexArray.WATER_DOWN), Semantic.Attr.NORMAL);

        // Bind the triangle indices in the vertex array object the triangle indices buffer
        gl.glVertexArrayElementBuffer(vertexArrayName.get(VertexArray.SCENE), bufferNames.get(Buffer.SCENE_E));
        gl.glVertexArrayElementBuffer(vertexArrayName.get(VertexArray.WATER), bufferNames.get(Buffer.WATER_E));
        gl.glVertexArrayElementBuffer(vertexArrayName.get(VertexArray.WATER_DOWN), bufferNames.get(Buffer.WATER_DOWN_E));

        // Bind the vertex array object to the vertex buffer
        gl.glVertexArrayVertexBuffer(vertexArrayName.get(VertexArray.SCENE), Semantic.Stream.A, bufferNames.get(Buffer.SCENE_V), 0, (3 + 3 + 3) * 4);
        gl.glVertexArrayVertexBuffer(vertexArrayName.get(VertexArray.WATER), Semantic.Stream.A, bufferNames.get(Buffer.WATER_V), 0, (3 + 3 + 3) * 4);
        gl.glVertexArrayVertexBuffer(vertexArrayName.get(VertexArray.WATER_DOWN), Semantic.Stream.A, bufferNames.get(Buffer.WATER_DOWN_V), 0, (3 + 3 + 3) * 4);
    }

    private void initFrameBuffers(GL4 gl){

    	// GENERATE FRAME BUFFERS FOR THE WATER
    	gl.glGenFramebuffers(FrameBuffers.MAX, frameBufferNames);

    	// GENERATE TETXURES FOR THE FRAME BUFFERS
    	gl.glGenTextures(Textures.MAX, textureNames);

    	// GENERATE RENDER BUFFERS FOR THE FRAME BUFFERS
    	gl.glGenRenderbuffers(DepthBuffer.MAX, depthBufferName);

    	// ============================= INITIALISE REFLECTION FRAME BUFFER ===========================

    	// Bind the frame buffer
    	gl.glBindFramebuffer(GL_FRAMEBUFFER, frameBufferNames.get(FrameBuffers.REFLECTION_FB));

    	// --------- REFLECTION COLOR TEXTURE ----------

    	// Bind the texture that is going to be attached
    	gl.glBindTexture(GL_TEXTURE_2D, textureNames.get(Textures.REFLECTION_COLOR_T));

    	gl.glTexImage2D(
    			GL_TEXTURE_2D,
    			0,
    			GL_RGBA,
    			reflectionWidth,
    			reflectionHeight,
    			0,
    			GL_RGBA,
    			GL_UNSIGNED_BYTE,
    			null
    	);

    	gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    	gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

    	// Unbind the texture
    	gl.glBindTexture(GL_TEXTURE_2D, 0);

    	// Attatch the texture to the frame buffer
    	gl.glFramebufferTexture2D(
    			GL_FRAMEBUFFER,
    			GL_COLOR_ATTACHMENT0,
    			GL_TEXTURE_2D,
    			textureNames.get(Textures.REFLECTION_COLOR_T),
    			0
    	);

    	// --------- REFLECTION DEPTH BUFFER ----------

    	// Bind the reder buffer that is going to be attached
    	gl.glBindRenderbuffer(GL_RENDERBUFFER, depthBufferName.get(DepthBuffer.REFLECTION_DEPTH_B));
        gl.glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT, reflectionWidth, reflectionHeight);

        // Unbind the render buffer
        gl.glBindRenderbuffer(GL_RENDERBUFFER, 0);

    	// Attatch the depth buffer to the frame buffer
    	gl.glFramebufferRenderbuffer(
    			GL_FRAMEBUFFER,
    			GL_DEPTH_ATTACHMENT,
    			GL_RENDERBUFFER,
    			depthBufferName.get(DepthBuffer.REFLECTION_DEPTH_B)
    	);

    	// --------------------------------------------

        // Make sure the frame buffer has been properly created
        if (gl.glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            System.out.println("The reflection framebuffer could not be created");

        // ============================= INITIALISE REFRACTION FRAME BUFFER ===========================

    	// Bind the frame buffer
    	gl.glBindFramebuffer(GL_FRAMEBUFFER, frameBufferNames.get(FrameBuffers.REFRACTION_FB));

    	// --------- REFRACTION COLOR TEXTURE ----------

    	// Bind the texture that is going to be attached
    	gl.glBindTexture(GL_TEXTURE_2D, textureNames.get(Textures.REFRACTION_COLOR_T));

    	gl.glTexImage2D(
    			GL_TEXTURE_2D,
    			0,
    			GL_RGBA,
    			refractionWidth,
    			refractionHeight,
    			0,
    			GL_RGBA,
    			GL_UNSIGNED_BYTE,
    			null
    	);

    	gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    	gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

    	// Unbind the texture
    	gl.glBindTexture(GL_TEXTURE_2D, 0);

    	// Attatch the texture to the frame buffer
    	gl.glFramebufferTexture2D(
    			GL_FRAMEBUFFER,
    			GL_COLOR_ATTACHMENT0,
    			GL_TEXTURE_2D,
    			textureNames.get(Textures.REFRACTION_COLOR_T),
    			0
    	);

    	// --------- REFRACTION DEPTH TEXTURE ----------

    	// Bind the texture that is going to be attached
    	gl.glBindTexture(GL_TEXTURE_2D, textureNames.get(Textures.REFRACTION_DEPTH_T));

    	gl.glTexImage2D(
    			GL_TEXTURE_2D,
    			0,
    			GL_DEPTH_COMPONENT32,
    			refractionWidth,
    			refractionHeight,
    			0,
    			GL_DEPTH_COMPONENT,
    			GL_FLOAT,
    			null
    	);

    	gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    	gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

    	// Unbind the texture
    	gl.glBindTexture(GL_TEXTURE_2D, 0);

    	// Attatch the texture to the frame buffer
    	gl.glFramebufferTexture2D(
    			GL_FRAMEBUFFER,
    			GL_DEPTH_ATTACHMENT,
    			GL_TEXTURE_2D,
    			textureNames.get(Textures.REFRACTION_DEPTH_T),
    			0
    	);

    	// --------------------------------------------

        // Make sure the frame buffer has been properly created
        if (gl.glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            System.out.println("The framebuffer could not be created");

        // ============================= INITIALISE DOWN REFLECTION FRAME BUFFER ===========================

    	// Bind the frame buffer
    	gl.glBindFramebuffer(GL_FRAMEBUFFER, frameBufferNames.get(FrameBuffers.REFLECTION_FB_DOWN));

    	// --------- REFLECTION COLOR TEXTURE ----------

    	// Bind the texture that is going to be attached
    	gl.glBindTexture(GL_TEXTURE_2D, textureNames.get(Textures.REFLECTION_COLOR_T_DOWN));

    	gl.glTexImage2D(
    			GL_TEXTURE_2D,
    			0,
    			GL_RGBA,
    			reflectionWidth,
    			reflectionHeight,
    			0,
    			GL_RGBA,
    			GL_UNSIGNED_BYTE,
    			null
    	);

    	gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    	gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

    	// Unbind the texture
    	gl.glBindTexture(GL_TEXTURE_2D, 0);

    	// Attatch the texture to the frame buffer
    	gl.glFramebufferTexture2D(
    			GL_FRAMEBUFFER,
    			GL_COLOR_ATTACHMENT0,
    			GL_TEXTURE_2D,
    			textureNames.get(Textures.REFLECTION_COLOR_T_DOWN),
    			0
    	);

    	// --------- REFLECTION DEPTH BUFFER ----------

    	// Bind the reder buffer that is going to be attached
    	gl.glBindRenderbuffer(GL_RENDERBUFFER, depthBufferName.get(DepthBuffer.REFLECTION_DEPTH_B_DOWN));
        gl.glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT, reflectionWidth, reflectionHeight);

        // Unbind the render buffer
        gl.glBindRenderbuffer(GL_RENDERBUFFER, 0);

    	// Attatch the depth buffer to the frame buffer
    	gl.glFramebufferRenderbuffer(
    			GL_FRAMEBUFFER,
    			GL_DEPTH_ATTACHMENT,
    			GL_RENDERBUFFER,
    			depthBufferName.get(DepthBuffer.REFLECTION_DEPTH_B_DOWN)
    	);

    	// --------------------------------------------

        // Make sure the frame buffer has been properly created
        if (gl.glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            System.out.println("The framebuffer could not be created");

        // ============================= INITIALISE REFRACTION FRAME BUFFER ===========================

    	// Bind the frame buffer
    	gl.glBindFramebuffer(GL_FRAMEBUFFER, frameBufferNames.get(FrameBuffers.REFRACTION_FB_DOWN));

    	// --------- REFRACTION COLOR TEXTURE ----------

    	// Bind the texture that is going to be attached
    	gl.glBindTexture(GL_TEXTURE_2D, textureNames.get(Textures.REFRACTION_COLOR_T_DOWN));

    	gl.glTexImage2D(
    			GL_TEXTURE_2D,
    			0,
    			GL_RGBA,
    			refractionWidth,
    			refractionHeight,
    			0,
    			GL_RGBA,
    			GL_UNSIGNED_BYTE,
    			null
    	);

    	gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    	gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

    	// Unbind the texture
    	gl.glBindTexture(GL_TEXTURE_2D, 0);

    	// Attatch the texture to the frame buffer
    	gl.glFramebufferTexture2D(
    			GL_FRAMEBUFFER,
    			GL_COLOR_ATTACHMENT0,
    			GL_TEXTURE_2D,
    			textureNames.get(Textures.REFRACTION_COLOR_T_DOWN),
    			0
    	);

    	// --------- REFRACTION DEPTH TEXTURE ----------

    	// Bind the texture that is going to be attached
    	gl.glBindTexture(GL_TEXTURE_2D, textureNames.get(Textures.REFRACTION_DEPTH_T_DOWN));

    	gl.glTexImage2D(
    			GL_TEXTURE_2D,
    			0,
    			GL_DEPTH_COMPONENT32,
    			refractionWidth,
    			refractionHeight,
    			0,
    			GL_DEPTH_COMPONENT,
    			GL_FLOAT,
    			null
    	);

    	gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    	gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

    	// Unbind the texture
    	gl.glBindTexture(GL_TEXTURE_2D, 0);

    	// Attatch the texture to the frame buffer
    	gl.glFramebufferTexture2D(
    			GL_FRAMEBUFFER,
    			GL_DEPTH_ATTACHMENT,
    			GL_TEXTURE_2D,
    			textureNames.get(Textures.REFRACTION_DEPTH_T_DOWN),
    			0
    	);

    	// --------------------------------------------

        // Make sure the frame buffer has been properly created
        if (gl.glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            System.out.println("The framebuffer could not be created");

        // ================================================================================

        // Unbind current Frame Buffer

        gl.glBindFramebuffer(GL_FRAMEBUFFER, 0);

    }

	public float[] getMiddle(float[] vec1, float[] vec2) {

			float[] out = new float[3];

			for(int i = 0; i<3; i++) {
				out[i] = vec2[i] - vec1[i];
			}

			out = VectorUtil.scaleVec3(new float[3], out, 0.5f);

			for(int i = 0; i<3; i++) {
				out[i] = out[i] + vec1[i];
			}

			return out;
		}

	public float[] subdivideMesh(float[] vertices, short[] elementData) {

		int numOfValues = 9; //number of values per vertex (3 coordinates, 3 texture, 3 normal)
		int numOfVertices = 3; //number of vertices per face
		int numOfFaces = elementData.length/numOfVertices;	//number of faces to subdivide

		List<Float> newVertices = new ArrayList<Float>();

		for(int i=0; i<numOfFaces; i++) {

			//get the indices of the vertices out of the element data
			int[] pos = {
					elementData[i*numOfVertices]*numOfValues,
					elementData[i*numOfVertices +1] * numOfValues,
					elementData[i*numOfVertices+2] * numOfValues
			};

			
			float[] x = new float[9];
			float[] y = new float[9];
			float[] z = new float[9];

			//using a 2d array to get the vertex data
			float[][] corners = new float[3][9];

			for(int j = 0; j<numOfVertices; j++) {
				for(int k = 0; k<numOfValues; k++) {
					corners[j][k] = vertices[pos[j] + k];
				}
			}

			//assign the vertex arrays to the corresponding array:
			x = corners[0];
			y = corners[1];
			z = corners[2];

			//get the coordinates of the vertices, needed to get the median of the edges:
			float[] x_coords = {x[0], x[1], x[2]};
			float[] y_coords = {y[0], y[1], y[2]};
			float[] z_coords = {z[0], z[1], z[2]};

			//find the medians of the face edges:
			float[] a_coords = getMiddle(x_coords, y_coords);
			float[] b_coords = getMiddle(y_coords, z_coords);
			float[] c_coords = getMiddle(z_coords, x_coords);


			float []normal_tmp = {0.0f, 1.0f, 0.0f};
			{
				float[] face = {
						x_coords[0], x_coords[1], x_coords[2], 0f, 0f, 1f, normal_tmp[0], normal_tmp[1], normal_tmp[2],
						a_coords[0], a_coords[1], a_coords[2], 0f, 0f, 1f, normal_tmp[0], normal_tmp[1], normal_tmp[2],
						c_coords[0], c_coords[1], c_coords[2], 0f, 0f, 1f, normal_tmp[0], normal_tmp[1], normal_tmp[2]
				};

				for(int j=0; j<face.length; j++) {
					newVertices.add(face[j]);
				}
			}

			{
				float[] face = {
						a_coords[0], a_coords[1], a_coords[2], 0f, 0f, 1f, normal_tmp[0], normal_tmp[1], normal_tmp[2],
						y_coords[0], y_coords[1], y_coords[2], 0f, 0f, 1f, normal_tmp[0], normal_tmp[1], normal_tmp[2],
						b_coords[0], b_coords[1], b_coords[2], 0f, 0f, 1f, normal_tmp[0], normal_tmp[1], normal_tmp[2]
				};

				for(int j=0; j<face.length; j++) {
					newVertices.add(face[j]);
				}
			}


			{
				float[] face = {
						a_coords[0], a_coords[1], a_coords[2], 0f, 0f, 1f, normal_tmp[0], normal_tmp[1], normal_tmp[2],
						b_coords[0], b_coords[1], b_coords[2], 0f, 0f, 1f, normal_tmp[0], normal_tmp[1], normal_tmp[2],
						c_coords[0], c_coords[1], c_coords[2], 0f, 0f, 1f, normal_tmp[0], normal_tmp[1], normal_tmp[2]
				};

				for(int j=0; j<face.length; j++) {
					newVertices.add(face[j]);
				}
			}

			{
				float[] face = {
						c_coords[0], c_coords[1], c_coords[2], 0f, 0f, 1f, normal_tmp[0], normal_tmp[1], normal_tmp[2],
						b_coords[0], b_coords[1], b_coords[2], 0f, 0f, 1f, normal_tmp[0], normal_tmp[1], normal_tmp[2],
						z_coords[0], z_coords[1], z_coords[2], 0f, 0f, 1f, normal_tmp[0], normal_tmp[1], normal_tmp[2]
				};

				for(int j=0; j<face.length; j++) {
					newVertices.add(face[j]);
				}
			}
		}

		//create fixed size array out of ArrayList:
		float[] outVertices = new float[newVertices.size()];

		for(int i = 0; i < newVertices.size(); i++) {
			outVertices[i] = newVertices.get(i);
		}
		return outVertices;
	}

	private short[] createMeshElementData(float[] vertexData) {
		short[] out = new short[vertexData.length / (3 + 3 + 3)];

		//since all the vertices are already in the right order
		//the element data just needs to count from 0 to the number of vertices:
		for(short i = 0; i<out.length; i++) {
			out[i] = i;
		}

		return out;
	}

	private float[] computeIntersection(float[] mouseRay) {
		//The vector is used to define a line from the camera position through the 3D-space.
		//This line is defined as following: L = cameraVec + lambda * mouseRay.
		//To find the intersection of this Line with the xz-Plane (where the water is), we just have to set the y-value of the line equal to zero and solve the equation to get lambda.
		//The solution for lambda is: lambda = -cameraVec[1]/mouseVec[1].

		float lambda = -cameraProperties[1] / mouseRay[1];

		float[] intersection = {cameraProperties[0] + lambda * mouseRay[0], cameraProperties[2] + lambda * mouseRay[2]};

		return intersection;
	}

	//function to add a drop at a clicked position:
	private void addDropAt(float x, float z) {
		System.out.println("DropX: " + x + "\tDropZ: " + z);
		new Thread(() -> {
			//System.out.println("key pressed!");
			int tmp = maxNumDrops;
			for (int i = 0; i < dropStartTime.length; i++) {
				if (dropStartTime[i] == 0f) {
					tmp = i;
					break;
				}
			}
			if (tmp != maxNumDrops) {
				numDrops++;
				dropStartTime[tmp] = System.currentTimeMillis();
				dropPositions[tmp][0] = x;
				dropPositions[tmp][1] = z;

				try {
					TimeUnit.SECONDS.sleep(7);
					numDrops--;
					dropStartTime[tmp] = 0;
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}

		}).start();
	}



    // Private class representing a vertex program
    private class Program {

        // The name of the program
        public int name = 0;

        // Constructor
        public Program(GL4 gl, String root, String vertex, String fragment) {

            // Instantiate a complete vertex shader
            ShaderCode vertShader = ShaderCode.create(gl, GL_VERTEX_SHADER, this.getClass(), root, null, vertex,
                    "vert", null, true);

            // Instantiate a complete fragment shader
            ShaderCode fragShader = ShaderCode.create(gl, GL_FRAGMENT_SHADER, this.getClass(), root, null, fragment,
                    "frag", null, true);

            // Create the shader program
            ShaderProgram shaderProgram = new ShaderProgram();

            // Add the vertex and fragment shader
            shaderProgram.add(vertShader);
            shaderProgram.add(fragShader);

            // Initialize the program
            shaderProgram.init(gl);

            // Store the program name (nonzero if valid)
            name = shaderProgram.program();

            // Compile and link the program
            shaderProgram.link(gl, System.err);
        }
    }

 // Private class to provide an semantic interface between Java and GLSL
 	private static class Semantic {

 		public interface Attr {
 			int POSITION = 0;
 			int COLOR = 1;
 			int NORMAL = 2;
 		}

 		public interface Uniform {
 			int TRANSFORM0 = 1;
 			int TRANSFORM1 = 2;
 			int LIGHT0 = 3;
 			int MATERIAL = 4;
 			int CAMERA = 5;
 			int CLIP_PLANE = 6;
			int NOISE_TIME = 7;
			int DROP_DATA = 8;
			int DROP_COUNT = 9;
 		}

 		public interface Stream {
 			int A = 0;
 		}
 	}
}
