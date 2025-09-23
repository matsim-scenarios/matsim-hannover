#!/usr/bin/env python
# -*- coding: utf-8 -*-

import geopandas as gpd
import numpy as np
import pandas as pd
from matsim.scenariogen.data import run_create_ref_data
from matsim.scenariogen.data.preparation import calc_needed_short_distance_trips, cut

CRS = "EPSG:25832"


def person_filter(df):
    """ Filter person that are relevant for the calibration."""

    df = df.dropna(subset=["geom"])
    df = gpd.GeoDataFrame(df, geometry=gpd.GeoSeries.from_wkt(df.geom, crs="EPSG:4326").to_crs(CRS))
    df = gpd.sjoin(df, region, how="inner", predicate="intersects")

    # Groups will be shown on the dashboard
    df["age"] = cut(df.age, [0, 12, 18, 25, 35, 66, np.inf])

    # Only weekdays Monday through Thursday are considered, with persons present in their home region
    return df[df.present_on_day & (df.reporting_day <= 4)]


def trip_filter(df):
    # All modes, expect for "other" are considered
    return df[df.main_mode != "other"]


if __name__ == "__main__":

    # Defines the study area
    region = gpd.read_file("../../../../../shared-svn/projects/umex-hope/data/shp/network-area-utm32n.shp").to_crs(CRS)

    # This contains the path to the MiD 2017 data with the highest resolution
    # See https://daten.clearingstelle-verkehr.de/279/ for more information, the data is not included in this repository
    r = run_create_ref_data.create(
        # this is the MID2017 dataset, it is not available in svn as it has to be encrypted
        "A:/B3_Lokal-Datensatzpaket/CSV",
        person_filter, trip_filter,
        run_create_ref_data.InvalidHandling.REMOVE_TRIPS,
        ref_groups=["age", "economic_status"]
    )

    print("Filtered %s persons" % len(r.persons))
    print("Filtered %s trips" % len(r.trips))

    print(r.share)

    print(r.trips.groupby("dist_group").agg(n=("main_mode", "count")) / len(r.trips))

    # Calculate the number of short distance trips that are missing in the simulated data
    # This function required that one run with 0 iterations has been performed beforehand

    # this says 10pct, but is 100pct population, number of person: 1134991 (without freight agents)
    # same for trips
    sim_persons = pd.read_csv("../../../output/output-oberlausitz-dresden-10pct/oberlausitz-dresden-10pct.output_persons.csv.gz",
                              delimiter=";", dtype={"person": "str"})
    sim_persons = sim_persons[sim_persons.subpopulation == "person"]
    sim_persons = gpd.GeoDataFrame(sim_persons,
                                   geometry=gpd.points_from_xy(sim_persons.home_x, sim_persons.home_y)).set_crs(CRS)

    sim_persons = gpd.sjoin(sim_persons, region, how="inner", predicate="intersects")

    sim = pd.read_csv("../../../output/output-oberlausitz-dresden-10pct/oberlausitz-dresden-10pct.output_trips.csv.gz",
                      delimiter=";", dtype={"person": "str"})

    sim = pd.merge(sim, sim_persons, how="inner", left_on="person", right_on="person", validate="many_to_one")

    share, add_trips = calc_needed_short_distance_trips(r.trips, sim, max_dist=700)
    print("Short distance trip missing: ", add_trips)
