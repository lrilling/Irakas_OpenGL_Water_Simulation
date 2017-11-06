package objParser;


public class Material {
	
	private String name;
	private float[] ambientColor;
	private float[] diffuseColor;
	private float[] specularColor;
	
	public Material(String n){
		name = n;
	}
	
	public void setAmbientColor(float[] color){
		ambientColor = color;
	}
	
	public void setDiffuseColor(float[] color){
		diffuseColor = color;
	}
	
	public void setSpecularColor(float[] color){
		specularColor = color;
	}
	
	public String getName(){
		return name;
	}

	public float[] getAmbientColor() {
		return ambientColor;
	}

	public float[] getDiffuseColor() {
		return diffuseColor;
	}

	public float[] getSpecularColor() {
		return specularColor;
	}
	
	public boolean equalsName(String matName){
		if (name.equals(matName))
			return true;
		else
			return false;
	}
	
}
