package objParser;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

// .obj model parser
// The result must be:

//  private float[] vertexData = {
//       x, y, z, colorR, colorG, colorB, normalX, normalY, normalZ, --> Makes one vertex
//   };

//   private short[] elementData = {
//      vertex1, vertex2, vertex3
//   };

public class ObjModelParser {

	private String objFileName;
	private String mtlFileName;
	
	private ArrayList<float[]> vertices;
	private String currentMaterialName;
	private ArrayList<float[]> normals;
//	private int objModelNum;
	private ArrayList<Material> materialList;
	private int materialNum;	
	
	private ArrayList<Float> parsedVertices;
	private short parsedVerticesNum;
	private ArrayList<Short> parsedElements;
	
	
	public ObjModelParser(String fileName) throws FileNotFoundException{
		objFileName = fileName + ".obj";
		mtlFileName = fileName + ".mtl";
//		objModelNum = 0;
		currentMaterialName = "";
		vertices = new ArrayList<float[]>();
		normals = new ArrayList<float[]>();
		materialNum = 0;
		materialList = new ArrayList<Material>();
		parsedVerticesNum = 0;
		parsedVertices = new ArrayList<Float>();
		parsedElements = new ArrayList<Short>();
	}
	
	public ParserModel parseModel() throws IOException{
		
		ParserModel model = new ParserModel();
				
		readMtlFile();
	    readObjFile();
	    
	    float[] parsedVertexData = new float[parsedVertices.size()];
	    
	    for (int i = 0; i < parsedVertices.size(); i++){
	    	parsedVertexData[i] = parsedVertices.get(i);
	    }
	    
	    model.setVertexData(parsedVertexData);
	    
	    short[] parsedElementData = new short[parsedElements.size()];
	    
	    for (int i = 0; i < parsedElements.size(); i++){
	    	parsedElementData[i] = parsedElements.get(i);
	    }
	    
	    model.setElementData(parsedElementData);
	    
	    return model;
	    
	}
	
	private void readMtlFile() throws IOException{
		FileReader mtlFileReader = new FileReader(mtlFileName);
		BufferedReader mtlReader = new BufferedReader(mtlFileReader);
		String line = null;
	    
		while ((line = mtlReader.readLine()) != null) {
		    if (line.length() > 0) {
		    	char c = line.charAt(0);
			    switch (c) {
			    	case '#':
			    		break;
			            
			        case 'n':
			        	addNewMaterial(line);
			        	break;
			        	
			        case 'K':
			            if (line.startsWith("Ka"))
			            	addAmbientColorToMaterial(line);
			            else if (line.startsWith("Kd")) 
			            	addDiffuseColorToMaterial(line);
			            else if (line.startsWith("Ks")) 
			            	addSpecularColorToMaterial(line);
			            break;
			            
			        default:
			            // For now we ignore all other lines
		        }
	      	}
	    }
		
		mtlReader.close();
		mtlFileReader.close();
	}
	
	private void readObjFile() throws IOException{
		FileReader objFileReader = new FileReader(objFileName);
		BufferedReader objReader = new BufferedReader(objFileReader);
		String line = null;
	    int lineNo = 0;
	    
	    while ((line = objReader.readLine()) != null) {
	      lineNo++;
		     if (line.length() > 0) {
		    	char c = line.charAt(0);
			      switch (c) {
			        case 'v':
			            if (Character.isWhitespace(line.charAt(1)))
			            	addVertex(line);
			            else if (line.startsWith("vn")) 
			            	addNormal(line);
			            else
			            	throw new IOException("Unsupported vertex command on line" + lineNo);
			            break;
			            
			        case 'u':
			        	addCurrentMaterialName(line);
			        	break;
			            
			        case 'f':
			            parseVertexAndElement(line);
			            break;
			            
			        default:
			            // For now we ignore all other lines
		        }
	      	}
	    }
	    
	    objReader.close();
	    objFileReader.close();
	}
	
	private void addNewMaterial(String line){
		String[] parsedLine = line.split(" ");
		String matName = parsedLine[1];
		Material mat = new Material(matName);
		materialList.add(mat);
		materialNum++;
	}
	
	private void addAmbientColorToMaterial(String line){
		float[] color = parseLineToFloats(line);
		materialList.get(materialNum-1).setAmbientColor(color);
	}
	
	private void addDiffuseColorToMaterial(String line){
		float[] color = parseLineToFloats(line);
		materialList.get(materialList.size()-1).setDiffuseColor(color);
	}
	
	private void addSpecularColorToMaterial(String line){
		float[] color = parseLineToFloats(line);
		materialList.get(materialNum-1).setSpecularColor(color);
	}
	
	private void addVertex(String line){
		float[] vertex = parseLineToFloats(line);
		vertices.add(vertex);
	}
	
	private void addNormal(String line){
		float[] normal = parseLineToFloats(line);
		normals.add(normal);
	}
	
	private void addCurrentMaterialName(String line){
		String[] parsedLine = line.split(" ");
		currentMaterialName = parsedLine[1];
	}
	
	private void parseVertexAndElement(String line){
		
		int matPos = getMaterialIndex(currentMaterialName);
		float[] diffuseColor = materialList.get(matPos).getDiffuseColor();
		
		String[] splitedLine = line.split(" ");
		
		for(int i = 1; i < splitedLine.length; i++){

			String[] splitedData = splitedLine[i].split("/"); // 0: vertexPos , 1: texturePos, 2: normalPos

			int vertexPos = Integer.parseInt(splitedData[0]);	
			float[] vertex = vertices.get(vertexPos - 1);
			
			int normalPos = Integer.parseInt(splitedData[2]);
			float[] normal = normals.get(normalPos - 1);
			
			parsedVertices.add(vertex[0]);
			parsedVertices.add(vertex[1]);
			parsedVertices.add(vertex[2]);
			parsedVertices.add(diffuseColor[0]);
			parsedVertices.add(diffuseColor[1]);
			parsedVertices.add(diffuseColor[2]);
			parsedVertices.add(normal[0]);
			parsedVertices.add(normal[1]);
			parsedVertices.add(normal[2]);
			
			parsedElements.add(parsedVerticesNum);
			
			parsedVerticesNum++;
		}
		
	}
	
	private float[] parseLineToFloats(String line){	
		String[] splitedLine = line.split(" ");
		
		float[] floats = new float[3];
		
		floats[0] = Float.parseFloat(splitedLine[1]);
		floats[1] = Float.parseFloat(splitedLine[2]);
		floats[2] = Float.parseFloat(splitedLine[3]);
		
		return floats;
	}
	
	private int getMaterialIndex(String name){

		int i = 0;
		int pos = -1;
		while (i < materialList.size() && pos==-1){
			if (materialList.get(i).equalsName(name))
				pos = i;
			i++;
		}
		
		return pos;
	}
	
	
}
