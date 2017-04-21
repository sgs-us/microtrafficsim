package microtrafficsim.core.exfmt.injector.streetgraph;

import microtrafficsim.core.exfmt.Container;
import microtrafficsim.core.exfmt.ExchangeFormat;
import microtrafficsim.core.exfmt.base.ScenarioAreaSet;
import microtrafficsim.core.exfmt.base.ScenarioConfigInfo;
import microtrafficsim.core.exfmt.base.ScenarioMetaInfo;
import microtrafficsim.core.exfmt.base.ScenarioRouteSet;
import microtrafficsim.core.logic.streetgraph.GraphGUID;
import microtrafficsim.core.simulation.scenarios.impl.AreaScenario;


public class AreaScenarioInjector implements ExchangeFormat.Injector<AreaScenario> {

    @Override
    public void inject(ExchangeFormat fmt, ExchangeFormat.Context ctx, Container dst, AreaScenario src) throws Exception {
        Config cfg = fmt.getConfig().getOr(Config.class, Config::getDefault);

        // store meta information
        ScenarioMetaInfo meta = new ScenarioMetaInfo();
        meta.setGraphGUID(GraphGUID.from(src.getGraph()));
        meta.setScenarioType(AreaScenario.class);
        dst.set(meta);

        // store config information
        ScenarioConfigInfo sconfig = new ScenarioConfigInfo();
        // TODO: store scenario-/simulation-config
        dst.set(sconfig);

        // store areas
        ScenarioAreaSet areas = new ScenarioAreaSet();
        areas.addAll(src.getAreas());
        dst.set(areas);

        // store routes
        if (cfg.storeRoutes) {
            ScenarioRouteSet routes = new ScenarioRouteSet();
            // TODO: store complete routes
            dst.set(routes);
        }
    }

    public static class Config extends microtrafficsim.core.exfmt.Config.Entry {
        public boolean storeRoutes;

        public static Config getDefault() {
            Config cfg = new Config();
            cfg.storeRoutes = false;

            return cfg;
        }
    }
}
