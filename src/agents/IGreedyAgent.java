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

import agents.plan.AggregatePlan;
import agents.plan.GlobalPlan;
import agents.fitnessFunction.IterativeFitnessFunction;
import agents.fitnessFunction.costFunction.CostFunction;
import agents.plan.Plan;
import agents.plan.PossiblePlan;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import messages.IGreedyDown;
import messages.IGreedyUp;
import org.joda.time.DateTime;
import agents.dataset.AgentDataset;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Evangelos
 */
public class IGreedyAgent extends IterativeAgentTemplate<IGreedyUp, IGreedyDown> {

    private boolean outputMovie;
    private boolean outputDetail = false;
    private PrintStream out;
    private PrintStream rootOut;

    private final int historySize;
    private final TreeMap<DateTime, AgentPlans> history = new TreeMap<>();

    private int numNodes;
    private int numNodesSubtree;
    private int layer;
    private double avgNumChildren;

    private IterativeFitnessFunction fitnessFunctionPrototype;
    private IterativeFitnessFunction fitnessFunction;

    private Plan costSignal;
    private AgentPlans current = new AgentPlans();
    private AgentPlans previous = new AgentPlans();
    private AgentPlans prevAggregate = new AgentPlans();
    private AgentPlans historic;

    private Double rampUpRate;

    private List<Integer> selectedCombination = new ArrayList<>();
    private LocalSearch localSearch;

    public static class Factory extends AgentFactory {

        @Override
        public Agent create(int id, AgentDataset dataSource, String treeStamp, File outFolder, DateTime initialPhase, DateTime previousPhase, Plan costSignal, int historySize) {
            return new IGreedyAgent(id, dataSource, treeStamp, outFolder, (IterativeFitnessFunction) fitnessFunction, initialPhase, previousPhase, costSignal, historySize, numIterations, localSearch, outputMovie, getMeasures(), getLocalMeasures(), rampUpRate, inMemory);
        }

        @Override
        public String toString() {
            return "IGreedy";
        }
    }

    public IGreedyAgent(int id, AgentDataset dataSource, String treeStamp, File outFolder, IterativeFitnessFunction fitnessFunction, DateTime initialPhase, DateTime previousPhase, Plan costSignal, int historySize, int numIterations, LocalSearch localSearch, boolean outputMovie, List<CostFunction> measures, List<CostFunction> localMeasures, Double rampUpRate, boolean inMemory) {
        super(id, dataSource, treeStamp, outFolder, initialPhase, numIterations, measures, localMeasures, inMemory);
        this.fitnessFunctionPrototype = fitnessFunction;
        this.historySize = historySize;
        this.costSignal = costSignal;
        this.localSearch = localSearch == null ? null : localSearch.clone();
        this.outputMovie = outputMovie;
        this.rampUpRate = rampUpRate;
    }

    @Override
    void initPhase() {
        if (this.history.size() > this.historySize) {
            this.history.remove(this.history.firstKey());
        }
        prevAggregate.reset();
        previous.reset();

        if (previousPhase != null) {
            this.historic = history.get(previousPhase);
        } else {
            this.historic = null;
        }
        numNodes = -1;
        fitnessFunction = fitnessFunctionPrototype.clone();

        if (outputDetail) {
            try {
                new File("output-data/detail").mkdir();
                out = new PrintStream("output-data/detail/" + getPeer().getIndexNumber() + ".txt");
                if (isRoot()) {
                    rootOut = new PrintStream("output-data/detail/root.txt");
                }
            } catch (FileNotFoundException ex) {
                Logger.getLogger(IGreedyAgent.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    void initIteration() {
        current = new AgentPlans();
        current.global = new GlobalPlan(this);
        current.aggregate = new AggregatePlan(this);
        current.selectedLocalPlan = new PossiblePlan(this);
        current.selectedPlan = new PossiblePlan(this);
        numNodesSubtree = 1;
        avgNumChildren = children.size();
        layer = 0;
    }

    @Override
    public IGreedyUp up(List<IGreedyUp> msgs) {
        Plan childAggregate = new AggregatePlan(this);

        if (localSearch != null) {
            List<Plan> childAggregates = new ArrayList<>();
            for (IGreedyUp msg : msgs) {
                childAggregates.add(msg.aggregate);
            }
            childAggregate = localSearch.calcAggregate(this, childAggregates, previous.global, costSignal);
        } else {
            for (IGreedyUp msg : msgs) {
                childAggregate.add(msg.aggregate);
            }
        }

        for (IGreedyUp msg : msgs) {
            numNodesSubtree += msg.numNodes;
        }

        // select best combination
        List<Plan> subSelectablePlans = possiblePlans;
        if (rampUpRate != null && !isRoot()) {
            Random r = new Random(getPeer().getIndexNumber());
            int numPlans = (int) Math.floor(iteration * rampUpRate + 2 + r.nextDouble() * 2 - 1);
            subSelectablePlans = possiblePlans.subList(0, Math.min(numPlans, possiblePlans.size()));
        }
        int selectedPlan = fitnessFunction.select(this, childAggregate, subSelectablePlans, costSignal, numNodes, numNodesSubtree, layer, avgNumChildren, iteration);
        current.selectedLocalPlan = subSelectablePlans.get(selectedPlan);
        measureLocal(current.selectedLocalPlan, costSignal, selectedPlan, possiblePlans.size());

        current.selectedPlan = current.selectedLocalPlan;
        current.aggregate.set(childAggregate);
        current.aggregate.add(current.selectedLocalPlan);

        if (outputDetail) {
            out.println(iteration + ": " + current.selectedLocalPlan);
        }

        IGreedyUp msg = new IGreedyUp();
        msg.aggregate = current.aggregate;
        msg.numNodes = numNodesSubtree;
        return msg;
    }

    @Override
    public IGreedyDown atRoot(IGreedyUp rootMsg) {
        current.global.set(current.aggregate);

        this.history.put(this.currentPhase, current);

        // Log + output
        if (outputMovie) {
            if (iteration == 0) {
                System.out.println("C(1:" + costSignal.getNumberOfStates() + "," + (iteration + 1) + ")=" + costSignal + ";");
            }
            System.out.println("D(1:" + costSignal.getNumberOfStates() + "," + (iteration + 1) + ")=" + current.global + ";");

            Plan c = costSignal.clone();
            if (iteration > 0) {
                c.multiply(1 + iteration);
                c.add(prevAggregate.global);
            }
            System.out.println("T(1:" + costSignal.getNumberOfStates() + "," + (iteration + 1) + ")=" + c + ";");
            if (outputDetail) {
                rootOut.println(iteration + ": " + Math.sqrt(measures.get(0).calcCost(current.global, costSignal, 0, 0)) + "," + current.global + "," + c);
            }
        } else {
            if (iteration % 10 == 9) {
                System.out.print("%");
            }
            if (iteration % 100 == 99) {
                System.out.print(" ");
            }
            if (iteration + 1 == numIterations) {
                System.out.println("");
            }
        }

        IGreedyDown msg = new IGreedyDown(current.global, numNodesSubtree, 0, 0);
        return msg;
    }

    @Override
    public List<IGreedyDown> down(IGreedyDown parent) {
        current.global.set(parent.globalPlan);
        if (parent.discard) {
            current.aggregate = previous.aggregate;
            current.selectedLocalPlan = previous.selectedLocalPlan;
            current.selectedPlan = previous.selectedPlan;
        }
        numNodes = parent.numNodes;
        layer = parent.hops;
        avgNumChildren = parent.sumChildren / Math.max(0.1, (double) parent.hops);

        measureGlobal(current.global, costSignal);
        fitnessFunction.updatePrevious(prevAggregate, current, costSignal, iteration);
        previous = current;

        List<IGreedyDown> msgs = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            IGreedyDown msg = new IGreedyDown(parent.globalPlan, parent.numNodes, parent.hops + 1, parent.sumChildren + children.size());
            if (localSearch != null) {
                msg.discard = parent.discard || !localSearch.getSelected().get(i);
            }
            msgs.add(msg);
        }
        return msgs;
    }
}
