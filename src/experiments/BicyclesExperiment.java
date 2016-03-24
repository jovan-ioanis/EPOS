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
package experiments;

import agents.fitnessFunction.iterative.*;
import agents.*;
import agents.fitnessFunction.*;
import dsutil.generic.RankPriority;
import dsutil.protopeer.services.topology.trees.DescriptorType;
import dsutil.protopeer.services.topology.trees.TreeType;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.joda.time.DateTime;
import protopeer.Experiment;
import protopeer.measurement.MeasurementLog;

/**
 * @author Peter
 */
public class BicyclesExperiment extends ExperimentLauncher {

    private final static int numExperiments = 5;
    private FitnessFunction fitnessFunction;
    private int numUser;

    public BicyclesExperiment(int numUser) {
        this.numUser = numUser;
    }

    private static MeasurementLog log = null;

    public static void main(String[] args) {
        long t0 = System.currentTimeMillis();
        
        List<FitnessFunction> comparedFunctions = new ArrayList<>();
        comparedFunctions.add(new IterMinVarGmA(new Factor1OverLayer(), new SumCombinator()));
        /*
        comparedFunctions.add(new IterMinVarGmA(new Factor1OverN(), new SumCombinator()));
        comparedFunctions.add(new IterMinVarGmA(new Factor1OverLayer(), new SumCombinator()));
        comparedFunctions.add(new IterMinVarGmA(new FactorMOverN(), new SumCombinator()));
        comparedFunctions.add(new IterMinVarGmA(new FactorDepthOverN(), new SumCombinator()));
        comparedFunctions.add(new IterMinVarGmA(new FactorNormalizeStd(), new SumCombinator()));
        /*
        comparedFunctions.add(new IterMinVarG(new Factor1OverN(), new WeightedSumCombinator()));
        comparedFunctions.add(new IterMinVarG(new Factor1OverLayer(), new WeightedSumCombinator()));
        comparedFunctions.add(new IterMinVarG(new FactorMOverN(), new WeightedSumCombinator()));
        comparedFunctions.add(new IterMinVarG(new FactorDepthOverN(), new WeightedSumCombinator()));
        comparedFunctions.add(new IterMinVarG(new FactorNormalizeStd(), new WeightedSumCombinator()));
        /*
        comparedFunctions.add(new IterMinVarGmT(new Factor1OverN(), new SumCombinator()));
        comparedFunctions.add(new IterMinVarGmT(new Factor1OverLayer(), new SumCombinator()));
        comparedFunctions.add(new IterMinVarGmT(new FactorMOverN(), new SumCombinator()));
        comparedFunctions.add(new IterMinVarGmT(new FactorDepthOverN(), new SumCombinator()));
        comparedFunctions.add(new IterMinVarGmT(new FactorNormalizeStd(), new SumCombinator()));
        /*
        comparedFunctions.add(new IterMinVarHGmA(new Factor1OverN(), new Factor1(), new SumCombinator(), new MostRecentCombinator()));
        comparedFunctions.add(new IterMinVarHGmA(new Factor1OverLayer(), new Factor1(), new SumCombinator(), new MostRecentCombinator()));
        comparedFunctions.add(new IterMinVarHGmA(new FactorMOverN(), new Factor1(), new SumCombinator(), new MostRecentCombinator()));
        comparedFunctions.add(new IterMinVarHGmA(new FactorDepthOverN(), new Factor1(), new SumCombinator(), new MostRecentCombinator()));
        comparedFunctions.add(new IterMinVarHGmA(new FactorNormalizeStd(), new Factor1(), new SumCombinator(), new MostRecentCombinator()));
        /**/
        comparedFunctions.add(new IterMaxMatchGmA(new FactorMOverNmM(), new SumCombinator()));
        comparedFunctions.add(new IterMaxMatchG(new Factor1OverN(), new SumCombinator()));
        /**/
        
        List<Integer> comparedNumUser = new ArrayList<>();
        //comparedNumUser.add(2300); // max user
        //comparedNumUser.add(1000);
        //comparedNumUser.add(50);
        comparedNumUser.add(2300);

        List<String> names = new ArrayList<>();
        List<MeasurementLog> logs = new ArrayList<>();

        new File("output-data").mkdir();
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("output-data/log.log"))) {
            for (int numUser : comparedNumUser) {
                for (FitnessFunction fitnessFunction : comparedFunctions) {
                    BicyclesExperiment launcher = new BicyclesExperiment(numUser);
                    launcher.fitnessFunction = fitnessFunction;
                    launcher.treeInstances = numExperiments;
                    launcher.runDuration = 4;
                    launcher.run();

                    names.add(launcher.getName());
                    logs.add(log);
                    oos.writeUTF(launcher.getName());
                    oos.writeObject(log);
                    log = null;
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        IEPOSEvaluator.evaluateLogs(names, logs);
        
        long t1 = System.currentTimeMillis();
        System.out.println((t1-t0)/1000);
    }

    @Override
    public EPOSExperiment createExperiment(int num) {
        EPOSExperiment experiment = new EPOSExperiment(getName(num),
                RankPriority.HIGH_RANK, DescriptorType.RANK, TreeType.SORTED_HtL,
                "input-data/bicycle", "user_plans_unique_8to10_force_trips", "cost.txt",
                //"input-data/bicycle", "user_plans_hybrid5_8to10_force_trips", "cost.txt",
                //"input-data/Archive", "1.1", null,
                "3BR" + num, DateTime.parse("0001-01-01"),
                fitnessFunction, DateTime.parse("0001-01-01"), 5, 3, numUser,
                //new IEPOSAgent.Factory());
                new IGreedyAgent.Factory());
                //new OPTAgent.Factory());
        return experiment;
    }

    @Override
    public void evaluateRun() {
        if (log == null) {
            log = Experiment.getSingleton().getRootMeasurementLog();
        } else {
            log.mergeWith(Experiment.getSingleton().getRootMeasurementLog());
        }
    }

    private String getName() {
        return fitnessFunction.toString();
    }

    private String getName(int num) {
        return getName() + " " + num;
    }
}
