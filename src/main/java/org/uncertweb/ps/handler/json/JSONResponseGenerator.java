package org.uncertweb.ps.handler.json;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;

import org.uncertweb.ps.data.DataReference;
import org.uncertweb.ps.data.RequestedOutput;
import org.uncertweb.ps.data.Response;
import org.uncertweb.ps.encoding.EncodeException;
import org.uncertweb.ps.handler.ResponseGenerateException;
import org.uncertweb.ps.handler.data.DataReferenceGenerator;
import org.uncertweb.ps.handler.json.gson.GsonWrapper;
import org.uncertweb.ps.storage.StorageException;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class JSONResponseGenerator {
	
	public static void generate(Response response, OutputStream outputStream) throws ResponseGenerateException {
		generate(response, null, outputStream);
	}

	public static void generate(Response response, List<RequestedOutput> reqOutputs, OutputStream outputStream) throws ResponseGenerateException {
		// build using gson
		Gson gson = GsonWrapper.getGson();
		JsonObject responseObject = gson.toJsonTree(response).getAsJsonObject();
		JsonObject innerObject = responseObject.get(response.getProcessIdentifier() + "Response").getAsJsonObject();

		// check requested outputs
//		List<RequestedOutput> reqOutputs = request.getRequestedOutputs();
//		if (reqOutputs.size() > 0) {
//			// horrible inefficient
//			for (Output output : response.getOutputs()) {
//				String outputIdentifier = output.getIdentifier();
//				boolean include = false;
//				for (RequestedOutput reqOutput : reqOutputs) {
//					if (reqOutput.getName().equals(output.getIdentifier())) {
//						// set include flag
//						include = true;
//
//						// check for ref
//						if (reqOutput.isReference()) {
//							// get config
//							Config config = Config.getInstance();
//							String basePath = config.getServerProperty("basePath");
//							String baseURL = config.getServerProperty("baseURL");
//
//							JsonElement dataElement;
//							if (output.isMultipleOutput()) {
//								JsonArray array = new JsonArray();
//								for (JsonElement element : innerObject.get(outputIdentifier).getAsJsonArray()) {
//									array.add(generateReferenceObject(element, basePath, baseURL));
//								}
//								dataElement = array;
//							}
//							else {
//								dataElement = generateReferenceObject(innerObject.get(outputIdentifier), basePath, baseURL);
//							}
//							innerObject.add(outputIdentifier, dataElement);
//						}
//
//						// all done
//						break;
//					}
//				}
//
//				// if not included, remove
//				if (!include) {
//					innerObject.remove(outputIdentifier);
//				}
//			}
//		}

		// write
		try {
			Writer writer = new OutputStreamWriter(outputStream);
			gson.toJson(responseObject, writer);
			writer.flush();
		}
		catch (IOException e) {
			throw new ResponseGenerateException("Could not write response to stream", e);
		}
	}

	private JsonObject generateReferenceObject(JsonElement element, String basePath, String baseURL) throws IOException, EncodeException, StorageException {
		// generate reference
		DataReferenceGenerator generator = new DataReferenceGenerator();
		DataReference ref = generator.generate(element);

		// return as object
		JsonObject refObj = new JsonObject();
		JsonObject innerRefObj = new JsonObject();
		refObj.add("DataReference", innerRefObj);
		innerRefObj.addProperty("href", ref.getURL().toString());
		innerRefObj.addProperty("mimeType", ref.getMimeType());
		return refObj;
	}
}
