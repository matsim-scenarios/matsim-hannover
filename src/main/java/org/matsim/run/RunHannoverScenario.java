package org.matsim.run;

import org.matsim.application.MATSimApplication;
import org.matsim.run.scenarios.HannoverScenario;

/**
 *  * Run the {@link HannoverScenario} with default configuration.
 */
public final class RunHannoverScenario {
	private RunHannoverScenario() {

	}

	public static void main(String[] args) {
		MATSimApplication.runWithDefaults(HannoverScenario.class, args);
	}
}
