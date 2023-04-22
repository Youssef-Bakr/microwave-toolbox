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
package org.csa.rstb.polarimetric.gpf;

import com.bc.ceres.core.ProgressMonitor;
import org.csa.rstb.polarimetric.gpf.specklefilters.*;
import org.csa.rstb.polarimetric.gpf.support.PolarimetricSpeckleFilter;
import eu.esa.sar.commons.polsar.PolBandUtils;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.engine_utilities.datamodel.AbstractMetadata;
import org.esa.snap.engine_utilities.gpf.FilterWindow;
import org.esa.snap.engine_utilities.gpf.InputProductValidator;
import org.esa.snap.engine_utilities.gpf.OperatorUtils;

import java.awt.*;
import java.util.Map;

/**
 * Applies a Polarimetric Speckle Filter to the data (covariance/coherency matrix data)
 */
@OperatorMetadata(alias = "Polarimetric-Speckle-Filter",
        category = "Radar/Polarimetric",
        authors = "Jun Lu, Luis Veci",
        version = "1.0",
        copyright = "Copyright (C) 2014 by Array Systems Computing Inc.",
        description = "Polarimetric Speckle Reduction")
public class PolarimetricSpeckleFilterOp extends Operator {

    @SourceProduct(alias = "source")
    private Product sourceProduct = null;
    @TargetProduct
    private Product targetProduct;

    @Parameter(valueSet = {BOXCAR_SPECKLE_FILTER, IDAN_FILTER, REFINED_LEE_FILTER, LEE_SIGMA_FILTER},
            defaultValue = REFINED_LEE_FILTER, label = "Filter")
    private String filter = REFINED_LEE_FILTER;

    @Parameter(description = "The filter size", interval = "(1, 100]", defaultValue = "5", label = "Filter Size")
    private int filterSize = 5;

    @Parameter(valueSet = {NUM_LOOKS_1, NUM_LOOKS_2, NUM_LOOKS_3, NUM_LOOKS_4},
            defaultValue = NUM_LOOKS_1, label = "Number of Looks")
    private String numLooksStr = NUM_LOOKS_1;

    @Parameter(valueSet = {FilterWindow.SIZE_5x5, FilterWindow.SIZE_7x7, FilterWindow.SIZE_9x9, FilterWindow.SIZE_11x11,
            FilterWindow.SIZE_13x13, FilterWindow.SIZE_15x15, FilterWindow.SIZE_17x17},
            defaultValue = FilterWindow.SIZE_7x7, label = "Window Size")
    private String windowSize = FilterWindow.SIZE_7x7; // window size for all filters

    @Parameter(valueSet = {FilterWindow.SIZE_3x3, FilterWindow.SIZE_5x5}, defaultValue = FilterWindow.SIZE_3x3,
            label = "Point target window Size")
    private String targetWindowSizeStr = FilterWindow.SIZE_3x3; // window size for point target determination in Lee sigma

    @Parameter(description = "The Adaptive Neighbourhood size", interval = "(1, 200]", defaultValue = "50",
            label = "Adaptive Neighbourhood Size")
    private int anSize = 50;

    @Parameter(valueSet = {LeeSigma.SIGMA_50_PERCENT, LeeSigma.SIGMA_60_PERCENT, LeeSigma.SIGMA_70_PERCENT, LeeSigma.SIGMA_80_PERCENT, LeeSigma.SIGMA_90_PERCENT},
            defaultValue = LeeSigma.SIGMA_90_PERCENT, label = "Point target window Size")
    private String sigmaStr = LeeSigma.SIGMA_90_PERCENT; // sigma value in Lee sigma

    @Parameter(description = "The search window size", valueSet = {"3", "5", "7", "9", "11", "13", "15", "17",
            "19", "21", "23", "25"}, defaultValue = "15", label = "Search Window Size")
    private String searchWindowSizeStr = "15";

    @Parameter(description = "The patch size", valueSet = {"3", "5", "7", "9", "11"}, defaultValue = "5",
            label = "Patch Size")
    private String patchSizeStr = "5";

    @Parameter(description = "The scale size", valueSet = {"0", "1", "2"}, defaultValue = "1", label = "Scale Size")
    private String scaleSizeStr = "1";

    private PolBandUtils.PolSourceBand[] srcBandList;

    private int sourceImageWidth = 0;
    private int sourceImageHeight = 0;
    private PolBandUtils.MATRIX sourceProductType = null;

    public static final String BOXCAR_SPECKLE_FILTER = "Box Car Filter";
    public static final String REFINED_LEE_FILTER = "Refined Lee Filter";
    public static final String IDAN_FILTER = "IDAN Filter";
    public static final String LEE_SIGMA_FILTER = "Improved Lee Sigma Filter";
    public static final String NON_LOCAL_FILTER = "Non Local Filter";

    public static final String NUM_LOOKS_1 = "1";
    public static final String NUM_LOOKS_2 = "2";
    public static final String NUM_LOOKS_3 = "3";
    public static final String NUM_LOOKS_4 = "4";

    private PolarimetricSpeckleFilter speckleFilter;
    private static final String PRODUCT_SUFFIX = "_Spk";

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public PolarimetricSpeckleFilterOp() {
    }

    /**
     * Set speckle filter. This function is used by unit test only.
     *
     * @param s The filter name.
     */
    public void SetFilter(final String s) {

        if (s.equals(BOXCAR_SPECKLE_FILTER) || s.equals(IDAN_FILTER) ||
                s.equals(REFINED_LEE_FILTER) || s.equals(LEE_SIGMA_FILTER) || s.equals(NON_LOCAL_FILTER)) {
            filter = s;
        } else {
            throw new OperatorException(s + " is an invalid filter name.");
        }
    }

    /**
     * Initializes this operator and sets the one and only target product.
     * <p>The target product can be either defined by a field of type {@link Product} annotated with the
     * {@link TargetProduct TargetProduct} annotation or
     * by calling {@link #setTargetProduct} method.</p>
     * <p>The framework calls this method after it has created this operator.
     * Any client code that must be performed before computation of tile data
     * should be placed here.</p>
     *
     * @throws OperatorException If an error occurs during operator initialisation.
     * @see #getTargetProduct()
     */
    @Override
    public void initialize() throws OperatorException {

        try {
            final InputProductValidator validator = new InputProductValidator(sourceProduct);
            validator.checkIfSARProduct();
            //validator.checkIfSLC();
            validator.checkIfTOPSARBurstProduct(false);

            sourceImageWidth = sourceProduct.getSceneRasterWidth();
            sourceImageHeight = sourceProduct.getSceneRasterHeight();

            sourceProductType = PolBandUtils.getSourceProductType(sourceProduct);

            checkSourceProductType(sourceProductType);

            srcBandList = PolBandUtils.getSourceBands(sourceProduct, sourceProductType);

            createTargetProduct();

            speckleFilter = createFilter();

        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    private static void checkSourceProductType(final PolBandUtils.MATRIX sourceProductType) {

        // Inside each of the 4 filters, there is a check for sourceProductType and UNKNOWN will cause an exception.
        // Without this check, we get a null pointer exception.
        if (sourceProductType == PolBandUtils.MATRIX.UNKNOWN) {
            throw new OperatorException("Input should be a polarimetric product");
        }
    }

    private PolarimetricSpeckleFilter createFilter() {
        final int numLooks = Integer.parseInt(numLooksStr);

        switch (filter) {
            case BOXCAR_SPECKLE_FILTER:
                return new BoxCar(this, sourceProduct, targetProduct, sourceProductType, srcBandList, filterSize);
            case REFINED_LEE_FILTER:
                filterSize = FilterWindow.parseWindowSize(windowSize);
                return new RefinedLee(this, sourceProduct, targetProduct, sourceProductType, srcBandList, filterSize,
                        numLooks);
            case IDAN_FILTER:
                return new IDAN(this, sourceProduct, targetProduct, sourceProductType, srcBandList, anSize,
                        numLooks);
            case LEE_SIGMA_FILTER:
                filterSize = FilterWindow.parseWindowSize(windowSize);
                return new LeeSigma(this, sourceProduct, targetProduct, sourceProductType, srcBandList, filterSize,
                        numLooks, sigmaStr, targetWindowSizeStr);
            case NON_LOCAL_FILTER:
                final int searchWindowSize = Integer.parseInt(searchWindowSizeStr);
                final int patchSize = Integer.parseInt(patchSizeStr);
                final int scaleSize = Integer.parseInt(scaleSizeStr);
                return new NonLocal(this, sourceProduct, targetProduct, sourceProductType, srcBandList, numLooks,
                        searchWindowSize, patchSize, scaleSize);
            default:
                return null;
        }
    }

    /**
     * Create target product.
     */
    private void createTargetProduct() {

        targetProduct = new Product(sourceProduct.getName() + PRODUCT_SUFFIX,
                sourceProduct.getProductType(),
                sourceProduct.getSceneRasterWidth(), sourceProduct.getSceneRasterHeight());

        ProductUtils.copyProductNodes(sourceProduct, targetProduct);

        addSelectedBands();

        AbstractMetadata.getAbstractedMetadata(targetProduct).setAttributeInt(AbstractMetadata.polsarData, 1);
    }

    private void addSelectedBands() throws OperatorException {

        String[] bandNames = null;
        boolean copyInputBands = false;
        if (sourceProductType == PolBandUtils.MATRIX.FULL) {
            bandNames = PolBandUtils.getT3BandNames();
        } else if (PolBandUtils.isDualPol(sourceProductType)) {
            bandNames = PolBandUtils.getC2BandNames();
        } else {
            copyInputBands = true;
        }

        for (PolBandUtils.PolSourceBand bandList : srcBandList) {
            String suffix = bandList.suffix;
            if (copyInputBands) {
                bandNames = new String[bandList.srcBands.length];
                int i = 0;
                for (Band band : bandList.srcBands) {
                    bandNames[i++] = band.getName();
                }
                suffix = "";
            }
            final Band[] targetBands = OperatorUtils.addBands(targetProduct, bandNames, suffix);
            bandList.addTargetBands(targetBands);
        }
    }

    /**
     * Called by the framework in order to compute the stack of tiles for the given target bands.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetTiles     The current tiles to be computed for each target band.
     * @param targetRectangle The area in pixel coordinates to be computed (same for all rasters in <code>targetRasters</code>).
     * @param pm              A progress monitor which should be used to determine computation cancelation requests.
     * @throws OperatorException if an error occurs during computation of the target rasters.
     */
    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm)
            throws OperatorException {

        try {
            if (filter.equals(NON_LOCAL_FILTER)) {
                final int searchWindowSize = Integer.parseInt(searchWindowSizeStr);
                final int patchSize = Integer.parseInt(patchSizeStr);
                final int scaleSize = Integer.parseInt(scaleSizeStr);
                final int extSize = searchWindowSize / 2 + patchSize / 2 + scaleSize;
                speckleFilter.computeTiles(targetTiles, targetRectangle, getSourceTileRectangle(targetRectangle, extSize));
            } else {
                speckleFilter.computeTiles(targetTiles, targetRectangle, getSourceTileRectangle(targetRectangle));
            }

        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        } finally {
            pm.done();
        }
    }

    /**
     * Get source tile rectangle.
     *
     * @param rect X target tile rectangle.
     * @return The source tile rectangle.
     */
    private Rectangle getSourceTileRectangle(final Rectangle rect) {

        int sx0 = rect.x;
        int sy0 = rect.y;
        int sw = rect.width;
        int sh = rect.height;
        final int halfFilterSize = filterSize / 2;

        if (rect.x >= halfFilterSize) {
            sx0 -= halfFilterSize;
            sw += halfFilterSize;
        }
        if (rect.y >= halfFilterSize) {
            sy0 -= halfFilterSize;
            sh += halfFilterSize;
        }
        if (rect.x + rect.width + halfFilterSize <= sourceImageWidth) {
            sw += halfFilterSize;
        }
        if (rect.y + rect.height + halfFilterSize <= sourceImageHeight) {
            sh += halfFilterSize;
        }

        return new Rectangle(sx0, sy0, sw, sh);
    }

    private Rectangle getSourceTileRectangle(final Rectangle rect, final int extSize) {

        int x0 = rect.x;
        int y0 = rect.y;
        int w = rect.width;
        int h = rect.height;

        final int sx0 = Math.max(x0 - extSize, 0);
        final int sy0 = Math.max(y0 - extSize, 0);
        final int sxMax = Math.min(x0 + w - 1 + extSize, sourceImageWidth - 1);
        final int syMax = Math.min(y0 + h - 1 + extSize, sourceImageHeight - 1);
        final int sw = sxMax - sx0 + 1;
        final int sh = syMax - sy0 + 1;

        return new Rectangle(sx0, sy0, sw, sh);
    }

    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.snap.core.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     *
     * @see OperatorSpi#createOperator()
     * @see OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(PolarimetricSpeckleFilterOp.class);
        }
    }
}

