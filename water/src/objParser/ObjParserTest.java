package objParser;

import java.io.IOException;

public class ObjParserTest {

	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub

		ObjModelParser parser = new ObjModelParser("models/tree3");
		
		ParserModel myModel = new ParserModel();
		
		myModel = parser.parseModel();
		
		float[] vertexData = myModel.getVertexData();
		short[] elementData = myModel.getElementData();
		
		System.out.println("-------------------------");
		System.out.println("The Vertex data: ");
		int j = 1;
		for (int i = 0; i < vertexData.length/4; i++){
			if (j % 9 == 0)
				System.out.println(vertexData[i]);
			else
				System.out.print(vertexData[i]  + " ");
			j++;
		}
		
//		System.out.println("-------------------------");
//		System.out.println("The Vertex data: ");
//		j = 1;
//		for (int i = 0; i < elementData.length; i++){
//			if (j % 3 == 0)
//				System.out.println(elementData[i]);
//			else
//				System.out.print(elementData[i]  + " ");
//			j++;
//		}
	}

}
