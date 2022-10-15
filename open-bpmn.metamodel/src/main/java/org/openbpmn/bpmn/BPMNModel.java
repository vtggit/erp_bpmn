package org.openbpmn.bpmn;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.openbpmn.bpmn.elements.BPMNBaseElement;
import org.openbpmn.bpmn.elements.BPMNBounds;
import org.openbpmn.bpmn.elements.BPMNLabel;
import org.openbpmn.bpmn.elements.BPMNParticipant;
import org.openbpmn.bpmn.elements.BPMNProcess;
import org.openbpmn.bpmn.exceptions.BPMNInvalidIDException;
import org.openbpmn.bpmn.exceptions.BPMNInvalidReferenceException;
import org.openbpmn.bpmn.exceptions.BPMNMissingElementException;
import org.openbpmn.bpmn.exceptions.BPMNModelException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A BPMNModel represents the dom tree of a BPMN model.
 * <p>
 * The model elements can be read or updated. The method 'save' writes the mode
 * back into the filesystem.
 * 
 * @author rsoika
 *
 */
public class BPMNModel {
    protected static Logger logger = Logger.getLogger(BPMNModel.class.getName());

    private static final SecureRandom random = new SecureRandom();
    private static final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    private Document doc;
    private Element definitions;
    private Node bpmnDiagram;
    protected Element bpmnPlane = null;
    protected Set<BPMNParticipant> participants = null;
    protected Set<BPMNProcess> processes = null;

    protected Element collaborationElement = null;

    public static final String PARTICIPANT = "participant";
    public static final String LANESET = "laneSet";
    public static final String LANE = "lane";

    public static List<String> BPMN_ACTIVITIES = Arrays.asList(new String[] { //
            BPMNTypes.TASK, //
            BPMNTypes.MANUAL_TASK, //
            BPMNTypes.SEND_TASK, //
            BPMNTypes.SERVICE_TASK, //
            BPMNTypes.SCRIPT_TASK, //
            BPMNTypes.USER_TASK, //
            "receiveTask", "businessRuleTask", "subProcess", "adHocSubProcess", "transaction", "callActivity" });

    public static List<String> BPMN_TASKS = Arrays.asList(new String[] { //
            BPMNTypes.TASK, //
            BPMNTypes.MANUAL_TASK, //
            BPMNTypes.SEND_TASK, //
            BPMNTypes.SERVICE_TASK, //
            BPMNTypes.SCRIPT_TASK, //
            BPMNTypes.USER_TASK //
    });

    public static List<String> BPMN_EVENTS = Arrays.asList(new String[] { //
            BPMNTypes.START_EVENT, //
            BPMNTypes.END_EVENT, //
            BPMNTypes.CATCH_EVENT, //
            BPMNTypes.THROW_EVENT //
    });

    public final static List<String> BPMN_FLOWELEMENTS = Arrays.asList(//
            BPMNTypes.TASK, //
            BPMNTypes.MANUAL_TASK, //
            BPMNTypes.SCRIPT_TASK, //
            BPMNTypes.SEND_TASK, //
            BPMNTypes.SERVICE_TASK, //
            BPMNTypes.EXCLUSIVE_GATEWAY, //
            BPMNTypes.INCLUSIVE_GATEWAY, //
            BPMNTypes.START_EVENT, //
            BPMNTypes.END_EVENT, //
            BPMNTypes.CATCH_EVENT, //
            BPMNTypes.THROW_EVENT, //
            BPMNTypes.SEQUENCE_FLOW);

    public static List<String> BPMN_EVENT_DEFINITIONS = Arrays.asList(new String[] { //
            BPMNTypes.EVENT_DEFINITION_CONDITIONAL, //
            BPMNTypes.EVENT_DEFINITION_TIMER, //
            BPMNTypes.EVENT_DEFINITION_SIGNAL, //
            BPMNTypes.EVENT_DEFINITION_MESSAGE, //
            BPMNTypes.EVENT_DEFINITION_LINK, //
            BPMNTypes.EVENT_DEFINITION_ERROR, //
            BPMNTypes.EVENT_DEFINITION_TERMINATE, //
            BPMNTypes.EVENT_DEFINITION_COMPENSATION });

    public static List<String> BPMN_GATEWAYS = Arrays.asList(new String[] { //
            BPMNTypes.EXCLUSIVE_GATEWAY, //
            BPMNTypes.INCLUSIVE_GATEWAY, //
            BPMNTypes.PARALLEL_GATEWAY, //
            BPMNTypes.COMPLEX_GATEWAY //
    });

    public static List<String> BPMN_SQUENCEFLOWS = Arrays.asList(new String[] { BPMNTypes.SEQUENCE_FLOW });

    private final Map<BPMNNS, String> URI_BY_NAMESPACE = new HashMap<>();

    /**
     * Returns the namespace uri for a given namespace
     * 
     * @param ns
     * @return
     */
    public String getNameSpaceUri(BPMNNS ns) {
        return URI_BY_NAMESPACE.get(ns);
    }

    public void setNameSpaceUri(BPMNNS ns, String uri) {
        URI_BY_NAMESPACE.put(ns, uri);
    }

    /**
     * This method instantiates a new BPMN model with the default BPMN namespaces.
     * 
     * @param doc
     */
    private BPMNModel() {
        setNameSpaceUri(BPMNNS.BPMN2, "http://www.omg.org/spec/BPMN/20100524/MODEL");
        setNameSpaceUri(BPMNNS.BPMNDI, "http://www.omg.org/spec/BPMN/20100524/DI");
        setNameSpaceUri(BPMNNS.DI, "http://www.omg.org/spec/DD/20100524/DI");
        setNameSpaceUri(BPMNNS.DC, "http://www.omg.org/spec/DD/20100524/DC");
    }

    /**
     * This method instantiates a new BPMN model based on a given
     * org.w3c.dom.Document. The method parses the BPMN namespaces.
     * <p>
     * The method throws a BPMNModelException in case the model file dose not show a
     * valid BPMN 2.0 structure.
     * <p>
     * If the given model does not include a bpmndi:BPMNDiagram section the method
     * will create an empty default diagram.
     * 
     * @param doc
     * @throws BPMNModelException
     */
    public BPMNModel(Document doc) throws BPMNModelException {
        this();
        if (doc != null) {
            this.doc = doc;

            definitions = doc.getDocumentElement();

            // parse the BPMN namespaces
            NamedNodeMap defAttributes = definitions.getAttributes();
            for (int j = 0; j < defAttributes.getLength(); j++) {
                Node node = defAttributes.item(j);
                // update to new namespace uri if not matching the default URI
                if (BPMNNS.BPMN2.prefix.equals(node.getLocalName())
                        && !getNameSpaceUri(BPMNNS.BPMN2).equals(node.getNodeValue())) {
                    logger.fine("...set BPMN2 namespace URI: " + node.getNodeValue());
                    setNameSpaceUri(BPMNNS.BPMN2, node.getNodeValue());
                }
                if (BPMNNS.BPMNDI.prefix.equals(node.getLocalName())
                        && !getNameSpaceUri(BPMNNS.BPMNDI).equals(node.getNodeValue())) {
                    logger.fine("...set BPMNDI namespace URI: " + node.getNodeValue());
                    setNameSpaceUri(BPMNNS.BPMNDI, node.getNodeValue());
                }
                if (BPMNNS.DC.prefix.equals(node.getLocalName())
                        && !getNameSpaceUri(BPMNNS.DC).equals(node.getNodeValue())) {
                    logger.fine("...set DC namespace URI: " + node.getNodeValue());
                    setNameSpaceUri(BPMNNS.DC, node.getNodeValue());
                }
                if (BPMNNS.DI.prefix.equals(node.getLocalName())
                        && !getNameSpaceUri(BPMNNS.DI).equals(node.getNodeValue())) {
                    logger.fine("...set DI namespace URI: " + node.getNodeValue());
                    setNameSpaceUri(BPMNNS.DI, node.getNodeValue());
                }
            }

            // find bpmndi:BPMNDiagram
            NodeList diagramList = doc.getElementsByTagName(BPMNNS.BPMNDI.prefix + ":BPMNDiagram");
            if (diagramList != null && diagramList.getLength() > 0) {
                bpmnDiagram = diagramList.item(0);
            } else {
                // no diagram included - so we create an empty one
                getLogger().warning("No bpmndi:BPMNDiagram found - created default diagram");
                // <bpmndi:BPMNDiagram id="BPMNDiagram_1" name="Default Process Diagram">
                Element bpmnDiagram = createElement(BPMNNS.BPMNDI, "BPMNDiagram");
                bpmnDiagram.setAttribute("id", "BPMNDiagram_1");
                bpmnDiagram.setAttribute("name", "OpenBPMN Diagram");
                definitions.appendChild(bpmnDiagram);
                setBpmnDiagram(bpmnDiagram);
            }

            // find BPMNPlane
            NodeList planeList = doc.getElementsByTagName(BPMNNS.BPMNDI.prefix + ":BPMNPlane");
            if (planeList != null && planeList.getLength() > 0) {
                bpmnPlane = (Element) planeList.item(0);
            }

            // init the participant and process list
            loadParticipantList();
            loadProcessList();

        }
    }

    public Element getDefinitions() {
        return definitions;
    }

    public Document getDoc() {
        return doc;
    }

    /**
     * Adds a new xml namespace if not yet defined
     * 
     * @param namespace
     * @param uri
     */
    public void addNamespace(String namespace, String uri) {
        if (!definitions.hasAttribute("xmlns:" + namespace)) {
            definitions.setAttribute("xmlns:" + namespace, uri);
        }
    }

    public Set<BPMNParticipant> getParticipants() {
        if (participants == null) {
            participants = new LinkedHashSet<BPMNParticipant>();
        }
        return participants;
    }

    public void setParticipants(Set<BPMNParticipant> participants) {
        this.participants = participants;
    }

    public Set<BPMNProcess> getProcesses() {
        if (processes == null) {
            processes = new LinkedHashSet<BPMNProcess>();
        }
        return processes;
    }

    public void setProcesses(Set<BPMNProcess> processes) {
        this.processes = processes;
    }

    public Node getBpmnDiagram() {
        return bpmnDiagram;
    }

    public void setBpmnDiagram(Node bpmnDiagram) {
        this.bpmnDiagram = bpmnDiagram;
    }

    public Element getBpmnPlane() {
        return bpmnPlane;
    }

    public void setBpmnPlane(Element bpmnPlane) {
        this.bpmnPlane = bpmnPlane;
    }

    /**
     * This method returns true if the current model contains a CollaborationDiagram
     * 
     * @return
     */
    public boolean isCollaborationDiagram() {
        return collaborationElement != null;
    }

    /**
     * Adds a new BPMNParticipant element and the corresponding BPMNProcess to the
     * model.
     * <p>
     * The method verifies if a the model already has a bpmn2:collaboration section.
     * If not, the method creates one.
     * <p>
     * In case the diagram type switches from a process diagram to a collaboration
     * diagram, the method also updates the attribute 'bpmnElement' of the bpmnPlane
     * to the new created bpmn2:collaboration element id.
     * <p>
     * {@code
     * <bpmn2:collaboration id="Collaboration_1" name="Default Collaboration">
     * <bpmn2:participant id="Participant_1" name="Pool 1"/>
     * }
     * 
     * @param name
     * @return the BPMNParticipant
     * @throws BPMNModelException
     */
    public BPMNParticipant addParticipant(String name) throws BPMNModelException {

        // first verify if the model already is a Collaboration model. If not we create
        // a bpmn2:collaboration
        if (!this.isCollaborationDiagram()) {
            logger.info("Migrating Process Diagram to Collaboration Diagram.");
            // create the default collaboration element
            String collaborationID = "collaboration_1";
            collaborationElement = createElement(BPMNNS.BPMN2, "collaboration");
            collaborationElement.setAttribute("id", collaborationID);
            collaborationElement.setAttribute("name", "Default Collaboration");
            this.definitions.insertBefore(collaborationElement, definitions.getFirstChild());
            // update the BPMNPlane attribute 'bpmnElement' which now references the
            // collaboration element
            this.getBpmnPlane().setAttribute("bpmnElement", collaborationID);

            // Now we migrate all existing processes into the new collaboration element....
            for (BPMNProcess existingProcess : processes) {
                Element migratedParticipantNode = createElement(BPMNNS.BPMN2, "participant");
                String participantID = BPMNModel.generateShortID("participant");
                migratedParticipantNode.setAttribute("id", participantID);
                migratedParticipantNode.setAttribute("name", existingProcess.getName());
                migratedParticipantNode.setAttribute("processRef", existingProcess.getId());

                collaborationElement.appendChild(migratedParticipantNode);
                existingProcess.setAttribute("definitionalCollaborationRef", collaborationElement.getAttribute("id"));
                // finally add a new BPMNParticipatn to the paticipant list
                getParticipants().add(new BPMNParticipant(this, migratedParticipantNode));
            }

        }

        // create a new Dom node...
        Element participantNode = createElement(BPMNNS.BPMN2, "participant");
        String participantID = BPMNModel.generateShortID("participant");
        participantNode.setAttribute("id", participantID);
        participantNode.setAttribute("name", name);
        this.collaborationElement.appendChild(participantNode);
        // add BPMNParticipant instance
        BPMNParticipant bpmnParticipant = new BPMNParticipant(this, participantNode);
        getParticipants().add(bpmnParticipant);

        // now add the corresponding Process
        // <bpmn2:process id="Process_2" name="Non-initiating Process"
        // definitionalCollaborationRef="Collaboration_1" isExecutable="false"/>
        int processNumber = this.getProcesses().size() + 1;
        BPMNProcess process = buildProcess(BPMNModel.generateShortID("process"), "Process " + processNumber,
                BPMNTypes.PROCESS_TYPE_PRIVATE);
        process.setAttribute("definitionalCollaborationRef", collaborationElement.getAttribute("id"));
        bpmnParticipant.setProcessRef(process.getId());
        // create the pool
        createPool(bpmnParticipant);

        return bpmnParticipant;

    }

    /**
     * This method creates a BPMNShape for this participant. The shape element
     * represents the Pool within the BPMNDiagram section.
     * 
     * @throws BPMNModelException
     */
    private void createPool(BPMNParticipant bpmnParticipant) throws BPMNModelException {
        if (!bpmnParticipant.hasPool()) {
            // create shape element
            Element poolShape = this.buildBPMNShape(bpmnParticipant);
            bpmnParticipant.setBpmnShape(poolShape);
            bpmnParticipant.setBounds(10.0, 10.0, BPMNParticipant.DEFAULT_WIDTH, BPMNParticipant.DEFAULT_HEIGHT);
            // create BPMNLabel
            BPMNLabel bpmnLabel = new BPMNLabel(this, poolShape);
            bpmnLabel.updateLocation(10.0, 10.0);
        }
    }

    /**
     * Builds a new BPMNProcess element and adds this into this model instance. This
     * method also generates a default BPMNPlane if not yet created
     * <p>
     * 
     * <pre>
     * {@code
     *   <bpmn2:process id="process_1"/>
         <bpmndi:BPMNDiagram id="BPMNDiagram_1" name="Default Process Diagram">
            <bpmndi:BPMNPlane bpmnElement="process_1" id="BPMNPlane_process_1"/>
         </bpmndi:BPMNDiagram>
     * }
     * </pre>
     * 
     * <p>
     * The method also expects a process type (public|private). If not type is
     * specified the method builds a public default process.
     * <p>
     * A BPMNModelException is thrown if the given id is already in use.
     * 
     * @param id
     * @param name
     * @param type - EventType
     * @throws BPMNModelException
     */
    protected BPMNProcess buildProcess(String id, String name, String type) throws BPMNModelException {

        if (id == null || id.isEmpty()) {
            throw new BPMNInvalidIDException(BPMNInvalidIDException.MISSING_ID, "id must not be empty or null!");
        }
        // verify id
        for (BPMNProcess process : processes) {
            if (process.getId().equals(id)) {
                throw new BPMNInvalidIDException(BPMNInvalidIDException.DUPLICATE_ID,
                        "id '" + id + "' is already in use!");
            }
        }
        // xmlns:bpmn2="http://www.omg.org/spec/BPMN/20100524/MODEL"
        Element process = createElement(BPMNNS.BPMN2, "process");
        logger.fine(process.getNodeName());
        logger.fine(process.getLocalName());
        logger.fine(process.getNamespaceURI());
        process.setAttribute("id", id);
        // set optional name
        if (name != null) {
            process.setAttribute("name", name);
        }

        // set type
        if (type == null || type.isEmpty()) {
            type = BPMNTypes.PROCESS_TYPE_PUBLIC;
        }
        process.setAttribute("processType", type);

        definitions.insertBefore(process, this.getBpmnDiagram());

        BPMNProcess bpmnProcess = new BPMNProcess(this, process, type);
        this.getProcesses().add(bpmnProcess);

        // add an empty BPMNPlane tag
        // <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="process_2">
        if (bpmnPlane == null) {
            bpmnPlane = createElement(BPMNNS.BPMNDI, "BPMNPlane");
            bpmnPlane.setAttribute("id", "BPMNPlane_1");
            bpmnPlane.setAttribute("bpmnElement", id);
            this.getBpmnDiagram().appendChild(bpmnPlane);
        }

        return bpmnProcess;
    }

    /**
     * Creates a BPMN shape node for this element
     * <p>
     * <bpmndi:BPMNShape id="BPMNShape_1" bpmnElement="StartEvent_1">
     * 
     * @throws BPMNMissingElementException
     */
    public Element buildBPMNShape(BPMNBaseElement bpmnElement) throws BPMNModelException {
        Element bpmnShape;
        if (getBpmnPlane() == null) {
            throw new BPMNMissingElementException("Missing bpmnPlane in current model context");
        }
        if (bpmnElement.getId() != null) {
            bpmnShape = createElement(BPMNNS.BPMNDI, "BPMNShape");
            bpmnShape.setAttribute("id", BPMNModel.generateShortID("BPMNShape"));
            bpmnShape.setAttribute("bpmnElement", bpmnElement.getId());
            getBpmnPlane().appendChild(bpmnShape);
            return bpmnShape;
        } else {
            throw new BPMNInvalidReferenceException("Missing ID attribute");
        }
    }

    /**
     * This Method opens a BPMNProcess with the given ID and initializes all BPMN
     * elements of the Process. This is a lazy loading mechanism to avoid loading
     * the full model if not needed.
     * <p>
     * The method returns null if no process with the given ID exists.
     * <p>
     * In case no ID is provided (null) the method returns the first public
     * (default) process from the model.
     * 
     * @param string
     * @return BPMNProcess instance
     * @throws BPMNModelException
     */
    public BPMNProcess openProcess(String id) throws BPMNModelException {
        BPMNProcess process = null;
        if (processes != null) {
            Iterator<BPMNProcess> it = processes.iterator();

            while (it.hasNext()) {
                BPMNProcess p = it.next();
                // default process?
                if (id == null || id.isEmpty()) {
                    if (BPMNTypes.PROCESS_TYPE_PUBLIC.equals(p.getProcessType())) {
                        // we take the first public process (should only exists once in the model)
                        process = p;
                        break;
                    }
                } else {
                    // verify process by id
                    if (id.equals(p.getId())) {
                        process = p;
                        break;
                    }
                }
            }
        }

        // if we found a matching process than we can initialize it
        if (process != null) {
            process.init();
        }
        return process;
    }

    /**
     * This Method opens the public default BPMNProcess and initializes all BPMN
     * elements of the Process. The default process always exists and is not
     * embedded in a Pool.
     */
    public BPMNProcess openDefaultProcess() {
        try {
            return openProcess(null);
        } catch (BPMNModelException e) {
            logger.warning("no default process defined!");
            return null;
        }
    }

    /**
     * Deletes a BPMNParticipant element from this model.
     * <p>
     * 
     * @param id
     */
    public void deleteBPMNParticipant(BPMNParticipant participant) {
        if (participant != null) {
            BPMNProcess process = participant.openProcess();
            
            process.deleteAllElements();
            this.definitions.removeChild(process.getElementNode());
            if (participant.hasPool()) {
                this.bpmnPlane.removeChild(participant.getBpmnShape());
            }
            this.collaborationElement.removeChild(participant.getElementNode());
            // remove the participant with its elements
            this.getProcesses().remove(process);
            this.getParticipants().remove(participant);
        }

    }

    public Element createElement(BPMNNS ns, String type) {
        Element element = this.getDoc().createElementNS(getNameSpaceUri(ns), ns.prefix + ":" + type);
        return element;
    }

    /**
     * Finds a BPMNBaseElement by ID within this model. The method iterates over all
     * existing Processes and its contained BPMNFlowElements.
     * <p>
     * If no element with the given ID exists, the method returns null.
     * 
     * @param id - the BPMN Element id
     * @return
     */
    public BPMNBaseElement findBPMNBaseElementById(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }

        // test for participant...
        BPMNParticipant participant = this.findBPMNParticipantById(id);
        if (participant != null) {
            return participant;
        }

        // iterate over all processes
        Set<BPMNProcess> processList = this.getProcesses();
        for (BPMNProcess process : processList) {
            if (id.equals(process.getId())) {
                // the id matches a Process
                return process;
            } else {
                // analyze the content of the process
                BPMNBaseElement baseElement = process.findBaseElementById(id);
                if (baseElement != null) {
                    return baseElement;
                }
            }
        }

        // no corresponding element found!
        return null;
    }

  
    /**
     * This method returns the BPMNBounds element of a BPMNBaseElement with the
     * given ID. This finder method can be used to just adjust the Bounds in the
     * Diagram section of a model.
     * <p>
     * In case of a process diagram, the method verfies all FlowElements within the
     * default process.
     * <p>
     * In case of a collaboration diagram, the method verifies all Participants and
     * also all FlowElements within the corresponding processes.
     * <p>
     * If no element with the given ID exists, the method returns null.
     * 
     * @param id         - the BPMN Element id
     * @param deepSearch - if true also containing elements like Pools are analyzed.
     * @return
     */
    public BPMNBounds findBPMNBoundsById(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        try {
            if (isCollaborationDiagram()) {
                // iterate over all participants
                Set<BPMNParticipant> participantList = this.getParticipants();
                for (BPMNParticipant participant : participantList) {
                    if (id.equals(participant.getId())) {
                        // the id matches the participant
                        return participant.getBounds();
                    } else {
                        // analyze the content of the process
                        BPMNBaseElement baseElement = participant.openProcess().findBaseElementById(id);
                        if (baseElement != null) {
                            return baseElement.getBounds();
                        }
                    }
                }
            } else {
                // just analyze the default process
                BPMNBaseElement baseElement = openDefaultProcess().findBaseElementById(id);
                if (baseElement != null) {
                    return baseElement.getBounds();
                }
            }
        } catch (BPMNMissingElementException e) {
            logger.warning("Failed to found Bounds for element '" + id + "' : " + e.getMessage());
        }

        // no corresponding element found!
        return null;
    }

    /**
     * Finds a BPMNParticipant by its id
     * 
     * @param id
     * @return
     */
    public BPMNParticipant findBPMNParticipantById(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }

        if (isCollaborationDiagram()) {
            // iterate over all participants
            Set<BPMNParticipant> participantList = this.getParticipants();
            for (BPMNParticipant participant : participantList) {
                if (id.equals(participant.getId())) {
                    // the id matches the participant
                    return participant;
                }
            }
        }

        // no corresponding element found!
        return null;
    }

    /**
     * Finds a corresponding BPMNParticipant by a processID
     * 
     * @param id
     * @return
     */
    public BPMNParticipant findBPMNParticipantByProcessId(String processId) {
        if (processId == null || processId.isEmpty()) {
            return null;
        }
        if (isCollaborationDiagram()) {
            for (BPMNParticipant _participant : participants) {
                if (processId.equals(_participant.getProcessRef())) {
                    return _participant;
                }
            }
        }
        // no corresponding element found!
        return null;
    }

    /**
     * This helper method returns a set of child nodes by name from a given parent
     * node.
     * 
     * @param parent
     * @param nodeName
     * @return - list of nodes. If no nodes were found, the method returns an empty
     *         list
     */
    public static Set<Element> findChildNodesByName(Element parent, String nodeName) {
        Set<Element> result = new LinkedHashSet<Element>();
        if (parent != null && nodeName != null) {
            NodeList childs = parent.getChildNodes();
            for (int i = 0; i < childs.getLength(); i++) {
                Node childNode = childs.item(i);
                if (childNode.getNodeType() == Node.ELEMENT_NODE && nodeName.equals(childNode.getNodeName())) {
                    result.add((Element) childNode);
                }
            }
        }
        return result;
    }

    /**
     * Writes the current instance to the file system.
     * 
     * @param file
     */
    public void save(String file) {
        try (FileOutputStream output = new FileOutputStream(file)) {
            if (doc == null) {
                logger.severe("...unable to save file - doc is null!");
            }

            writeXml(doc, output);
        } catch (TransformerException | IOException e) {
            e.printStackTrace();
        }
    }

    public void save(URI targetURI) {
        save(Paths.get(targetURI).toString());
    }

    /**
     * Generates a random 12 byte id
     * <p>
     * HEX: 0-9, a-f. For example: 6587fddb, c0f182c1
     * 
     * @return
     */
    public static String generateShortID() {
        byte[] buffer = new byte[12];
        random.nextBytes(buffer);
        String id = encoder.encodeToString(buffer);
        // remove -_
        id = id.replace("-", "0");
        id = id.replace("_", "0");
        return id;

    }

    /**
     * Generates a random short 6 byte id with a prafix
     * <p>
     * HEX: 0-9, a-f. For example: BPMNShape_a55ac5, BPMNShape_1382c1
     * 
     * @return
     */
    public static String generateShortID(String prefix) {
        byte[] buffer = new byte[4];
        random.nextBytes(buffer);
        String id = encoder.encodeToString(buffer);
        // remove -_
        id = id.replace("-", "0");
        id = id.replace("_", "0");
        return prefix + "_" + id;

    }

    /**
     * Returns true if the node is an activity node.
     * 
     * @param node
     * @return
     */
    public static boolean isActivity(Node node) {
        return (BPMN_ACTIVITIES.contains(node.getLocalName()));
    }

    public static boolean isActivity(BPMNBaseElement element) {
        return isActivity(element.getElementNode());
    }

    /**
     * Returns true if the node is an participant node.
     * 
     * @param node
     * @return
     */
    public static boolean isParticipant(Node node) {
        return (PARTICIPANT.equals(node.getLocalName()));
    }

    /**
     * Returns true if the node is an LaneSet node.
     * 
     * @param node
     * @return
     */
    public static boolean isLaneSet(Node node) {
        return (LANESET.equals(node.getLocalName()));
    }

    /**
     * Returns true if the node is a gateway node.
     * 
     * @param node
     * @return
     */
    public static boolean isGateway(Node node) {
        return (BPMN_GATEWAYS.contains(node.getLocalName()));
    }

    public static boolean isGateway(BPMNBaseElement element) {
        return isGateway(element.getElementNode());
    }

    /**
     * Returns true if the node is an event node.
     * 
     * @param node
     * @return
     */
    public static boolean isEvent(Node node) {
        return (BPMN_EVENTS.contains(node.getLocalName()));
    }

    public static boolean isEvent(BPMNBaseElement element) {
        return isEvent(element.getElementNode());
    }

    /**
     * Returns true if the node is a sequenceFlow.
     * 
     * @param node
     * @return
     */
    public static boolean isSequenceFlow(Node node) {
        return (BPMN_SQUENCEFLOWS.contains(node.getLocalName()));
    }

    /**
     * This helper method returns a BPMNDI node for the given bpmnElement. A BPMNDI
     * element is identified by the ID stored in the attribute bpmnElement
     * <p>
     * 
     * <pre>
     *  {@code<bpmndi:BPMNShape id="BPMNShape_ihwxlQ" bpmnElement="event-1">}
     * </pre>
     * <p>
     * 
     * @param bpmnPlane - the bpmnPlane Element
     * @param nodeName  - name of the element
     * @param id        - the id referring to the main BPMN element
     */
    public Node findBPMNPlaneElement(String nodeName, String id) {
        if (id == null || id.isEmpty() || bpmnPlane == null || nodeName == null) {
            return null;
        }
        String fullNodeName = BPMNNS.BPMNDI.prefix + ":" + nodeName;
        NodeList childList = bpmnPlane.getChildNodes();
        for (int i = 0; i < childList.getLength(); i++) {
            Node child = childList.item(i);
            // check the attribute bpmnElement
            if (fullNodeName.equals(child.getNodeName()) && child.hasAttributes()) {
                // get attributes names and values
                NamedNodeMap attributesMap = child.getAttributes();
                for (int j = 0; j < attributesMap.getLength(); j++) {
                    Node attr = attributesMap.item(j);
                    if ("bpmnElement".equals(attr.getNodeName()) && id.equals(attr.getNodeValue())) {
                        // found it!
                        return child;
                    }
                }
            }

        }
        // not found!
        return null;
    }

    /**
     * Returns the central loger instance
     * 
     * @return
     */
    public static Logger getLogger() {
        return logger;
    }

    public static void log(String message) {
        logger.info(message);
    }

    public static void warning(String message) {
        logger.warning(message);
    }

    public static void error(String message) {
        logger.severe(message);
    }

    public static void debug(String message) {
        logger.info(message);
    }

    /**
     * This helper method loads the participant elements from a collaboration
     * diagram located in the 'bpmn2:collaboration' section. This section is
     * optional. If the model is not a collaboration model the method
     * getPaticpants() returns null
     * 
     * @throws BPMNModelException
     * 
     */
    private void loadParticipantList() throws BPMNModelException {
        participants = new LinkedHashSet<BPMNParticipant>();
        NodeList collaborationNodeList = definitions.getElementsByTagName(BPMNNS.BPMN2.prefix + ":collaboration");
        if (collaborationNodeList != null && collaborationNodeList.getLength() > 0) {

            // we only take the first collaboration element (this is what is expected)
            collaborationElement = (Element) collaborationNodeList.item(0);
            // now find all participants...
            NodeList participantList = collaborationElement.getElementsByTagName(BPMNNS.BPMN2.prefix + ":participant");
            logger.fine("..found " + participantList.getLength() + " participants");
            for (int i = 0; i < participantList.getLength(); i++) {
                Element item = (Element) participantList.item(i);
                BPMNParticipant participant;
                participant = new BPMNParticipant(this, item);
                // set processRef
                participant.setProcessRef(item.getAttribute("processRef"));
                participants.add(participant);
            }
        }
    }

    /**
     * This helper method loads the process elements from the current model. If the
     * model does not yet contain any process the method getProcesses() returns null
     * <p>
     * The method also verify the attribute processType. If the processType is not
     * set and but the process ID is listed in the optional list of participants the
     * processType will be set to 'Private'.
     * 
     * @param string
     * @return BPMNProcess instance
     * @throws BPMNModelException
     */
    private void loadProcessList() throws BPMNModelException {
        processes = new LinkedHashSet<BPMNProcess>();
        int publicCount = 0;

        // find process
        NodeList processList = definitions.getElementsByTagName(BPMNNS.BPMN2.prefix + ":process");
        if (processList != null && processList.getLength() > 0) {
            for (int i = 0; i < processList.getLength(); i++) {
                Element item = (Element) processList.item(i);
                String id = item.getAttribute("id");
                if (id == null) {
                    id = "";
                }
                String processType = item.getAttribute("processType");
                if (processType.isEmpty()) {
                    // we do not have a process Type provided. We now verify
                    // if the process ID is listed in the list of participants.
                    // If the participant has a shape (Pool) than the type is 'Private'. Otherwise
                    // it seems to be the public default process
                    for (BPMNParticipant participant : participants) {
                        if (id.equals(participant.getProcessRef())) {
                            // do we have a pool for this participant?
                            if (participant.getBpmnShape() != null) {
                                processType = BPMNTypes.PROCESS_TYPE_PRIVATE;
                            } else {
                                // no Pool - so we assume this is the public process
                                processType = BPMNTypes.PROCESS_TYPE_PUBLIC;
                                publicCount++;
                            }
                            break;
                        }
                    }
                } else {
                    if (BPMNTypes.PROCESS_TYPE_PUBLIC.equals(processType)) {
                        publicCount++;
                    }
                }

                BPMNProcess bpmnProcess = new BPMNProcess(this, item, processType);
                processes.add(bpmnProcess);
            }
        }

        // if we do not have a process at all or a public default process is missing in
        // the participant list, we create a default process now
        if (processes.size() == 0 || (participants.size() > 0 && publicCount == 0)) {
            buildProcess("process_" + (processes.size() + 1), "Default Process", BPMNTypes.PROCESS_TYPE_PUBLIC);
        } else if (publicCount > 1) {
            getLogger().warning("Invalid model structure! The model contains more than one public process instance!");
        }

    }

    /**
     * Helper method to write the dom document to an output stream
     * <p>
     * We also call the helper method 'clearBlankLines' to get rid of unnecessary
     * white space. See also discussion here
     * https://stackoverflow.com/questions/12669686/how-to-remove-extra-empty-lines-from-xml-file/12670194#12670194
     * 
     * @param doc
     * @param output
     * @throws TransformerException
     */
    private void writeXml(Document doc, OutputStream output) throws TransformerException {
        // clenup blank lines
        clearBlankLines();

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(output);
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(source, result);
    }

    /**
     * This helper method use XPath to find all whitespace-only TEXT nodes in the
     * current doucment, iterate through them and remove each one from its parent
     * (using getParentNode().removeChild()). Something like this would do (doc
     * would be your DOM document object):
     * 
     * See:
     * https://stackoverflow.com/questions/12669686/how-to-remove-extra-empty-lines-from-xml-file/12670194#12670194
     * 
     * @param element
     */
    private void clearBlankLines() {
        try {
            XPath xp = XPathFactory.newInstance().newXPath();
            NodeList nl;
            nl = (NodeList) xp.evaluate("//text()[normalize-space(.)='']", doc, XPathConstants.NODESET);
            for (int i = 0; i < nl.getLength(); ++i) {
                Node node = nl.item(i);
                node.getParentNode().removeChild(node);
            }
        } catch (XPathExpressionException e) {
            logger.warning("Failed to clean blank up lines during save: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
