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
package agents.dataset;

import agents.plan.Plan;
import agents.plan.PossiblePlan;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import org.joda.time.DateTime;

/**
 *
 * @author Peter
 */
public class NoiseAgentDataset implements AgentDataset {

    private final int id;
    private final int numPlans;
    private final int planSize;
    private final double mean;
    private final double std;
    private final long seed;
    private final int nonZero;
    
    private final String config;

    public NoiseAgentDataset(int id, int numPlans, int planSize, double mean, double std, int nonZero, Random r) {
        this.id = id;
        this.numPlans = numPlans;
        this.planSize = planSize;
        this.mean = mean;
        this.std = std;
        this.seed = r.nextLong();
        this.nonZero = nonZero;
        
        this.config = "mean" + mean + "_std" + std;
    }

    @Override
    public List<Plan> getPlans(DateTime phase) {
        Random r = new Random(seed);

        List<Plan> plans = new ArrayList<>();
        for (int i = 0; i < numPlans; i++) {
            plans.add(generatePlan(r));
        }
        return plans;
    }

    @Override
    public List<DateTime> getPhases() {
        return Arrays.asList(new DateTime(0));
    }

    @Override
    public String getId() {
        return "Agent " + id;
    }

    @Override
    public String getConfig() {
        return config;
    }

    @Override
    public int getPlanSize() {
        return planSize;
    }

    private Plan generatePlan(Random r) {
        Plan plan = new PossiblePlan();
        plan.init(planSize);
        
        //double nonZero = 1;
        double setZero = planSize-nonZero;
        Queue<Double> indices = new PriorityQueue<>();
        
        for (int i = 0; i < planSize; i++) {
            indices.add(r.nextInt(planSize*1000) + 0.5*i/(double)planSize);
            plan.setValue(i, (r.nextGaussian() * std + mean));
        }
        
        if(mean == 0) {
            double std = Math.sqrt(plan.variance());

            for(int i = 0; i < setZero; i++) {
                double idx = indices.poll();
                idx = idx - Math.floor(idx);
                int j = (int) Math.round(2*idx*planSize);
                plan.setValue(j, 0);
            }

            plan.multiply(std / Math.sqrt(plan.variance()));
        }
        
        return plan;
    }
}
