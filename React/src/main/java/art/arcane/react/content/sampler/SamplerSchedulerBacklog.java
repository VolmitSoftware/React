/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.content.sampler;

import art.arcane.react.React;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.core.controller.JobController;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

import java.util.List;

public class SamplerSchedulerBacklog extends ReactCachedSampler {
    public static final String ID = "scheduler-backlog";

    public SamplerSchedulerBacklog() {
        super(ID, 250);
    }

    @Override
    public Material getIcon() {
        return Material.COMPARATOR;
    }

    @Override
    public double onSample() {
        JobController controller = React.controller(JobController.class);
        List<Runnable> jobs = controller.getJobs();
        synchronized (jobs) {
            return jobs.size();
        }
    }

    @Override
    public String formattedValue(double t) {
        return Form.f(Math.ceil(t));
    }

    @Override
    public String formattedSuffix(double t) {
        return "BACKLOG";
    }
}
