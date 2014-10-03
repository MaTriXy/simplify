package org.cf.smalivm;

import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;

import java.util.ArrayDeque;
import java.util.Deque;

import org.cf.smalivm.context.ExecutionGraph;
import org.cf.smalivm.context.ExecutionNode;
import org.cf.smalivm.exception.MaxAddressVisitsExceeded;
import org.cf.smalivm.exception.MaxCallDepthExceeded;
import org.cf.smalivm.exception.MaxMethodVisitsExceeded;
import org.cf.smalivm.opcode.Op;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MethodExecutor {

    private static Logger log = LoggerFactory.getLogger(MethodExecutor.class.getSimpleName());

    private final VirtualMachine vm;
    private int totalVisits;

    MethodExecutor(VirtualMachine vm) {
        this.vm = vm;
        totalVisits = 0;
    }

    private void resetTotalVisits() {
        totalVisits = 0;
    }

    ExecutionGraph execute(ExecutionGraph graph) throws MaxAddressVisitsExceeded, MaxCallDepthExceeded,
    MaxMethodVisitsExceeded {
        TIntIntMap addressToVisitCount = new TIntIntHashMap();
        String methodDescriptor = graph.getMethodDescriptor();
        ExecutionNode currentNode = graph.getRoot();
        int callDepth = currentNode.getCallDepth();
        if (log.isInfoEnabled()) {
            log.info("Executing " + methodDescriptor + ", depth=" + callDepth);
        }
        if (currentNode.getCallDepth() > vm.getMaxCallDepth()) {
            throw new MaxCallDepthExceeded(methodDescriptor);
        }

        if (callDepth == 0) {
            // This is a new entry point.
            resetTotalVisits();
        }

        Deque<ExecutionNode> executeStack = new ArrayDeque<ExecutionNode>();
        executeStack.push(currentNode);
        while ((currentNode = executeStack.poll()) != null) {
            totalVisits += 1;
            checkMaxVisits(currentNode, methodDescriptor, addressToVisitCount);

            int[] childAddresses = currentNode.execute();
            for (int address : childAddresses) {
                // Each visit adds a new ExecutionNode in the pile. Piles can be inspected later for register / field
                // consensus for optimization.

                Op childOp = graph.getTemplateNode(address).getOp();
                ExecutionNode childNode = currentNode.getChild(childOp);
                graph.addNode(childNode);
            }

            executeStack.addAll(currentNode.getChildren());
        }

        return graph;
    }

    private void checkMaxVisits(ExecutionNode node, String methodDescriptor, TIntIntMap addressToVisitCount)
                    throws MaxAddressVisitsExceeded, MaxMethodVisitsExceeded {
        if (totalVisits > vm.getMaxMethodVisits()) {
            throw new MaxMethodVisitsExceeded(node, methodDescriptor);
        }

        int address = node.getAddress();
        int visitCount = addressToVisitCount.get(address);
        if (visitCount > vm.getMaxAddressVisits()) {
            throw new MaxAddressVisitsExceeded(node, methodDescriptor);
        }
        boolean adjusted = addressToVisitCount.adjustValue(address, 1);
        if (!adjusted) {
            addressToVisitCount.put(address, 1);
        }
    }

}
