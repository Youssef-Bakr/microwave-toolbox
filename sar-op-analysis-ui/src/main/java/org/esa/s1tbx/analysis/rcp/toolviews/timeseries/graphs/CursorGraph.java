/*
 * Copyright (C) 2015 by Array Systems Computing Inc. http://www.array.ca
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
package org.esa.s1tbx.analysis.rcp.toolviews.timeseries.graphs;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.glayer.support.ImageLayer;
import org.esa.s1tbx.analysis.rcp.toolviews.timeseries.TimeSeriesGraph;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.RasterDataNode;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.core.util.ThreadExecutor;
import org.esa.snap.core.util.ThreadRunnable;
import org.esa.snap.core.util.math.IndexValidator;
import org.esa.snap.core.util.math.Range;

import java.io.IOException;

public class CursorGraph extends TimeSeriesGraph {

    public CursorGraph() {
    }

    @Override
    public String getYName() {
        return "Cursor";
    }

    @Override
    public void readValues(final ImageLayer imageLayer, final GeoPos geoPos, final int level) {
        resetData();

        try {
            final ThreadExecutor executor = new ThreadExecutor();
            for (Band band : selectedBands) {
                final int index = getTimeIndex(band);
                if (index >= 0) {
                    final PixelPos pix = band.getGeoCoding().getPixelPos(geoPos, null);

                    final ThreadRunnable runnable = new ThreadRunnable() {
                        @Override
                        public void process() {
                            //dataPoints[index] = getPixelDouble(band, (int) pix.getX(), (int) pix.getY());
                            dataPoints[index] = ProductUtils.getGeophysicalSampleAsDouble(band, (int) pix.getX(), (int) pix.getY(), 0);

                            if (dataPoints[index] == band.getNoDataValue()) {
                                dataPoints[index] = Double.NaN;
                            }
                        }
                    };
                    executor.execute(runnable);
                }
            }
            executor.complete();
        } catch (Exception e) {
            SystemUtils.LOG.severe("CursorGraph unable to read values " + e.getMessage());
        }

        if(dataPoints != null) {
            Range.computeRangeDouble(dataPoints, IndexValidator.TRUE, dataPointRange, ProgressMonitor.NULL);
        }
    }

    public static double getPixelDouble(final RasterDataNode raster, final int x, final int y) {

        if (raster.hasRasterData()) {
            if (raster.isPixelValid(x, y)) {
                if (raster.isFloatingPointType()) {
                    return raster.getPixelDouble(x, y);
                } else {
                    return raster.getPixelInt(x, y);
                }
            } else {
                return Double.NaN;
            }
        } else {
            try {
                final boolean pixelValid = raster.readValidMask(x, y, 1, 1, new boolean[1])[0];
                if (pixelValid) {
                    if (raster.isFloatingPointType()) {
                        final float[] pixel = raster.readPixels(x, y, 1, 1, new float[1], ProgressMonitor.NULL);
                        return pixel[0];
                    } else {
                        final int[] pixel = raster.readPixels(x, y, 1, 1, new int[1], ProgressMonitor.NULL);
                        return pixel[0];
                    }
                } else {
                    return Double.NaN;
                }
            } catch (IOException e) {
                return Double.NaN;
            }
        }
    }
}