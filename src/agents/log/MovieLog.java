/*
 * Copyright (C) 2016 Evangelos Pournaras
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
package agents.log;

import agents.fitnessFunction.costFunction.IterativeCostFunction;
import agents.fitnessFunction.costFunction.StdDevCostFunction;
import agents.plan.Plan;
import java.io.PrintStream;
import protopeer.measurement.MeasurementLog;

/**
 *
 * @author Peter
 */
public class MovieLog extends FileLog {

    private Plan costSignal;
    private Plan iterativeCost;

    private IterativeCostFunction measure = new StdDevCostFunction();

    public void init(int agentId) {
    }

    public void initRoot(Plan costSignal) {
        this.costSignal = costSignal;
    }

    public void log(MeasurementLog log, int epoch, int iteration, Plan selectedLocalPlan) {
    }

    public void logRoot(MeasurementLog log, int epoch, int iteration, Plan global) {
        if (iterativeCost != null) {
            iterativeCost = measure.calcGradient(global, costSignal);
        } else {
            iterativeCost = iterativeCost.clone();
            iterativeCost.add(measure.calcGradient(global, costSignal));
        }

        Entry entry = new Entry();
        entry.iteration = iteration;
        entry.costSignal = iteration == 0 ? costSignal : null;
        entry.global = global;
        entry.iterativeCost = iterativeCost;

        log.log(epoch, entry, 0.0);
    }

    @Override
    public void print(MeasurementLog log) {
        PrintStream out = System.out;

        for (Object entryObj : log.getTagsOfType(Entry.class)) {
            Entry entry = (Entry) entryObj;

            if (entry.iteration == 0) {
                out.println("C=" + entry.costSignal + ";");
                out.println("D=zeros(1:" + entry.costSignal.getNumberOfStates() + ",0);");
                out.println("T=zeros(1:" + entry.costSignal.getNumberOfStates() + ",0);");
            }
            out.println("D(:," + (entry.iteration + 1) + ")=" + entry.global + ";");
            out.println("T(:," + (entry.iteration + 1) + ")=" + entry.iterativeCost + ";");
        }
    }

    private class Entry {

        public int iteration;
        public Plan costSignal;
        public Plan global;
        public Plan iterativeCost;
    }
}
