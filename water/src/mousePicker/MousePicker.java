/*
 * MousePicker based on this video tutorial: https://www.youtube.com/watch?v=DLKN0jExRIM
 * as well as on this paper: http://antongerdelan.net/opengl/raycasting.html
*/
package mousePicker;

import com.jogamp.opengl.math.FloatUtil;

public class MousePicker {
	private float[] projectionMatrix;
	private float[] viewMatrix;
	
	private int windowWidth;
	private int windowHeight;
	
	public MousePicker(float[] projectionMatrix, float[] viewMatrix, int windowX, int windowY) {
		this.projectionMatrix = projectionMatrix;
		this.viewMatrix = viewMatrix;
		
		this.windowWidth = windowX;
		this.windowHeight = windowY;
		
		System.out.println("MousePicker created!");
	}
	
	public void updateView(float[] viewMatrix) {
		this.viewMatrix = viewMatrix;
	}
	
	public void updateProjection(float[] projectionMatrix) {
		this.projectionMatrix = projectionMatrix;
	}
	
	public void updateWindow(int x, int y) {
		this.windowWidth = x;
		this.windowHeight = y;
	}
	
	public float[] getMouseRay(int mouseX, int mouseY) {
		System.out.println("MousePicker accessed!");
		return calculateMouseRay(mouseX, mouseY);
	}
	
	private float[] getNormalizedDeviceCoords(int mouseX, int mouseY) {
		float x = (2f*mouseX) / windowWidth - 1;
		float y = (2f*mouseY) / windowHeight - 1;
		
		float[] out = {x, y};
		return out;
	}
	
	private float[] toEyeCoords(float[] clipCoords) {
		float[] invertedProjection = FloatUtil.invertMatrix(projectionMatrix, new float[16]);
		
		float[] eyeCoords = FloatUtil.multMatrixVec(invertedProjection, clipCoords, new float[4]);
		
		float[] output = {eyeCoords[0], eyeCoords[1], -1f, 0f};
		return output;
	}
	
	private float[] toWorldCoords(float[] eyeCoords) {
		float[] invertedView = FloatUtil.invertMatrix(viewMatrix, new float[16]);
		float[] rayWorld = FloatUtil.multMatrixVec(invertedView, eyeCoords, new float[4]);
		
		float[] mouseRay = {rayWorld[0], rayWorld[1], rayWorld[2]};
		
		float mouseRayLength = FloatUtil.sqrt(mouseRay[0]*mouseRay[0] + mouseRay[1]*mouseRay[1] + mouseRay[2]*mouseRay[2]);
		
		float[] normalizedMouseRay = {mouseRay[0] / mouseRayLength, mouseRay[1] / mouseRayLength, mouseRay[2] / mouseRayLength};
		
		return normalizedMouseRay;
	}
	
	private float[] calculateMouseRay(int mouseX, int mouseY) {
		//2D vector with normalized coords, used by OpenGL 
		float[] normalizedDeviceCoords = getNormalizedDeviceCoords(mouseX, mouseY);
		
		//Creating a 4D vector out of the normalizedDeviceCoords, defining z as -1 (clipping Plane):
		float[] clipCoords = {normalizedDeviceCoords[0], normalizedDeviceCoords[1], -1f, 1f};
		
		//4D vector containing the inverted projection
		float[] eyeCoords = toEyeCoords(clipCoords);
		
		//2D vector containing the world coords of our mouse ray
		float[] worldCoords = toWorldCoords(eyeCoords);
		
		return worldCoords;
	}
}
