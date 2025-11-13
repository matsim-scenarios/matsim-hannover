package org.matsim.utils;

import java.util.Set;

/**
 * Utils class for Hannover scenario.
 */
public final class HannoverUtils {
	private static final String HEAVY_MODE = "truck40t";
	private static final String MEDIUM_MODE = "truck18t";
	private static final String LIGHT_MODE = "truck8t";

	private HannoverUtils() {

	}

	public static Set<String> getSNZPersonAttrNames() {
		return Set.of(SNZPersonAttributeNames.HH_INCOME, SNZPersonAttributeNames.HOME_REGIOSTAR_17, SNZPersonAttributeNames.HH_SIZE, SNZPersonAttributeNames.AGE,
			SNZPersonAttributeNames.PT_TICKET, SNZPersonAttributeNames.CAR_AVAILABILITY, SNZPersonAttributeNames.GENDER);
	}

	public static Set<String> getFreightModes() {
		return Set.of(HEAVY_MODE, MEDIUM_MODE, LIGHT_MODE);
	}

	/**
	 * original snz attribute names as delivered in personAttributes.xml (shared-svn/projects/umex-hope).
	 */
	public final class SNZPersonAttributeNames {
		private static final String HH_INCOME = "hhIncome";
		private static final String HOME_REGIOSTAR_17 = "homeRegioStaR17";
		private static final String HH_SIZE = "hhSize";
		private static final String AGE = "age";
		private static final String PT_TICKET = "ptTicket";
		private static final String CAR_AVAILABILITY = "carAvailability";
		private static final String GENDER = "gender";

		private SNZPersonAttributeNames() {

		}

		public static String getHhIncomeAttributeName() {
			return HH_INCOME;
		}
		public static String getHhSizeAttributeName() {
			return HH_SIZE;
		}
		public static String getAgeAttributeName() {
			return AGE;
		}
		public static String getGenderAttributeName() {
			return GENDER;
		}
	}

	/**
	 * Helper enum to enable/disable functionalities.
	 */
	public enum FunctionalityHandling {ENABLED, DISABLED}
}
