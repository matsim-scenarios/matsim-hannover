package org.matsim.run.scenarios;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import com.google.common.collect.Sets;
import com.google.inject.multibindings.Multibinder;
import org.jetbrains.annotations.Nullable;
import org.matsim.analysis.personMoney.PersonMoneyEventsAnalysisModule;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
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
import org.matsim.contrib.vsp.pt.fare.DistanceBasedPtFareParams;
import org.matsim.contrib.vsp.pt.fare.FareZoneBasedPtFareParams;
import org.matsim.contrib.vsp.pt.fare.PtFareConfigGroup;
import org.matsim.contrib.vsp.pt.fare.PtFareModule;
import org.matsim.contrib.vsp.scenario.SnzActivities;
import org.matsim.contrib.vsp.scoring.RideScoringParamsFromCarParams;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.turnRestrictions.DisallowedNextLinks;
import org.matsim.core.replanning.annealing.ReplanningAnnealerConfigGroup;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.dashboards.HannoverDashboardProvider;
import org.matsim.prepare.CreateFacilitiesFromPopulation;
import org.matsim.prepare.PrepareNetwork;
import org.matsim.prepare.PreparePopulation;
import org.matsim.simwrapper.DashboardProvider;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import org.matsim.simwrapper.SimWrapperModule;
import org.matsim.smallScaleCommercialTrafficGeneration.GenerateSmallScaleCommercialTrafficDemand;
import org.matsim.smallScaleCommercialTrafficGeneration.prepare.CreateDataDistributionOfStructureData;
import org.matsim.utils.HannoverUtils;
import picocli.CommandLine;
import playground.vsp.scoring.IncomeDependentUtilityOfMoneyPersonScoringParameters;

import java.util.HashSet;
import java.util.Set;

import static org.matsim.utils.HannoverUtils.*;

@CommandLine.Command(header = ":: Open Hannover Scenario ::", version = HannoverScenario.VERSION, mixinStandardHelpOptions = true)
@MATSimApplication.Prepare({
		CreateNetworkFromSumo.class, CreateTransitScheduleFromGtfs.class, TrajectoryToPlans.class, GenerateShortDistanceTrips.class,
		MergePopulations.class, ExtractRelevantFreightTrips.class, DownSamplePopulation.class, ExtractHomeCoordinates.class,
		CreateLandUseShp.class, ResolveGridCoordinates.class, FixSubtourModes.class, AdjustActivityToLinkDistances.class, XYToLinks.class,
		CleanNetwork.class, CreateCountsFromBAStData.class, PreparePopulation.class, SplitActivityTypesDuration.class, CreateFacilitiesFromPopulation.class,
		GenerateSmallScaleCommercialTrafficDemand.class, CreateDataDistributionOfStructureData.class
})
@MATSimApplication.Analysis({
		LinkStats.class, CheckPopulation.class
})

public class HannoverScenario extends MATSimApplication {

	static final String VERSION = "v1.0";

	@CommandLine.Mixin
	private final SampleOptions sample = new SampleOptions(100, 25, 10, 1);
	@CommandLine.Option(names = "--emissions", defaultValue = "ENABLED", description = "Define if emission analysis should be performed or not.")
	HannoverUtils.FunctionalityHandling emissions;
	@CommandLine.Option(names = "--explicit-walk-intermodality", defaultValue = "ENABLED", description = "Define if explicit walk intermodality parameter to/from pt should be set or not (use default).")
	static HannoverUtils.FunctionalityHandling explicitWalkIntermodality;
	@CommandLine.Option(names = "--ride-alpha", defaultValue = "2.0", description = "Alpha value for ride. It is multiplied (+1) with the distance and tt based utilities and cost for car.")
	Double rideAlpha;

	public HannoverScenario(@Nullable Config config) {
		super(config);
	}

	public HannoverScenario() {
		super(String.format("input/%s/hannover-%s-10pct.config.xml", VERSION, VERSION));
	}

	public static void main(String[] args) {
		MATSimApplication.run(HannoverScenario.class, args);
	}

	@Nullable
	@Override
	protected Config prepareConfig(Config config) {

		// Add all activity types with time bins
		SnzActivities.addScoringParams(config);

		//		add simwrapper config module
		SimWrapperConfigGroup simWrapper = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);

		simWrapper.defaultParams().setContext("");
		simWrapper.defaultParams().setMapCenter("9.7, 52.4");
		simWrapper.defaultParams().setMapZoomLevel(6.8);
//		the tarifzone shp file basically is a hannover shp file with fare prices as additional information
		simWrapper.defaultParams().setShp(String.format("hannover-tarifzone-A/%s-hannover-tarifzone-A-utm32n.shp", VERSION));

		if (sample.isSet()){
			config.controller().setOutputDirectory(sample.adjustName(config.controller().getOutputDirectory()));
			config.plans().setInputFile(sample.adjustName(config.plans().getInputFile()));
			config.controller().setRunId(sample.adjustName(config.controller().getRunId()));

			config.qsim().setFlowCapFactor(sample.getSample());
			config.qsim().setStorageCapFactor(sample.getSample());
			config.counts().setCountsScaleFactor(sample.getSample());
			simWrapper.setSampleSize(sample.getSample());
		}

//		We would like to "switch off" the usage of LastestActivityEndTime, but this is not possible with just setting the value.
//		Option 1: set latestActivityEndTime = 0; Then all mutated act end times would become 0 because in class MutateActivityTimeAllocation the following line is used to set the act end time
//		double newEndTime = Math.min(mutateTime(endTime, mutationRange),this.latestActivityEndTime);
//		Option 2: set latestActivityEndTIme = 36h = qsim endtime, but doesn't this cause a postponing of the act end time until we reach 36h ulimatively? This is not what we want I think..
//		TODO: try this out in Dresden and apply here after
//		config.timeAllocationMutator().setLatestActivityEndTime(String.valueOf(config.qsim().getEndTime().seconds()));
//		if mutateAroundInitialEndTImeOnly = true we potentially trap acts with start and/or end outside of act type opening times, so we switch it off here.
		config.timeAllocationMutator().setMutateAroundInitialEndTimeOnly(false);
		//		mutation should not affect act duration because otherwise short acts can end up with max_dur=0s.
		config.timeAllocationMutator().setAffectingDuration(false);

		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.abort);

		//		performing set to 6.0 after calibration task force july 24
		double performing = 6.0;
		ScoringConfigGroup scoringConfigGroup = config.scoring();
		scoringConfigGroup.setPerforming_utils_hr(performing);
		scoringConfigGroup.setWriteExperiencedPlans(true);
		scoringConfigGroup.setPathSizeLogitBeta(0.);

//		prepare config for usage of longDistanceFreight and small scale commercial traffic
		prepareCommercialTrafficConfig(config);

//		set ride scoring params dependent from car params
//		2.0 + 1.0 = alpha + 1
//		ride cost = alpha * car cost
//		ride marg utility of traveling = (alpha + 1) * marg utility travelling car + alpha * beta perf
//		double alpha = 2;
		RideScoringParamsFromCarParams.setRideScoringParamsBasedOnCarParams(scoringConfigGroup, rideAlpha);

		config.qsim().setUsingTravelTimeCheckInTeleportation(true);
		config.qsim().setUsePersonIdForMissingVehicleId(false);
		config.routing().setAccessEgressType(RoutingConfigGroup.AccessEgressType.accessEgressModeToLink);

//		configure annealing params
		config.replanningAnnealer().setActivateAnnealingModule(true);
		ReplanningAnnealerConfigGroup.AnnealingVariable annealingVar = new ReplanningAnnealerConfigGroup.AnnealingVariable();
		annealingVar.setAnnealType(ReplanningAnnealerConfigGroup.AnnealOption.sigmoid);
		annealingVar.setEndValue(0.01);
		annealingVar.setHalfLife(0.5);
		annealingVar.setShapeFactor(0.01);
		annealingVar.setStartValue(0.45);
		annealingVar.setDefaultSubpopulation("person");
		config.replanningAnnealer().addAnnealingVariable(annealingVar);

		//set pt fare calc model to fareZoneBased = fare of hannover tarifzone A are paid for trips within fare zone
//		every other trip: Deutschlandtarif
//		for more info see PTFareModule / ChainedPtFareCalculator classes in vsp contrib
		PtFareConfigGroup ptFareConfigGroup = ConfigUtils.addOrGetModule(config, PtFareConfigGroup.class);


//		pt fare for single ticket in tarifzone A hannover is 3.60 eu in 2025.
//		see: https://www.uestra.de/fahrkarten-preise/fahrkartensortiment/fahrkartendetails/einzelkarte/
//		pt single ticket fare 2021 = fare 2025 / inflationFactor (see below) = 3.6eu / 1.18 ~ 3.1 eu
//		1.18 is for 2021 to 2025, but as of now (dec 25) there is no value for 2025, so we assume the same inflation from 24 to 25 as for 23 to 24 (2.2)
//		fare prices for tarifzone A aka Hannover have to be set in shp file.
		FareZoneBasedPtFareParams tarifzoneA = new FareZoneBasedPtFareParams();
		tarifzoneA.setTransactionPartner("Hannover Tarifzone A");
		tarifzoneA.setDescription("Hannover Tarifzone A");
		tarifzoneA.setOrder(1);
		tarifzoneA.setFareZoneShp(String.format("./hannover-tarifzone-A/%s-hannover-tarifzone-A-utm32n.shp", VERSION));

		DistanceBasedPtFareParams germany = DistanceBasedPtFareParams.GERMAN_WIDE_FARE_2024;
		germany.setTransactionPartner("Deutschlandtarif");
		germany.setDescription("Deutschlandtarif");
		germany.setOrder(2);

//		apply inflation factor to distance based fare. fare values are from 10.12.23 / for the whole of 2024.
//		car cost in this scenario is projected to 2021. Hence, we deflate the pt cost to 2021
//		according to https://www-genesis.destatis.de/genesis/online?sequenz=tabelleErgebnis&selectionname=61111-0001&startjahr=1991#abreadcrumb (same source as for car cost inflation in google drive)
//		Verbraucherpreisindex 2021 to 2024: 103.1 to 119.3 = 16.2 = inflationFactor of 1.16
//		pt distance cost 2021: cost = (m*distance + b) / inflationFactor = m * inflationFactor * distance + b * inflationFactor
//		ergo: slope2021 = slope2024/inflationFactor and intercept2021 = intercept2024/inflationFactor
		double inflationFactor = 1.16;
		DistanceBasedPtFareParams.DistanceClassLinearFareFunctionParams below100km = germany.getOrCreateDistanceClassFareParams(100_000.);
		below100km.setFareSlope(below100km.getFareSlope() / inflationFactor);
		below100km.setFareIntercept(below100km.getFareIntercept() / inflationFactor);

		DistanceBasedPtFareParams.DistanceClassLinearFareFunctionParams greaterThan100km = germany.getOrCreateDistanceClassFareParams(Double.POSITIVE_INFINITY);
		greaterThan100km.setFareSlope(greaterThan100km.getFareSlope() / inflationFactor);
		greaterThan100km.setFareIntercept(greaterThan100km.getFareIntercept() / inflationFactor);

		ptFareConfigGroup.addParameterSet(tarifzoneA);
		ptFareConfigGroup.addParameterSet(germany);

		if (explicitWalkIntermodality == HannoverUtils.FunctionalityHandling.ENABLED) {
			setExplicitIntermodalityParamsForWalkToPt(ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class));
		}

		if (emissions == HannoverUtils.FunctionalityHandling.ENABLED) {
//		set hbefa input files for emission analysis
			setEmissionsConfigs(config);
		}
		return config;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {
		//		add freight modes of HannoverUtils to network.
//		this happens in the makefile pipeline already, but we do it here anyways, in case somebody uses a preliminary network.
		PrepareNetwork.prepareFreightNetwork(scenario.getNetwork());

//		remove disallowed links. The disallowed links cause many problems and (usually) are not useful in our rather macroscopic view on transport systems.
		for (Link link : scenario.getNetwork().getLinks().values()) {
			DisallowedNextLinks disallowed = NetworkUtils.getDisallowedNextLinks(link);
			if (disallowed != null) {
				link.getAllowedModes().forEach(disallowed::removeDisallowedLinkSequences);
				if (disallowed.isEmpty()) {
					NetworkUtils.removeDisallowedNextLinks(link);
				}
			}
		}

		if (emissions == FunctionalityHandling.ENABLED) {
//			prepare hbefa link attributes + make link.getType() handable for OsmHbefaMapping
//			this also happens in makefile pipeline. integrating it here for same reason as above.
			PrepareNetwork.prepareEmissionsAttributes(scenario.getNetwork());
//			prepare vehicle types for emission analysis
			prepareVehicleTypesForEmissionAnalysis(scenario);
		}
	}

	@Override
	protected void prepareControler(Controler controler) {
		//analyse PersonMoneyEvents
		controler.addOverridingModule(new PersonMoneyEventsAnalysisModule());

		controler.addOverridingModule(new SimWrapperModule());

		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				install(new PtFareModule());
				bind(ScoringParametersForPerson.class).to(IncomeDependentUtilityOfMoneyPersonScoringParameters.class).asEagerSingleton();

				addTravelTimeBinding(TransportMode.ride).to(carTravelTime());
				addTravelDisutilityFactoryBinding(TransportMode.ride).to(carTravelDisutilityFactoryKey());
//				this binds the DresdenDashboardProvider with guice instead of resources/services/.../file.
//				This is way more convenient imho.
				Multibinder.newSetBinder(binder(), DashboardProvider.class).addBinding().to(HannoverDashboardProvider.class);
			}
		});
	}

	/**
	 * Prepare the config for commercial traffic.
	 */
	private static void prepareCommercialTrafficConfig(Config config) {

		getFreightModes().forEach(mode -> {
			ScoringConfigGroup.ModeParams thisModeParams = new ScoringConfigGroup.ModeParams(mode);
			config.scoring().addModeParams(thisModeParams);
		});

		Set<String> qsimModes = new HashSet<>(config.qsim().getMainModes());
		config.qsim().setMainModes(Sets.union(qsimModes, getFreightModes()));

		Set<String> networkModes = new HashSet<>(config.routing().getNetworkModes());
		config.routing().setNetworkModes(Sets.union(networkModes, getFreightModes()));

		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("commercial_start").setTypicalDuration(30 * 60.));
		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("commercial_end").setTypicalDuration(30 * 60.));
		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("service").setTypicalDuration(30 * 60.));
		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("start").setTypicalDuration(30 * 60.));
		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("end").setTypicalDuration(30 * 60.));
		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("freight_start").setTypicalDuration(30 * 60.));
		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("freight_end").setTypicalDuration(30 * 60.));

//		replanning strategies for small scale commercial traffic
		for (String subpopulation : getSmallScaleComSubpops()) {
			config.replanning().addStrategySettings(
				new ReplanningConfigGroup.StrategySettings()
					.setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta)
					.setWeight(0.85)
					.setSubpopulation(subpopulation)
			);

			config.replanning().addStrategySettings(
				new ReplanningConfigGroup.StrategySettings()
					.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ReRoute)
					.setWeight(0.1)
					.setSubpopulation(subpopulation)
			);
		}

//		replanning strategies for longDistanceFreight
		config.replanning().addStrategySettings(
			new ReplanningConfigGroup.StrategySettings()
				.setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta)
				.setWeight(0.95)
				.setSubpopulation(LONG_DIST_FREIGHT_SUBPOP)
		);
		config.replanning().addStrategySettings(
			new ReplanningConfigGroup.StrategySettings()
				.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ReRoute)
				.setWeight(0.05)
				.setSubpopulation(LONG_DIST_FREIGHT_SUBPOP)
		);

//		analyze travel times for all qsim main modes
		config.travelTimeCalculator().setAnalyzedModes(Sets.union(qsimModes, getFreightModes()));

	}
}
