/********************************************************************************
 * Copyright (c) 2022 Imixs Software Solutions GmbH and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 ********************************************************************************/
package org.openbpmn.glsp.operations;

import java.util.List;
import java.util.logging.Logger;

import org.eclipse.glsp.server.operations.AbstractOperationHandler;
import org.eclipse.glsp.server.operations.DeleteOperation;
import org.openbpmn.bpmn.elements.Lane;
import org.openbpmn.bpmn.elements.Participant;
import org.openbpmn.bpmn.elements.Process;
import org.openbpmn.bpmn.elements.core.AbstractBPMNElement;
import org.openbpmn.bpmn.elements.core.BPMNElementEdge;
import org.openbpmn.bpmn.elements.core.BPMNElementNode;
import org.openbpmn.bpmn.exceptions.BPMNModelException;
import org.openbpmn.glsp.model.BPMNGModelState;

import com.google.inject.Inject;

public class BPMNDeleteNodeHandler extends AbstractOperationHandler<DeleteOperation> {
    private static Logger logger = Logger.getLogger(BPMNDeleteNodeHandler.class.getName());

    @Inject
    protected BPMNGModelState modelState;

    @Override
    public void executeOperation(final DeleteOperation operation) {

        List<String> elementIds = operation.getElementIds();
        if (elementIds == null || elementIds.size() == 0) {
            System.out.println("Elements to delete are not specified");
        }
        for (String id : elementIds) {
            // Update the source model
            System.out.println("...delete Element " + id);
            // test if the element is a participant or a FlowElement
            Participant participant = modelState.getBpmnModel().findParticipantById(id);
            if (participant != null) {
                // delete participant with the pool and all contained elements
                modelState.getBpmnModel().deleteParticipant(participant);
                continue;

            }

            // find the bpmnBaseElement
            AbstractBPMNElement bpmnElement = modelState.getBpmnModel().findElementById(id);
            if (bpmnElement != null && bpmnElement instanceof Lane) {
                // delete lane
                Lane lane = (Lane) bpmnElement;
                try {
                    modelState.getBpmnModel().openProcess(lane.getProcessId()).deleteLane(id);
                } catch (BPMNModelException e) {
                    logger.severe("Failed to delete lane: " + e.getMessage());
                }
                continue;

            }
            // finally we assume that this is a FlowElement
            if (bpmnElement != null && bpmnElement instanceof BPMNElementNode) {
                // open the corresponding process
                Process process = ((BPMNElementNode) bpmnElement).getBpmnProcess();
                process.deleteBPMNElementNode(id);
                continue;
            }

            if (bpmnElement != null && bpmnElement instanceof BPMNElementEdge) {
                // open the corresponding process
                Process process = ((BPMNElementEdge) bpmnElement).getBpmnProcess();
                process.deleteElementEdge(id);
                continue;
            }

            logger.warning("...no BPMN elmenet with id: " + id + " found!");

        }

        // reset model state
        modelState.reset();

    }

}