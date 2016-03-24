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
package agents.fitnessFunction.iterative;

import agents.AgentPlans;
import agents.plan.Plan;
import java.util.List;

/**
 *
 * @author Peter
 */
public class FactorMOverNmM implements Factor {

    @Override
    public double calcFactor(Plan factorTargetPlan, Plan childAggregatePlan, List<Plan> combinationalPlans, Plan pattern, AgentPlans previous, int numNodes, int numNodesSubtree, int layer, double avgChildren) {
        double factor = numNodesSubtree/((double)numNodes-numNodesSubtree);
        if(!Double.isFinite(factor)) {
            factor = 1;
        }
        return factor;
    }
    
    @Override
    public String toString() {
        return "m/n";
    }
}
