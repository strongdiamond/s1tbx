/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.snap.framework.dataop.dem;

import org.esa.snap.framework.dataop.resamp.Resampling;

import java.io.File;
import java.net.URL;

/**
 * An <code>ElevationModel</code> is used to obtain an elevation above a
 * specified geographical datum for a given geographical position.
 *
 * @author Luis Veci
 * @author Norman Fomferra
 */
public interface ElevationModelDescriptor {

    int DEM_INSTALLED = 1;
    int DEM_INSTALLATION_IN_PROGRESS = 2;

    /**
     * The name of this elevation source, e.g. "GTOPO30"
     *
     * @return a name
     */
    String getName();

    /**
     * Gets the no-data value for this elevation map.
     *
     * @return the  no-data value, e.g. -99999
     */
    float getNoDataValue();

    /**
     * Gets the (raster) width of this elevation map.
     *
     * @return the  width value, e.g. 15000
     */
    int getRasterWidth();

    /**
     * Gets the (raster) height of this elevation map.
     *
     * @return the (raster) height value, e.g. 30000
     */
    int getRasterHeight();

    /**
     * Gets the width of a tile in degrees.
     *
     * @return the width of a tile in degrees, e.g. 15
     */
    int getTileWidthInDegrees();

    /**
     * Gets the width of a tile in pixels.
     *
     * @return the width of a tile in pixels, e.g. 1800
     */
    int getTileWidth();

    /**
     * Gets the number of tiles in x direction
     *
     * @return number of rows
     */
    int getNumXTiles();

    /**
     * Gets the number of tiles in y direction
     *
     * @return number of columns
     */
    int getNumYTiles();

    /**
     * Creates the elevation model instance.
     *
     * @param resampling The resampling method to be used.
     *
     * @return a DEM instance, can be null e.g. if related DEM files are not installed
     *
     * @since BEAM 4.6
     */
    ElevationModel createDem(Resampling resampling);

    /**
     * Returns true if the DEM can be automatically downloaded
     *
     * @return true if auto download
     */
    boolean canBeDownloaded();

    /**
     * Returns the directory (if any) where the DEM files shall be located.
     *
     * @return the DEM file directory
     */
    File getDemInstallDir();

    /**
     * Gets the URL of the DEM (ZIP) archive to be used for on-line installaion.
     *
     * @return the URL of the DEM (ZIP) archive
     */
    URL getDemArchiveUrl();

    /**
     * Asynchronously installs the files required to use the DEM if not already done or in progress.
     *
     * @param uiComponent an optional UI component which serves as parent for progress monitoring
     *
     * @return true, if the DEM is already installed, is being installed or will be installed. False, if an error occurred
     *         or the user canceled the installation
     */
    boolean installDemFiles(Object uiComponent);

    /**
     * Currently not used.
     */
    int getInstallationStatus();

    /**
     * Create the tile filename
     * @param minLat
     * @param minLon
     * @return filename
     */
    String createTileFilename(int minLat, int minLon);
}
