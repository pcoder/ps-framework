package org.uncertweb.ps.handler.soap;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.jdom.Content;
import org.jdom.Element;
import org.uncertweb.ps.data.DataDescription;
import org.uncertweb.ps.data.DataReference;
import org.uncertweb.ps.data.Output;
import org.uncertweb.ps.data.ProcessOutputs;
import org.uncertweb.ps.data.RequestedOutput;
import org.uncertweb.ps.data.Response;
import org.uncertweb.ps.encoding.EncodeException;
import org.uncertweb.ps.encoding.Encoding;
import org.uncertweb.ps.encoding.EncodingRepository;
import org.uncertweb.ps.encoding.xml.AbstractXMLEncoding;
import org.uncertweb.ps.handler.ResponseGenerateException;
import org.uncertweb.ps.handler.data.DataReferenceGenerator;
import org.uncertweb.ps.process.AbstractProcess;
import org.uncertweb.ps.process.ProcessRepository;
import org.uncertweb.ps.storage.StorageException;
import org.uncertweb.xml.Namespaces;

public class XMLResponseGenerator {

	private static final Logger logger = Logger.getLogger(XMLResponseGenerator.class);

	public static Element generate(Response response) throws ResponseGenerateException {
		return generate(response, null);
	}

	public static Element generate(Response response, List<RequestedOutput> requestedOutputs) throws ResponseGenerateException {
		// get identifier, process, outputs
		String processIdentifier = response.getProcessIdentifier();
		AbstractProcess process = ProcessRepository.getInstance().getProcess(processIdentifier);
		ProcessOutputs outputs = response.getOutputs();

		// generate response
		Element responseElement = new Element(processIdentifier + "Response", Namespaces.PS);

		// add outputs
		for (String outputIdentifier : process.getOutputIdentifiers()) {
			// get output from process
			Output output = outputs.get(outputIdentifier);	

			// check if output was requested and as reference
			boolean requested = false;
			boolean reference = false;
			if (requestedOutputs == null) {
				requested = true;
			}
			else {
				for (RequestedOutput reqOutput : requestedOutputs) {
					if (reqOutput.getName().equals(output.getIdentifier())) {
						requested = true;
						reference = reqOutput.isReference();
					}
				}
			}

			// add output
			if (requested) {
				// get objects related to output
				List<Object> objects;
				if (output.isSingleOutput()) {
					objects = new ArrayList<Object>();
					objects.add(output.getAsSingleOutput().getObject());
				}
				else {
					objects = output.getAsMultipleOutput().getObjects();
				}

				// encode each object
				DataDescription dataDescription = process.getOutputDataDescription(outputIdentifier);
				for (Object o : objects) {			
					try {
						// base element
						Element outputElement = new Element(outputIdentifier, Namespaces.PS);
						responseElement.addContent(outputElement);

						// generate
						Content dataContent = generateData(o, dataDescription.getType(), reference);
						outputElement.addContent(dataContent);
					}
					catch (EncodeException e) {
						String message = "Couldn't encode data for " + output.getIdentifier() + ".";
						logger.error(message);
						throw new ResponseGenerateException(message, e);
					}
				}
			}
		}

		return responseElement;
	}

	private static Content generateData(Object object, Class<?> type, boolean reference) throws EncodeException, ResponseGenerateException {
		// get encoding
		EncodingRepository encodingRepo = EncodingRepository.getInstance();
		Encoding xmlEncoding = encodingRepo.getXMLEncoding(type);
		Encoding binaryEncoding = encodingRepo.getBinaryEncoding(type);

		if (xmlEncoding == null && binaryEncoding == null) {
			// FIXME: not a client problem
			throw new ResponseGenerateException("No encoding found for type " + type.getName() + ".");
		}
		else {
			Content content;
			if (reference || xmlEncoding == null) {
				try {
					content = generateReferenceData(object, type);
				}
				catch (StorageException e) {
					throw new ResponseGenerateException("Couldn't store data for reference.", e);
				}
			}
			else {
				content = generateInlineData(object, type);
			}
			return content;
		}
	}

	private static Content generateReferenceData(Object object, Class<?> type) throws EncodeException, StorageException {
		// generate data
		DataReference ref = new DataReferenceGenerator().generate(object);

		// add reference url to response
		Element referenceElement = new Element("DataReference", Namespaces.PS);
		referenceElement.setAttribute("href", ref.getURL().toString());
		referenceElement.setAttribute("mimeType", ref.getMimeType());
		return referenceElement;
	}

	private static Content generateInlineData(Object object, Class<?> dataClass) throws EncodeException {
		AbstractXMLEncoding encoding = EncodingRepository.getInstance().getXMLEncoding(dataClass);
		return encoding.encode(object).detach();
	}

}
