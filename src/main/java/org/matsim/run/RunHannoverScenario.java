package org.matsim.run;

import org.matsim.application.MATSimApplication;
import org.matsim.run.scenarios.HannoverScenario;

public class RunHannoverScenario {
	private RunHannoverScenario() {

	}

	public static void main(String[] args) {
		MATSimApplication.runWithDefaults(HannoverScenario.class, args);
	}
}
