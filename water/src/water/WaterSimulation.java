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

import static com.jogamp.opengl.GL.*;
import static com.jogamp.opengl.GL2ES2.GL_DEBUG_SEVERITY_HIGH;
import static com.jogamp.opengl.GL2ES2.GL_DEBUG_SEVERITY_MEDIUM;
import static com.jogamp.opengl.GL2ES3.*;
import static com.jogamp.opengl.GL2ES3.GL_UNIFORM_BUFFER;
import static com.jogamp.opengl.GL4.GL_MAP_COHERENT_BIT;
import static com.jogamp.opengl.GL4.GL_MAP_PERSISTENT_BIT;

public class WaterSimulation implements GLEventListener, KeyListener {

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
    	
    	int GLOBAL_MATRICES = 4;
    	
    	int MODEL_MATRIX_SCENE = 5;
    	
    	int MODEL_MATRIX_WATER = 6;
    	
    	int LIGHT_PROPERTIES = 7;
    	int MATERIAL_PROPERTIES = 8;
    	int CAMERA_PROPERTIES = 9;
    	
    	int TIME = 10;
    	int NOISE_TIME = 11;
    	
    	int MAX = 12;
    }
    
    private interface VertexArray{
    	int SCENE = 0;
    	int WATER = 1;
    	int MAX = 2;
    }
    
    private float[] sceneVertexData;
    private short[] sceneElementData;
    
    // Vertex data (3 POSITION - 2 UV - 3 NORMAL)
    private float[] testVertexData = {
        // Front
        -1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f,
        -1.0f, -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f,
        1.0f, -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f,
        1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f,
        // Back
        1.0f, 1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.0f,
        1.0f, -1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.0f,
        -1.0f, -1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.0f,
        -1.0f, 1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.0f,
        // Left
        -1.0f, 1.0f, -1.0f, 1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f,
        -1.0f, -1.0f, -1.0f,1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f,
        -1.0f, -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f,
        -1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f,
        // Right
        1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f,
        1.0f, -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f,
        1.0f, -1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f,
        1.0f, 1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f,
        // Top
        -1.0f, 1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f,
        -1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f,
        1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f,
        1.0f, 1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f,
        // Bottom
        -1.0f, -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, -1.0f, 0.0f,
        -1.0f, -1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.0f, -1.0f, 0.0f,
        1.0f, -1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.0f, -1.0f, 0.0f,
        1.0f, -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, -1.0f, 0.0f
    };

    // Triangles
    private short[] testElementData = {
        // Front
        0, 1, 2, 2, 3, 0,
        // Back
        4, 5, 6, 6, 7, 4,
        // Left
        8, 9, 10, 10, 11, 8,
        // Right
        12, 13, 14, 14, 15, 12,
        // Top
        16, 17, 18, 18, 19, 16,
        // Bottom
        20, 21, 22, 22, 23, 20
    };
    
    private float[] planeVertexData = {
    	1f, 0f, 1f, 0f, 0f ,1f, 0f, 1f, 0f,
    	-1f, 0f, 1f, 0f, 0f ,1f, 0f, 1f, 0f,
    	-1f, 0f, -1f, 0f, 0f ,1f, 0f, 1f, 0f,
    	1f, 0f, -1f, 0f, 0f ,1f, 0f, 1f, 0f,
    };
    
    private short[] planeElementData = {
    		0, 1, 2,
    		2, 3, 0
    };
    
    // Create buffers for the names
 	private IntBuffer bufferNames = GLBuffers.newDirectIntBuffer(Buffer.MAX);
 	private IntBuffer vertexArrayName = GLBuffers.newDirectIntBuffer(VertexArray.MAX);
    
	// Create buffers for clear values
	private FloatBuffer clearColor = GLBuffers.newDirectFloatBuffer(new float[] { 0.008f, 0.616f, 0.825f, 0 });
	private FloatBuffer clearDepth = GLBuffers.newDirectFloatBuffer(new float[] { 1 });
	
	private ByteBuffer globalMatricesPointer, sceneModelMatrixPointer, waterModelMatrixPointer, timePointer, noiseTimePointer;
	
	 // Light properties (4 valued vectors due to std140 see OpenGL 4.5 reference)
    private float[] lightProperties = {
        // Position
        0f, -6f, 2f, 0f,
        // Ambient Color
        1f, 1f, 1f, 0f,
        // Diffuse Color
        1f, 1f, 1f, 0f,
        // Specular Color
        1f, 1f, 1f, 0f
    };

    private float[] materialProperties = {
        // Shininess
        100f
    };

    // Camera properties 
    private float[] cameraProperties = {
        0f, 2f, 12f
    };

    // Program instance reference
    private Program program, waterProgram;
    
    //Bug 1287:
    private boolean bug1287 = true;
    
    //Timer:
    private long start;
    private long noise_start;
    
    //Variables for controls:
    private Set<Short> pressed = new HashSet<Short>();
    private boolean shift = false;
    
    private float angleX = 0.0f;
    private float angleY = 0.0f;
    
    private boolean drop = false;

    // Application setup function
    private void setup() {

        // This appears to be a mistake; further investigation is required.
        //GLProfile glProfile = GLProfile.get(GLProfile.GL3);

        // Get a OpenGL 4.x profile (x >= 0)
        GLProfile glProfile = GLProfile.get(GLProfile.GL4);

        // Get a structure for definining the OpenGL capabilities with default values
        GLCapabilities glCapabilities = new GLCapabilities(glProfile);

        // Create the window with default capabilities
        window = GLWindow.create(glCapabilities);

        // Set the title of the window
        window.setTitle("waterSimulation JOGL");

        // Set the size of the window
        window.setSize(1920, 1080);

        // Set debug context (must be set before the window is set to visible)
        window.setContextCreationFlags(GLContext.CTX_OPTION_DEBUG);

        // Make the window visible
        window.setVisible(true);

        // Add OpenGL and keyboard event listeners
        window.addGLEventListener(this);
        window.addKeyListener(this);

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

        // Set up the program
        program = new Program(gl, "water", "waterSimulation", "waterSimulation");
        
        waterProgram = new Program(gl, "water", "water", "waterSimulation");

        // Enable Opengl depth buffer testing
        gl.glEnable(GL_DEPTH_TEST);
        
        //Store the start time of the application for the time diff:
        start = System.currentTimeMillis();
        noise_start = start;
    }

    // GLEventListener.display implementation
    @Override
    public void display(GLAutoDrawable drawable) {

        // Get OpenGL 4 reference
        GL4 gl = drawable.getGL().getGL4();
        
        //Copy the view matrix to the server
        {
        	float [] translateView = FloatUtil.makeTranslation(new float[16], false, -cameraProperties[0], -cameraProperties[1], -cameraProperties[2]);
        	
        	float[] rotationYView = FloatUtil.makeRotationAxis(new float[16], 0, angleY, 0f, 1f, 0f, new float[3]);
			float[] rotationXView = FloatUtil.makeRotationAxis(new float[16], 0, angleX, 1f, 0f, 0f, new float[3]);
			float[] view = FloatUtil.multMatrix(translateView, FloatUtil.multMatrix(rotationXView, rotationYView));
			
        	for(int i=0; i<16; i++)
        		globalMatricesPointer.putFloat(16*4 + i * 4, view[i]);
        }
        
        gl.glClearBufferfv(GL_COLOR, 0, clearColor);
        gl.glClearBufferfv(GL_DEPTH, 0, clearDepth);
        
        //Copy the model matrices to the server 
        {
        	long now = System.currentTimeMillis();
        	float diff = (float) (now-start) / 100;
        	float noiseDiff = (float)(now - noise_start) / 100;
        	
        	if(drop) {
        		timePointer.asFloatBuffer().put(diff);
        	}
        	else {
        		timePointer.asFloatBuffer().put(0);
        	}
        	
        	noiseTimePointer.asFloatBuffer().put(noiseDiff);
        	
        	float[] rotateZ = FloatUtil.makeRotationAxis(new float[16], 0, 3*FloatUtil.PI/2f, 0f, 1f, 0f, new float[3]);
        	
        	float[] model = FloatUtil.makeIdentity(new float[16]);
        	
        	sceneModelMatrixPointer.asFloatBuffer().put(rotateZ);
        	
        	float[] scale = FloatUtil.makeScale(new float[16], false, 6f, 6f, 6f);
        	float[] translate = FloatUtil.makeTranslation(new float[16], false, 0f, 1f, 0f);
        	waterModelMatrixPointer.asFloatBuffer().put(FloatUtil.multMatrix(translate, scale));
        }
        
        
        gl.glUseProgram(program.name);
        gl.glBindVertexArray(vertexArrayName.get(VertexArray.SCENE));
        
        //Bind the global matrices buffer:
        gl.glBindBufferBase(GL_UNIFORM_BUFFER, Semantic.Uniform.TRANSFORM0, bufferNames.get(Buffer.GLOBAL_MATRICES));
        
        //Bind the model matrices buffer:
        gl.glBindBufferBase(GL_UNIFORM_BUFFER, Semantic.Uniform.TRANSFORM1, bufferNames.get(Buffer.MODEL_MATRIX_SCENE));
        
        // Bind the ligh properties buffer to a specified uniform index
        gl.glBindBufferBase(
                GL_UNIFORM_BUFFER,
                Semantic.Uniform.LIGHT0,
                bufferNames.get(Buffer.LIGHT_PROPERTIES));

        // Bind the ligh properties buffer to a specified uniform index
        gl.glBindBufferBase(
                GL_UNIFORM_BUFFER,
                Semantic.Uniform.MATERIAL,
                bufferNames.get(Buffer.MATERIAL_PROPERTIES));

        // Bind the ligh properties buffer to a specified uniform index
        gl.glBindBufferBase(
                GL_UNIFORM_BUFFER,
                Semantic.Uniform.CAMERA,
                bufferNames.get(Buffer.CAMERA_PROPERTIES));
        
        //Draw the triangle
        gl.glDrawElements(GL_TRIANGLES, sceneElementData.length, GL_UNSIGNED_SHORT, 0);
        
        gl.glUseProgram(waterProgram.name);
        
        gl.glBindBufferBase(GL_UNIFORM_BUFFER, Semantic.Uniform.TRANSFORM1, bufferNames.get(Buffer.MODEL_MATRIX_WATER));
        
        gl.glBindBufferBase(GL_UNIFORM_BUFFER, Semantic.Uniform.TIME, bufferNames.get(Buffer.TIME));
        gl.glBindBufferBase(GL_UNIFORM_BUFFER, Semantic.Uniform.NOISE_TIME, bufferNames.get(Buffer.NOISE_TIME));
        
        gl.glBindVertexArray(vertexArrayName.get(VertexArray.WATER));
        
        gl.glDrawElements(GL_TRIANGLES, planeElementData.length, GL_UNSIGNED_SHORT, 0);
        
        
        gl.glUseProgram(0);
        gl.glBindVertexArray(0);
    }

    // GLEventListener.reshape implementation
    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {

        // Get OpenGL 4 reference
        GL4 gl = drawable.getGL().getGL4();
        
        
        float[] frustum = FloatUtil.makeFrustum(new float[16], 0, false, -1.6f, 1.6f, -0.9f, 0.9f, 1f, 100f);
        
        globalMatricesPointer.asFloatBuffer().put(frustum);
        
        gl.glViewport(x, y, width, height);
    }

    // GLEventListener.dispose implementation
    @Override
    public void dispose(GLAutoDrawable drawable) {

        // Get OpenGL 4 reference
        GL4 gl = drawable.getGL().getGL4();
        
        // Unmap the transformation matrices
        gl.glUnmapNamedBuffer(bufferNames.get(Buffer.GLOBAL_MATRICES));
        gl.glUnmapNamedBuffer(bufferNames.get(Buffer.MODEL_MATRIX_SCENE));


        // Delete the program
        gl.glDeleteProgram(program.name);
        
        //Delete the vertex array
        gl.glDeleteVertexArrays(VertexArray.MAX, vertexArrayName);
        
        //Delete the buffers
        gl.glDeleteBuffers(Buffer.MAX, bufferNames);

    }

	// KeyListener.keyPressed implementation
	//		Arrow-key pressed: rotation
	//		Shift + Arrow-key pressed: translation
	@Override
	public void keyPressed(KeyEvent e) {
		//add the pressed key to the set
		pressed.add(e.getKeyCode());
		
		if(e.getKeyCode() == KeyEvent.VK_SHIFT) {
			shift = true;
		}
		if(pressed.size() > 1) {
			Iterator<Short> it = pressed.iterator();
			short tmp;
			while(it.hasNext()) {
				tmp = it.next();
				if(shift) {
					//change the camera properties if, shift and an arrow key are pressed (effects the global matrix):
					if(tmp == KeyEvent.VK_UP) {
						cameraProperties[2] = cameraProperties[2] - 0.5f;
					}
					else if(tmp == KeyEvent.VK_DOWN) {
						cameraProperties[2] = cameraProperties[2] + 0.5f;
					}
					else if(tmp == KeyEvent.VK_RIGHT) {
						cameraProperties[0] = cameraProperties[0] + 0.5f;
					}
					else if(tmp == KeyEvent.VK_LEFT) {
						cameraProperties[0] = cameraProperties[0] - 0.5f;
					}
				}
				
			}
		}
		else {
			// Destroy the window if the escape key is pressed
			if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
				new Thread(() -> {
					window.destroy();
				}).start();
			}
			//change the rotation of the global matrix if an arrow key is pressed without shift
			if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
				new Thread(() -> {
					angleY += 0.08f;
				}).start();
			}
			if (e.getKeyCode() == KeyEvent.VK_LEFT) {
				new Thread(() -> {
					angleY -= 0.08f;
				}).start();
			}
			if (e.getKeyCode() == KeyEvent.VK_UP) {
				new Thread(() -> {
					angleX -= 0.08f;
				}).start();
			}
			if (e.getKeyCode() == KeyEvent.VK_DOWN) {
				new Thread(() -> {
					angleX += 0.08f;
				}).start();
			}
			if (e.getKeyCode() == KeyEvent.VK_D) {
				new Thread(() -> {
					start = System.currentTimeMillis();
					drop = true;
					try {
						TimeUnit.SECONDS.sleep(5);
						drop = false;
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					
				}).start();
			}
		}
		
	}

	// KeyListener.keyPressed implementation
	@Override
	public void keyReleased(KeyEvent e) {
		pressed.remove(e.getKeyCode());
		if(e.getKeyCode() == KeyEvent.VK_SHIFT) {
			shift = false;
		}
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
			ObjModelParser parser = new ObjModelParser("models/tree3");
			
			ParserModel sceneModel = new ParserModel();
			sceneModel = parser.parseModel();
			
			sceneVertexData = sceneModel.getVertexData();
			sceneElementData = sceneModel.getElementData();
			
			System.out.println("VertexData: " + Arrays.toString(sceneVertexData));
			System.out.println("Siz: " + sceneVertexData.length);
			
			System.out.println("ElementData: " + Arrays.toString(sceneElementData));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	catch(IOException e) {
    		e.printStackTrace();
    	}
    	
    }
    
    public void initBuffers(GL4 gl) {
    	int detail = 5;
    	
    	for(int i = 0; i<detail; i++) {
    		planeVertexData = subdivideMesh(planeVertexData, planeElementData);
        	planeElementData = createMeshElementData(planeVertexData);
    	}
    	
    	
    	System.out.println("VertexDataWater: " + Arrays.toString(planeVertexData));
    	System.out.println("ElementDataWater: " + Arrays.toString(planeElementData));
    	
    	FloatBuffer sceneVertexBuffer = GLBuffers.newDirectFloatBuffer(sceneVertexData);
    	ShortBuffer sceneElementBuffer = GLBuffers.newDirectShortBuffer(sceneElementData);
    	
    	FloatBuffer waterVertexBuffer = GLBuffers.newDirectFloatBuffer(planeVertexData);
    	ShortBuffer waterElementBuffer = GLBuffers.newDirectShortBuffer(planeElementData);
    	
    	 // Create a direct buffer for the light properties
        FloatBuffer lightBuffer = GLBuffers.newDirectFloatBuffer(lightProperties);

        // Create a direct buffer for the material properties
        FloatBuffer materialBuffer = GLBuffers.newDirectFloatBuffer(materialProperties);

        // Create a direct buffer for the light properties
        FloatBuffer cameraBuffer = GLBuffers.newDirectFloatBuffer(cameraProperties);
        
    	
    	gl.glCreateBuffers(Buffer.MAX, bufferNames);
    	
    	if(!bug1287) {
    		//Buffer storage for vertex data:
    		gl.glNamedBufferStorage(bufferNames.get(Buffer.SCENE_V), sceneVertexBuffer.capacity() * Float.BYTES, sceneVertexBuffer, GL_STATIC_DRAW);
    		gl.glNamedBufferStorage(bufferNames.get(Buffer.WATER_V), waterVertexBuffer.capacity() * Float.BYTES, waterVertexBuffer, GL_STATIC_DRAW);
    		
    		//Buffer storage for element data:
    		gl.glNamedBufferStorage(bufferNames.get(Buffer.SCENE_E), sceneElementBuffer.capacity() * Short.BYTES, sceneElementBuffer, GL_STATIC_DRAW);
    		gl.glNamedBufferStorage(bufferNames.get(Buffer.WATER_E), waterElementBuffer.capacity()*Short.BYTES, waterElementBuffer, GL_STATIC_DRAW);
    		
    		//Buffer for global Matrix:
    		gl.glNamedBufferStorage(bufferNames.get(Buffer.GLOBAL_MATRICES), 16*4*2, null, GL_MAP_WRITE_BIT);
    		
    		//Buffer for model Matrix:
    		gl.glNamedBufferStorage(bufferNames.get(Buffer.MODEL_MATRIX_SCENE), 16*4, null, GL_MAP_WRITE_BIT);
    		
    		gl.glNamedBufferStorage(bufferNames.get(Buffer.MODEL_MATRIX_WATER), 16*4, null, GL_MAP_WRITE_BIT);
    		
    		// Create and initialize a named buffer storage for the light properties
            gl.glNamedBufferStorage(bufferNames.get(Buffer.LIGHT_PROPERTIES), 16 * Float.BYTES, lightBuffer, 0);

            // Create and initialize a named buffer storage for the material properties
            gl.glNamedBufferStorage(bufferNames.get(Buffer.MATERIAL_PROPERTIES), 1 * Float.BYTES, materialBuffer, 0);

            // Create and initialize a named buffer storage for the camera properties
            gl.glNamedBufferStorage(bufferNames.get(Buffer.CAMERA_PROPERTIES), 3 * Float.BYTES, cameraBuffer, 0);
            
            //Create and initialize a named buffer storage for the time property
            gl.glNamedBufferStorage(bufferNames.get(Buffer.TIME), 1 * Float.BYTES, null, GL_MAP_WRITE_BIT);
            gl.glNamedBufferStorage(bufferNames.get(Buffer.NOISE_TIME), 1 * Float.BYTES, null, GL_MAP_WRITE_BIT);

    	}
    	
    	else {
    		//Buffer for Vertex Data:
    		 gl.glBindBuffer(GL_ARRAY_BUFFER, bufferNames.get(Buffer.SCENE_V));
             gl.glBufferStorage(GL_ARRAY_BUFFER, sceneVertexBuffer.capacity() * Float.BYTES, sceneVertexBuffer, 0);
             gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
             
             gl.glBindBuffer(GL_ARRAY_BUFFER, bufferNames.get(Buffer.WATER_V));
             gl.glBufferStorage(GL_ARRAY_BUFFER, waterVertexBuffer.capacity() * Float.BYTES, waterVertexBuffer, 0);
             gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
             
             //Buffer for Element Data:
             gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufferNames.get(Buffer.SCENE_E));
             gl.glBufferStorage(GL_ELEMENT_ARRAY_BUFFER, sceneElementBuffer.capacity() * Short.BYTES, sceneElementBuffer, 0);
             gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
             
             gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufferNames.get(Buffer.WATER_E));
             gl.glBufferStorage(GL_ELEMENT_ARRAY_BUFFER, waterElementBuffer.capacity() * Short.BYTES, waterElementBuffer, 0);
             gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
             
             //Retrieve the uniform buffer offset alignment minimum
             IntBuffer uniformBufferOffset = GLBuffers.newDirectIntBuffer(1);
             gl.glGetIntegerv(GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT, uniformBufferOffset);
             
             //Set the required bytes for the matrices in accordance to the uniform buffer offset alignment:
             int globalBlockSize = Math.max(16*4*2, uniformBufferOffset.get(0));
             int modelBlockSize = Math.max(16*4, uniformBufferOffset.get(0));
             int lightBlockSize = Math.max(12 * Float.BYTES, uniformBufferOffset.get(0));
             int materialBlockSize = Math.max(3 * Float.BYTES, uniformBufferOffset.get(0));
             int cameraBlockSize = Math.max(3 * Float.BYTES, uniformBufferOffset.get(0));
             
             
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
             
          // Create and initialize a named buffer storage for the light properties
             gl.glBindBuffer(GL_UNIFORM_BUFFER, bufferNames.get(Buffer.LIGHT_PROPERTIES));
             gl.glBufferStorage(GL_UNIFORM_BUFFER, lightBlockSize, lightBuffer, 0);
             gl.glBindBuffer(GL_UNIFORM_BUFFER, 0);

             // Create and initialize a named buffer storage for the camera properties
             gl.glBindBuffer(GL_UNIFORM_BUFFER, bufferNames.get(Buffer.MATERIAL_PROPERTIES));
             gl.glBufferStorage(GL_UNIFORM_BUFFER, materialBlockSize, materialBuffer, 0);
             gl.glBindBuffer(GL_UNIFORM_BUFFER, 0);

             // Create and initialize a named buffer storage for the camera properties
             gl.glBindBuffer(GL_UNIFORM_BUFFER, bufferNames.get(Buffer.CAMERA_PROPERTIES));
             gl.glBufferStorage(GL_UNIFORM_BUFFER, cameraBlockSize, cameraBuffer, 0);
             gl.glBindBuffer(GL_UNIFORM_BUFFER, 0);
             
             //Create and initialize a named buffer storage for the time property
             gl.glBindBuffer(GL_UNIFORM_BUFFER, bufferNames.get(Buffer.TIME));
             gl.glBufferStorage(GL_UNIFORM_BUFFER, modelBlockSize, null, GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT);
             gl.glBindBuffer(GL_UNIFORM_BUFFER, 0);
             
             gl.glBindBuffer(GL_UNIFORM_BUFFER, bufferNames.get(Buffer.NOISE_TIME));
             gl.glBufferStorage(GL_UNIFORM_BUFFER, modelBlockSize, null, GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT);
             gl.glBindBuffer(GL_UNIFORM_BUFFER, 0);
    	}
             globalMatricesPointer = gl.glMapNamedBufferRange(
            		 bufferNames.get(Buffer.GLOBAL_MATRICES),
            		 0,
            		 16*4*2,
            		 GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT | GL_MAP_INVALIDATE_BUFFER_BIT);
             
             sceneModelMatrixPointer = gl.glMapNamedBufferRange(
                     bufferNames.get(Buffer.MODEL_MATRIX_SCENE),
                     0,
                     16 * 4,
                     GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT | GL_MAP_INVALIDATE_BUFFER_BIT);
             
             waterModelMatrixPointer = gl.glMapNamedBufferRange(
            		 bufferNames.get(Buffer.MODEL_MATRIX_WATER),
            		 0,
            		 16*4,
            		 GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT | GL_MAP_INVALIDATE_BUFFER_BIT);
             
             timePointer = gl.glMapNamedBufferRange(
            		 bufferNames.get(Buffer.TIME), 
            		 0, 
            		 1*Float.BYTES, 
            		 GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT | GL_MAP_INVALIDATE_BUFFER_BIT);
             
             noiseTimePointer = gl.glMapNamedBufferRange(
            		 bufferNames.get(Buffer.NOISE_TIME), 
            		 0, 
            		 1*Float.BYTES, 
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
    	
    	 // Set the format of the vertex attributes in the vertex array object
        gl.glVertexArrayAttribFormat(vertexArrayName.get(VertexArray.SCENE), Semantic.Attr.POSITION, 3, GL_FLOAT, false, 0);
        gl.glVertexArrayAttribFormat(vertexArrayName.get(VertexArray.SCENE), Semantic.Attr.COLOR, 3, GL_FLOAT, false, 3 * 4);
        gl.glVertexArrayAttribFormat(vertexArrayName.get(VertexArray.SCENE), Semantic.Attr.NORMAL, 3, GL_FLOAT, false, 6 * 4);
        
        gl.glVertexArrayAttribFormat(vertexArrayName.get(VertexArray.WATER), Semantic.Attr.POSITION, 3, GL_FLOAT, false, 0);
        gl.glVertexArrayAttribFormat(vertexArrayName.get(VertexArray.WATER), Semantic.Attr.COLOR, 3, GL_FLOAT, false, 3 * 4);
        gl.glVertexArrayAttribFormat(vertexArrayName.get(VertexArray.WATER), Semantic.Attr.NORMAL, 3, GL_FLOAT, false, 6 * 4);

        // Enable the vertex attributes in the vertex object
        gl.glEnableVertexArrayAttrib(vertexArrayName.get(VertexArray.SCENE), Semantic.Attr.POSITION);
        gl.glEnableVertexArrayAttrib(vertexArrayName.get(VertexArray.SCENE), Semantic.Attr.COLOR);
        gl.glEnableVertexArrayAttrib(vertexArrayName.get(VertexArray.SCENE), Semantic.Attr.NORMAL);

        gl.glEnableVertexArrayAttrib(vertexArrayName.get(VertexArray.WATER), Semantic.Attr.POSITION);
        gl.glEnableVertexArrayAttrib(vertexArrayName.get(VertexArray.WATER), Semantic.Attr.COLOR);
        gl.glEnableVertexArrayAttrib(vertexArrayName.get(VertexArray.WATER), Semantic.Attr.NORMAL);

        // Bind the triangle indices in the vertex array object the triangle indices buffer
        gl.glVertexArrayElementBuffer(vertexArrayName.get(VertexArray.SCENE), bufferNames.get(Buffer.SCENE_E));

        gl.glVertexArrayElementBuffer(vertexArrayName.get(VertexArray.WATER), bufferNames.get(Buffer.WATER_E));

        // Bind the vertex array object to the vertex buffer
        gl.glVertexArrayVertexBuffer(vertexArrayName.get(VertexArray.SCENE), Semantic.Stream.A, bufferNames.get(Buffer.SCENE_V), 0, (3 + 3 + 3) * 4);
        
        gl.glVertexArrayVertexBuffer(vertexArrayName.get(VertexArray.WATER), Semantic.Stream.A, bufferNames.get(Buffer.WATER_V), 0, (3 + 3 + 3) * 4);

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
		//float[] newVertices = new float[vertices.length*4];
		
		int numOfValues = 9; //number of values per vertex (3 coordinates, 3 texture, 3 normal)
		int numOfVertices = 3; //number of vertices per face before subdivision
		int numOfFaces = elementData.length/numOfVertices;	//number of faces on the subdivided icosahedron
		
		System.out.println("numOfFaces: " + numOfFaces);
		List<Float> newVertices = new ArrayList<Float>();
		
		for(int i=0; i<numOfFaces; i++) {
			
			int[] pos = {
					elementData[i*numOfVertices]*numOfValues,
					elementData[i*numOfVertices +1] * numOfValues,
					elementData[i*numOfVertices+2] * numOfValues
			};
			
			float[] x = new float[9];
			float[] y = new float[9];
			float[] z = new float[9];
			
			float[][] corners = new float[3][9];
			
			for(int j = 0; j<numOfVertices; j++) {
				for(int k = 0; k<numOfValues; k++) {
					corners[j][k] = vertices[pos[j] + k];
				}
			}
			
			x = corners[0];
			y = corners[1];
			z = corners[2];
			
			float[] x_coords = {x[0], x[1], x[2]};
			float[] y_coords = {y[0], y[1], y[2]};
			float[] z_coords = {z[0], z[1], z[2]};
			
			float[] a_coords = getMiddle(x_coords, y_coords);
			float[] b_coords = getMiddle(y_coords, z_coords);
			float[] c_coords = getMiddle(z_coords, x_coords);
			
			
			float[] normal_tmp = computeNormal(x_coords, a_coords, c_coords);
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
			
			normal_tmp = computeNormal(a_coords, y_coords, b_coords);
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
			
			normal_tmp = computeNormal(a_coords, b_coords, c_coords);
			
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
			
			normal_tmp = computeNormal(c_coords, b_coords, z_coords);
			
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
		
		float[] outVertices = new float[newVertices.size()];
		
		for(int i = 0; i < newVertices.size(); i++) {
			outVertices[i] = newVertices.get(i);
		}
		
		return outVertices;
	}
	
	private short[] createMeshElementData(float[] vertexData) {
		short[] out = new short[vertexData.length / (3 + 3 + 3)];
		
		//since all the vertices are already in the right order the element data just needs to count from 0 to the number of vertices:
		for(short i = 0; i<out.length; i++) {
			out[i] = i;
		}
		
		return out;
	}
	
	//function to compute the normal of a face defined by 3 vertices:
		private float[] computeNormal(float[] x, float[] y, float[] z) {
			float[] y_x = {
					y[0] - x[0],
					y[1] - x[1],
					y[2] - y[2]
			};
			
			float[] z_x = {
					z[0] - x[0],
					z[1] - x[1],
					z[2] - x[2]
			};
			
			float[] n;
			n = VectorUtil.normalizeVec3(VectorUtil.crossVec3(new float[3], y_x, z_x));
			return n;
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
 			int TIME = 3;
 			int NOISE_TIME = 4;
 			int LIGHT0 = 5;
 			int MATERIAL = 6;
 			int CAMERA = 7;
 		}

 		public interface Stream {
 			int A = 0;
 		}
 	}
}
