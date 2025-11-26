package org.matsim.run.scenarios;

import org.jetbrains.annotations.Nullable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.application.MATSimApplication;
import org.matsim.application.analysis.CheckPopulation;
import org.matsim.application.analysis.traffic.LinkStats;
import org.matsim.application.options.SampleOptions;
import org.matsim.application.prepare.CreateLandUseShp;
import org.matsim.application.prepare.counts.CreateCountsFromBAStData;
import org.matsim.application.prepare.longDistanceFreightGER.tripExtraction.ExtractRelevantFreightTrips;
import org.matsim.application.prepare.network.CleanNetwork;
import org.matsim.application.prepare.network.CreateNetworkFromSumo;
import org.matsim.application.prepare.population.*;
import org.matsim.application.prepare.pt.CreateTransitScheduleFromGtfs;
import org.matsim.contrib.vsp.scenario.SnzActivities;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.prepare.CreateFacilitiesFromPopulation;
import org.matsim.prepare.PreparePopulation;
import org.matsim.simwrapper.SimWrapperModule;
import picocli.CommandLine;

@CommandLine.Command(header = ":: Open Hannover Scenario ::", version = HannoverScenario.VERSION, mixinStandardHelpOptions = true)
@MATSimApplication.Prepare({
		CreateNetworkFromSumo.class, CreateTransitScheduleFromGtfs.class, TrajectoryToPlans.class, GenerateShortDistanceTrips.class,
		MergePopulations.class, ExtractRelevantFreightTrips.class, DownSamplePopulation.class, ExtractHomeCoordinates.class,
		CreateLandUseShp.class, ResolveGridCoordinates.class, FixSubtourModes.class, AdjustActivityToLinkDistances.class, XYToLinks.class,
		CleanNetwork.class, CreateCountsFromBAStData.class, PreparePopulation.class, SplitActivityTypesDuration.class, CreateFacilitiesFromPopulation.class
})
@MATSimApplication.Analysis({
		LinkStats.class, CheckPopulation.class
})

public class HannoverScenario extends MATSimApplication {

	static final String VERSION = "1.0";

	@CommandLine.Mixin
	private final SampleOptions sample = new SampleOptions(100, 25, 10, 1);


	public HannoverScenario(@Nullable Config config) {
		super(config);
	}

	public HannoverScenario() {
		super(String.format("input/v%s/hannover-v%s-10pct.config.xml", VERSION, VERSION));
	}

	public static void main(String[] args) {
		MATSimApplication.run(HannoverScenario.class, args);
	}

	@Nullable
	@Override
	protected Config prepareConfig(Config config) {

		// Add all activity types with time bins
		SnzActivities.addScoringParams(config);

		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("car interaction").setTypicalDuration(60));
		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("other").setTypicalDuration(600 * 3));

		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("freight_start").setTypicalDuration(60 * 15));
		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("freight_end").setTypicalDuration(60 * 15));

		config.controller().setOutputDirectory(sample.adjustName(config.controller().getOutputDirectory()));
		config.plans().setInputFile(sample.adjustName(config.plans().getInputFile()));
		config.controller().setRunId(sample.adjustName(config.controller().getRunId()));

		config.qsim().setFlowCapFactor(sample.getSize() / 100.0);
		config.qsim().setStorageCapFactor(sample.getSize() / 100.0);

		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.abort);
		config.routing().setAccessEgressType(RoutingConfigGroup.AccessEgressType.accessEgressModeToLink);

//		TODO: set facility source (like done in CDMX) in cfg file

		// TODO: Config options

		return config;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {


	}

	@Override
	protected void prepareControler(Controler controler) {

		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
//				TODO: check if the following is needed. it should be added in the core already.
//				addControlerListenerBinding().to(ModeChoiceCoverageControlerListener.class);

			}
		});

		controler.addOverridingModule(new SimWrapperModule());

	}
}
