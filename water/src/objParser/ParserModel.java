package objParser;


public class ParserModel {
	
	private float[] vertexData;
	private short[] elementData;
	
	public ParserModel(){}

	public float[] getVertexData() {
		return vertexData;
	}

	public void setVertexData(float[] vertexData) {
		this.vertexData = vertexData;
	}

	public short[] getElementData() {
		return elementData;
	}

	public void setElementData(short[] elementData) {
		this.elementData = elementData;
	};

}
