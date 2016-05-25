/*
 * Copyright (C) 2015 Evangelos Pournaras
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package agents;

import agents.dataset.FileAgentDataset;
import agents.plan.AggregatePlan;
import agents.plan.CombinationalPlan;
import agents.plan.GlobalPlan;
import agents.fitnessFunction.FitnessFunction;
import agents.plan.Plan;
import agents.plan.PossiblePlan;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import messages.EPOSBroadcast;
import messages.EPOSRequest;
import messages.EPOSResponse;
import org.joda.time.DateTime;
import protopeer.Finger;
import protopeer.measurement.MeasurementLog;
import protopeer.network.Message;
import agents.dataset.AgentDataset;

/**
 *
 * @author Evangelos
 */
public class EPOSAgent extends Agent {
    
    private final int historySize;
    private final TreeMap<DateTime, AgentPlans> history = new TreeMap<>();

    private final Map<Finger, EPOSRequest> messageBuffer = new HashMap<>();

    private final FitnessFunction fitnessFunction;
    private double robustness;

    private Plan costSignal;
    private AgentPlans current = new AgentPlans();
    private List<Integer> selectedCombination;
    private Plan childAggregatePlan;

    public static class Factory extends AgentFactory {

        @Override
        public Agent create(int id, AgentDataset dataSource, String treeStamp, File outFolder, DateTime initialPhase, DateTime previousPhase, Plan costSignal, int historySize) {
            return new EPOSAgent(id, dataSource, treeStamp, fitnessFunction, initialPhase, previousPhase, costSignal, historySize, outFolder);
        }
    }

    public EPOSAgent(int id, AgentDataset dataSource, String treeStamp, FitnessFunction fitnessFunction, DateTime initialPhase, DateTime previousPhase, Plan costSignal, int historySize, File outFolder) {
        super(id, dataSource, treeStamp, outFolder, initialPhase, new ArrayList<>(), new ArrayList<>(), false);
        this.fitnessFunction = fitnessFunction;
        this.historySize = historySize;
        this.costSignal = costSignal;
    }

    @Override
    void runPhase() {
        initPhase();
        if (this.isLeaf()) {
            readPlans();
            informParent();
        }
    }

    private void initPhase() {
        if (this.history.size() > this.historySize) {
            this.history.remove(this.history.firstKey());
        }
        this.robustness = 0.0;
        this.possiblePlans.clear();
        this.childAggregatePlan = new AggregatePlan(this);
        this.current.global = new GlobalPlan(this);
        this.current.aggregate = new AggregatePlan(this);
        this.current.selectedLocalPlan = new PossiblePlan(this);
        this.current.selectedPlan = new CombinationalPlan(this);
        this.messageBuffer.clear();
        this.selectedCombination = null;
    }

    public void informParent() {
        EPOSRequest request = new EPOSRequest();
        request.child = getPeer().getFinger();
        request.possiblePlans = this.possiblePlans;
        request.aggregatePlan = this.current.aggregate;
        if (previousPhase != null) {
            AgentPlans historicPlans = history.get(previousPhase);
            request.aggregateHistoryPlan = historicPlans.aggregate;
        } else {
            request.aggregateHistoryPlan = null;
        }
        this.getPeer().sendMessage(this.parent.getNetworkAddress(), request);
    }

    private void preProcessing() {
        for (Finger child : children) {
            EPOSRequest msg = messageBuffer.get(child);
            childAggregatePlan.add(msg.aggregatePlan);
        }
    }

    private void select() {
        List<Plan> combinationalPlans = new ArrayList<>();
        List<List<Integer>> combinationalSelections = new ArrayList<>();

        // init combinations
        int numCombinations = 1;
        for (Finger child : children) {
            EPOSRequest msg = messageBuffer.get(child);
            numCombinations *= msg.possiblePlans.size();
        }
        for (int i = 0; i < numCombinations; i++) {
            combinationalPlans.add(new CombinationalPlan(this));
            combinationalSelections.add(new ArrayList<>());
        }

        // calc all possible combinations
        int factor = 1;
        for (Finger child : children) {
            List<Plan> childPlans = messageBuffer.get(child).possiblePlans;
            int numPlans = childPlans.size();
            for (int i = 0; i < numCombinations; i++) {
                int planIdx = (i / factor) % numPlans;
                Plan combinationalPlan = combinationalPlans.get(i);
                combinationalPlan.add(childPlans.get(planIdx));
                combinationalPlan.setDiscomfort(combinationalPlan.getDiscomfort() + childPlans.get(planIdx).getDiscomfort());
                combinationalSelections.get(i).add(planIdx);
            }
            factor *= numPlans;
        }

        AgentPlans historicPlans = null;
        if (previousPhase != null) {
            historicPlans = history.get(previousPhase);
        }

        // select best combination
        int selectedCombination = fitnessFunction.select(this, childAggregatePlan, combinationalPlans, costSignal, historicPlans);
        current.selectedPlan.set(combinationalPlans.get(selectedCombination));
        this.selectedCombination = combinationalSelections.get(selectedCombination);
    }

    private void update() {
        current.aggregate.set(childAggregatePlan);
        current.aggregate.add(current.selectedPlan);
    }

    private void informChildren() {
        for (int c = 0; c < children.size(); c++) {
            Finger child = children.get(c);
            int selected = selectedCombination.get(c);

            EPOSResponse response = new EPOSResponse();
            response.selectedPlan = messageBuffer.get(child).possiblePlans.get(selected);
            getPeer().sendMessage(child.getNetworkAddress(), response);
        }
    }

    private void broadcast() {
        EPOSBroadcast broadcast = new EPOSBroadcast();
        broadcast.coordinationPhase = current.global.getCoordinationPhase();
        broadcast.globalPlan = current.global;
        for (Finger child : children) {
            getPeer().sendMessage(child.getNetworkAddress(), broadcast);
        }
    }

    @Override
    public void handleIncomingMessage(Message message) {
        if (message instanceof EPOSRequest) {
            EPOSRequest request = (EPOSRequest) message;
            this.messageBuffer.put(request.child, request);
            if (this.children.size() == this.messageBuffer.size()) {
                this.preProcessing();
                this.select();
                this.update();
                this.informChildren();
                this.readPlans();
                if (this.isRoot()) {
                    AgentPlans historic = null;
                    if (previousPhase != null) {
                        historic = history.get(previousPhase);
                    }
                    int selected = fitnessFunction.select(this, current.aggregate, possiblePlans, costSignal, historic);
                    Plan selectedPlan = possiblePlans.get(selected);
                    current.selectedLocalPlan.set(selectedPlan);
                    current.global.set(current.aggregate);
                    current.global.add(selectedPlan);

                    history.put(currentPhase, current);

                    this.robustness = fitnessFunction.getRobustness(current.global, costSignal, historic);

                    System.out.println(current.global.getNumberOfStates() + "," + currentPhase.toString("yyyy-MM-dd") + "," + robustness + ": " + current.global);
                    this.broadcast();
                } else {
                    this.informParent();
                }
            }
        } else if (message instanceof EPOSResponse) {
            EPOSResponse response = (EPOSResponse) message;
            current.selectedLocalPlan.set(response.selectedPlan);
        } else if (message instanceof EPOSBroadcast) {
            EPOSBroadcast broadcast = (EPOSBroadcast) message;
            current.global.set(broadcast.globalPlan);

            history.put(currentPhase, current);

            this.robustness = fitnessFunction.getRobustness(current.global, costSignal, current);

            if (!this.isLeaf()) {
                for (Finger child : children) {
                    getPeer().sendMessage(child.getNetworkAddress(), broadcast);
                }
            }
        }
    }

    @Override
    void measure(MeasurementLog log, int epochNumber) {
        if (epochNumber == 2) {
            if (this.isRoot()) {
                log.log(epochNumber, current.global, 1.0);
                log.log(epochNumber, EPOSMeasures.PLAN_SIZE, current.global.getNumberOfStates());
                log.log(epochNumber, EPOSMeasures.ROBUSTNESS, robustness);
            }
            log.log(epochNumber, current.selectedLocalPlan, 1.0);
            log.log(epochNumber, EPOSMeasures.DISCOMFORT, current.selectedLocalPlan.getDiscomfort());
            writeGraphData(epochNumber);
        }
    }

    private void writeGraphData(int epochNumber) {
        System.out.println(getPeer().getNetworkAddress().toString() + ","
                + ((parent != null) ? parent.getNetworkAddress().toString() : "-") + ","
                + findSelectedPlan());
    }

    private int findSelectedPlan() {
        for (int i = 0; i < possiblePlans.size(); i++) {
            if (possiblePlans.get(i).equals(current.selectedLocalPlan)) {
                return i;
            }
        }
        return -1;
    }
}
